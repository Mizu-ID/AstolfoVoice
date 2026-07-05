package id.astolfo.voicechat;

import id.astolfo.voicechat.api.PlaybackHandle;
import id.astolfo.voicechat.api.PlaybackOptions;
import id.astolfo.voicechat.audio.AudioEngine;
import id.astolfo.voicechat.audio.PlaylistManager;
import id.astolfo.voicechat.config.AstolfoConfig;
import id.astolfo.voicechat.voice.common.PlayerState;
import id.astolfo.voicechat.voice.server.ServerVoiceEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AstolfoCommand - perintah /astolfovoice (alias /asv, /av, /astolfo).
 *
 * v0.2.2: visual femboyish pink (AstolfoStyle), tab completion context-aware
 * (file audio, playlist, world, preset, player), sound feedback ke sender,
 * dekorasi hand-crafted.
 *
 * Urutan: FILE DULU, lalu target, lalu nama + flag.
 */
public final class AstolfoCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB = Arrays.asList(
            "play", "stop", "list", "voicerange", "status", "reset", "reload", "playlist");
    private static final List<String> PRESETS = Arrays.asList(
            "NONE", "PHONE", "RADIO", "MEGA", "CAVE", "KAWAII", "LOFI");
    private static final TextColor CLICK = TextColor.color(0xFF, 0x8F, 0xB6);

    /** Holder mutable untuk parse flag (pitch/preset/volume) tanpa lambda-capture issue. */
    private static final class Flags {
        double volume;
        double pitch = 1.0;
        String preset;
    }

    private final AstolfoVoice plugin;
    private final ServerVoiceEvents server;
    private final AstolfoConfig config;
    private final AudioEngine audioEngine;
    private final PlaylistManager playlists;
    private final Map<UUID, List<PlaybackHandle>> playerHandles = new HashMap<>();

    public AstolfoCommand(AstolfoVoice plugin, ServerVoiceEvents server, AstolfoConfig config,
                          AudioEngine audioEngine, PlaylistManager playlists) {
        this.plugin = plugin;
        this.server = server;
        this.config = config;
        this.audioEngine = audioEngine;
        this.playlists = playlists;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "play" -> handlePlay(sender, args);
            case "stop" -> handleStop(sender, args);
            case "list" -> handleList(sender);
            case "voicerange" -> handleVoiceRange(sender, args);
            case "status" -> handleStatus(sender, args);
            case "reset" -> handleReset(sender, args);
            case "reload" -> handleReload(sender);
            case "playlist" -> handlePlaylist(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    /** Parse satu argumen flag ke holder. Return true bila argumen adalah flag/number (bukan player name). */
    private static boolean parseFlag(String a, Flags f) {
        String low = a.toLowerCase();
        if (low.startsWith("pitch=")) { f.pitch = parseDouble(a.substring(6), f.pitch); return true; }
        if (low.startsWith("preset=")) { f.preset = a.substring(7); return true; }
        if (isNumber(a)) { f.volume = parseDouble(a, f.volume); return true; }
        return false;
    }

    // ============ PLAY ============
    private void handlePlay(CommandSender sender, String[] args) {
        if (!sender.hasPermission("astolfo.play")) { noPerm(sender); return; }
        if (audioEngine == null) { sender.sendMessage(AstolfoStyle.error("Audio engine nonaktif.")); return; }
        if (args.length < 3) {
            sender.sendMessage(AstolfoStyle.info("Penggunaan: /av play <file> <player|world|all|location|group> ..."));
            return;
        }
        String file = args[1];
        String target = args[2].toLowerCase();
        List<Player> targets = new ArrayList<>();
        Flags flags = new Flags();
        flags.volume = config.audioDefaultVolume();
        double distance = config.audioDefaultDistance();
        String locWorld = null;
        double lx = 0, ly = 0, lz = 0;
        boolean isLocation = false;

        switch (target) {
            case "player" -> {
                if (args.length < 4) { sender.sendMessage(AstolfoStyle.info("Penggunaan: /av play <file> player <p1> <p2> ... [volume] [pitch=P] [preset=X]")); return; }
                for (int i = 3; i < args.length; i++) {
                    if (parseFlag(args[i], flags)) continue;
                    Player p = Bukkit.getPlayerExact(args[i]);
                    if (p == null) sender.sendMessage(AstolfoStyle.error("Player tidak ditemukan: " + args[i]));
                    else targets.add(p);
                }
            }
            case "all" -> {
                targets.addAll(Bukkit.getOnlinePlayers());
                for (int i = 3; i < args.length; i++) parseFlag(args[i], flags);
            }
            case "world" -> {
                if (args.length < 4) { sender.sendMessage(AstolfoStyle.info("Penggunaan: /av play <file> world <world> [volume] [pitch=P] [preset=X]")); return; }
                World w = Bukkit.getWorld(args[3]);
                if (w == null) { sender.sendMessage(AstolfoStyle.error("World tidak ditemukan: " + args[3])); return; }
                targets.addAll(w.getPlayers());
                for (int i = 4; i < args.length; i++) parseFlag(args[i], flags);
            }
            case "group" -> {
                if (args.length < 4) { sender.sendMessage(AstolfoStyle.info("Penggunaan: /av play <file> group <group>")); return; }
                var g = server.getGroupManager().getGroupByName(args[3]);
                if (g == null) { sender.sendMessage(AstolfoStyle.error("Grup tidak ditemukan: " + args[3])); return; }
                for (UUID m : server.getGroupManager().getMembers(g.id)) {
                    Player mp = Bukkit.getPlayer(m);
                    if (mp != null) targets.add(mp);
                }
                for (int i = 4; i < args.length; i++) parseFlag(args[i], flags);
            }
            case "location" -> {
                if (args.length < 8) { sender.sendMessage(AstolfoStyle.info("Penggunaan: /av play <file> location <world> <x> <y> <z> [distance] [volume] [pitch=P] [preset=X]")); return; }
                locWorld = args[3];
                World w = Bukkit.getWorld(locWorld);
                if (w == null) { sender.sendMessage(AstolfoStyle.error("World tidak ditemukan: " + locWorld)); return; }
                try {
                    lx = Double.parseDouble(args[4]);
                    ly = Double.parseDouble(args[5]);
                    lz = Double.parseDouble(args[6]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(AstolfoStyle.error("Koordinat x/y/z tidak valid."));
                    return;
                }
                isLocation = true;
                targets.addAll(w.getPlayers());
                int idx = 7;
                if (args.length > idx && isNumber(args[idx])) { distance = parseDouble(args[idx], distance); idx++; }
                if (args.length > idx && isNumber(args[idx])) { flags.volume = parseDouble(args[idx], flags.volume); idx++; }
                for (int i = idx; i < args.length; i++) parseFlag(args[i], flags);
            }
            default -> { sender.sendMessage(AstolfoStyle.error("Target tidak dikenal: " + target)); return; }
        }

        if (targets.isEmpty()) { sender.sendMessage(AstolfoStyle.info("Tidak ada target player online.")); return; }
        File resolved = audioEngine.resolveFile(file);
        if (resolved == null) {
            sender.sendMessage(AstolfoStyle.error("File audio tidak ditemukan: " + file + " (cari di plugins/AstolfoVoice/audio)"));
            return;
        }
        PlaybackOptions opts = new PlaybackOptions().setVolume(flags.volume).setDistance(distance).setPitch(flags.pitch).setPreset(flags.preset);
        PlaybackHandle handle;
        if (isLocation) {
            org.bukkit.Location loc = new org.bukkit.Location(Bukkit.getWorld(locWorld), lx, ly, lz);
            handle = audioEngine.playToLocation(targets, resolved.getName(), opts, loc);
        } else {
            handle = audioEngine.playToTargets(targets, resolved.getName(), opts);
        }
        if (handle == null) {
            sender.sendMessage(AstolfoStyle.error("Playback gagal (decode error atau kuota penuh). Cek log."));
            return;
        }
        for (Player tg : targets) playerHandles.computeIfAbsent(tg.getUniqueId(), k -> new ArrayList<>()).add(handle);
        ding(sender);
        Component msg = AstolfoStyle.prefix()
                .append(Component.text("Memutar ", AstolfoStyle.CREAM))
                .append(Component.text("'" + resolved.getName() + "'", AstolfoStyle.CANDY).decoration(TextDecoration.ITALIC, true))
                .append(Component.text(" ke " + targets.size() + " target ", AstolfoStyle.CREAM))
                .append(Component.text("(" + target + (isLocation ? " @ " + locWorld + " " + lx + "," + ly + "," + lz : "") + ")", AstolfoStyle.MUTE));
        if (flags.pitch != 1.0) msg = msg.append(Component.text(" pitch=" + flags.pitch, AstolfoStyle.LAV));
        if (flags.preset != null) msg = msg.append(Component.text(" preset=" + flags.preset, AstolfoStyle.HOT));
        sender.sendMessage(msg);
    }

    // ============ STOP ============
    private void handleStop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("astolfo.stop")) { noPerm(sender); return; }
        if (audioEngine == null) return;
        if (args.length < 2 || args[1].equalsIgnoreCase("all")) {
            int n = audioEngine.activeCount();
            audioEngine.stopAll();
            playerHandles.clear();
            sender.sendMessage(AstolfoStyle.success("Stop semua playback (" + n + ")."));
        } else if (args[1].equalsIgnoreCase("player") && args.length > 2) {
            Player p = Bukkit.getPlayerExact(args[2]);
            if (p == null) { sender.sendMessage(AstolfoStyle.error("Player tidak ditemukan.")); return; }
            List<PlaybackHandle> list = playerHandles.remove(p.getUniqueId());
            if (list != null) for (PlaybackHandle h : list) h.stop();
            sender.sendMessage(AstolfoStyle.success("Stop playback untuk " + p.getName() + "."));
        } else {
            sender.sendMessage(AstolfoStyle.info("Penggunaan: /av stop [all | player <name>]"));
        }
    }

    // ============ LIST (clickable, pink) ============
    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("astolfo.list")) { noPerm(sender); return; }
        File audioDir = audioEngine != null ? audioEngine.getAudioDir() : new File(plugin.getDataFolder(), config.audioFolder());
        sender.sendMessage(AstolfoStyle.header("Folder audio"));
        if (sender instanceof Player player) {
            player.sendMessage(Component.text("  ", AstolfoStyle.BLUSH).append(Component.text(audioDir.getPath(), AstolfoStyle.MUTE)));
        } else {
            sender.sendMessage(AstolfoStyle.info(audioDir.getPath()));
        }
        File[] files = audioDir.listFiles((d, n) -> n.endsWith(".mp3") || n.endsWith(".ogg") || n.endsWith(".wav"));
        if (files == null || files.length == 0) {
            sender.sendMessage(AstolfoStyle.info("Belum ada file audio. Letakkan mp3/ogg/wav di folder itu~"));
            return;
        }
        boolean canClick = sender instanceof Player;
        if (canClick) ((Player) sender).sendMessage(AstolfoStyle.divider());
        for (File f : files) {
            String name = f.getName();
            String size = (f.length() / 1024) + " KB";
            if (canClick) {
                Component line = Component.text("  ♪ ", AstolfoStyle.CANDY)
                        .append(Component.text(name, CLICK, TextDecoration.UNDERLINED)
                                .clickEvent(ClickEvent.runCommand("/av play " + name + " all"))
                                .hoverEvent(HoverEvent.showText(Component.text("Klik untuk putar ke semua~ ♡", AstolfoStyle.LAV))))
                        .append(Component.text("  " + size, AstolfoStyle.MUTE));
                ((Player) sender).sendMessage(line);
            } else {
                sender.sendMessage(AstolfoStyle.item(name, size));
            }
        }
        if (canClick) {
            ((Player) sender).sendMessage(Component.text("  Klik nama untuk putar ✿", AstolfoStyle.MUTE).decoration(TextDecoration.ITALIC, true));
        }
    }

    // ============ VOICERANGE ============
    private void handleVoiceRange(CommandSender sender, String[] args) {
        if (!sender.hasPermission("astolfo.voicerange")) { noPerm(sender); return; }
        if (args.length < 2) { sender.sendMessage(AstolfoStyle.info("Penggunaan: /av voicerange <player> [range]")); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(AstolfoStyle.error("Player tidak ditemukan: " + args[1])); return; }
        if (args.length == 2) {
            Double ov = plugin.getApi().getVoiceRange(target.getUniqueId());
            sender.sendMessage(AstolfoStyle.prefix().append(Component.text(target.getName() + " voice range: ", AstolfoStyle.CREAM))
                    .append(Component.text(String.valueOf(ov), AstolfoStyle.CANDY)));
        } else {
            try {
                double range = Double.parseDouble(args[2]);
                range = Math.min(range, config.maxPlayerRangeOverride());
                plugin.getApi().setVoiceRange(target.getUniqueId(), range);
                sender.sendMessage(AstolfoStyle.success(target.getName() + " voice range di-set " + range + " ✿"));
            } catch (NumberFormatException e) {
                sender.sendMessage(AstolfoStyle.error("Range tidak valid: " + args[2]));
            }
        }
    }

    // ============ STATUS ============
    private void handleStatus(CommandSender sender, String[] args) {
        if (args.length > 1) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage(AstolfoStyle.error("Player tidak ditemukan: " + args[1])); return; }
            PlayerState state = server.getPlayerStateManager().getState(target.getUniqueId());
            double range = plugin.getApi().getVoiceRange(target.getUniqueId());
            if (state == null) { sender.sendMessage(AstolfoStyle.info(target.getName() + ": belum ter-track.")); return; }
            sender.sendMessage(AstolfoStyle.prefix().append(Component.text(target.getName(), AstolfoStyle.CANDY))
                    .append(Component.text("  conn=" + !state.isDisconnected() + "  disabled=" + state.isDisabled()
                            + "  range=" + range + "  world=" + (target.getWorld() != null ? target.getWorld().getName() : "-"), AstolfoStyle.CREAM)));
        } else {
            int tracked = server.getPlayerStateManager().allStates().size();
            int active = audioEngine != null ? audioEngine.activeCount() : 0;
            int groups = server.getGroupManager().allGroups().size();
            int pl = this.playlists.all().size();
            sender.sendMessage(AstolfoStyle.prefix().append(Component.text("Status: ", AstolfoStyle.CREAM))
                    .append(Component.text(tracked + " tracked", AstolfoStyle.CANDY)).append(Component.text(" • ", AstolfoStyle.BLUSH))
                    .append(Component.text(groups + " grup", AstolfoStyle.LAV)).append(Component.text(" • ", AstolfoStyle.BLUSH))
                    .append(Component.text(active + " playback", AstolfoStyle.HOT)).append(Component.text(" • ", AstolfoStyle.BLUSH))
                    .append(Component.text(pl + " playlist", AstolfoStyle.CANDY)));
        }
    }

    // ============ RESET ============
    private void handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("astolfo.reset")) { noPerm(sender); return; }
        if (args.length < 2) { sender.sendMessage(AstolfoStyle.info("Penggunaan: /av reset <player>")); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(AstolfoStyle.error("Player tidak ditemukan: " + args[1])); return; }
        plugin.getApi().resetPlayer(target.getUniqueId());
        sender.sendMessage(AstolfoStyle.success("Reset " + target.getName() + " (voice range & override) ♡"));
    }

    // ============ RELOAD ============
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("astolfo.reload")) { noPerm(sender); return; }
        // #5: reload AstolfoConfig beneran (bukan cuma Bukkit config), lalu tunjukin nilai baru.
        boolean ok = plugin.reloadAstolfoConfig();
        if (ok) {
            AstolfoConfig cfg = plugin.getAstolfoConfig();
            sender.sendMessage(AstolfoStyle.prefix()
                    .append(Component.text("Config di-reload ", AstolfoStyle.CREAM))
                    .append(Component.text("range=" + cfg.voiceRange() + " whisper=" + cfg.whisperRange()
                            + " shout=" + cfg.shoutRange() + " bitrate=" + cfg.opusBitrate(), AstolfoStyle.CANDY)));
        } else {
            sender.sendMessage(AstolfoStyle.error("Gagal reload config, cek log."));
        }
    }

    // ============ PLAYLIST ============
    private void handlePlaylist(CommandSender sender, String[] args) {
        if (!sender.hasPermission("astolfo.playlist")) { noPerm(sender); return; }
        if (args.length < 2) { playlistUsage(sender); return; }
        String sub = args[1].toLowerCase();
        switch (sub) {
            case "create" -> {
                if (args.length < 3) { sender.sendMessage(AstolfoStyle.info("Penggunaan: /av playlist create <name>")); return; }
                if (playlists.create(args[2])) sender.sendMessage(AstolfoStyle.success("Playlist '" + args[2] + "' dibuat ♡"));
                else sender.sendMessage(AstolfoStyle.error("Playlist sudah ada: " + args[2]));
            }
            case "delete" -> {
                if (args.length < 3) { sender.sendMessage(AstolfoStyle.info("Penggunaan: /av playlist delete <name>")); return; }
                if (playlists.delete(args[2])) sender.sendMessage(AstolfoStyle.success("Playlist '" + args[2] + "' dihapus ✦"));
                else sender.sendMessage(AstolfoStyle.error("Playlist tidak ditemukan: " + args[2]));
            }
            case "add" -> {
                if (args.length < 4) { sender.sendMessage(AstolfoStyle.info("Penggunaan: /av playlist add <name> <file> [file ...]")); return; }
                String name = args[2];
                int added = 0;
                for (int i = 3; i < args.length; i++) {
                    if (audioEngine != null && audioEngine.resolveFile(args[i]) == null) {
                        sender.sendMessage(AstolfoStyle.error("File tidak ditemukan: " + args[i] + " (skip)"));
                        continue;
                    }
                    playlists.add(name, args[i]);
                    added++;
                }
                sender.sendMessage(AstolfoStyle.success("Tambah " + added + " file ke playlist '" + name + "' ✿"));
            }
            case "remove" -> {
                if (args.length < 4) { sender.sendMessage(AstolfoStyle.info("Penggunaan: /av playlist remove <name> <file>")); return; }
                if (playlists.remove(args[2], args[3])) sender.sendMessage(AstolfoStyle.success("File dihapus dari playlist."));
                else sender.sendMessage(AstolfoStyle.error("File/playlist tidak ditemukan."));
            }
            case "shuffle" -> {
                if (args.length < 3) { sender.sendMessage(AstolfoStyle.info("Penggunaan: /av playlist shuffle <name>")); return; }
                if (playlists.shuffle(args[2])) sender.sendMessage(AstolfoStyle.success("Playlist '" + args[2] + "' di-shuffle ✦"));
                else sender.sendMessage(AstolfoStyle.error("Playlist tidak ditemukan/kosong: " + args[2]));
            }
            case "list" -> {
                if (args.length < 3) {
                    Map<String, List<String>> all = playlists.all();
                    if (all.isEmpty()) { sender.sendMessage(AstolfoStyle.info("Belum ada playlist~")); return; }
                    boolean canClick = sender instanceof Player;
                    sender.sendMessage(AstolfoStyle.header("Playlist"));
                    if (canClick) ((Player) sender).sendMessage(AstolfoStyle.divider());
                    for (var e : all.entrySet()) {
                        if (canClick) {
                            ((Player) sender).sendMessage(Component.text("  ♡ ", AstolfoStyle.CANDY)
                                    .append(Component.text(e.getKey(), CLICK, TextDecoration.UNDERLINED)
                                            .clickEvent(ClickEvent.runCommand("/av playlist play " + e.getKey() + " all"))
                                            .hoverEvent(HoverEvent.showText(Component.text("Klik untuk putar playlist ✿", AstolfoStyle.LAV))))
                                    .append(Component.text("  " + e.getValue().size() + " file", AstolfoStyle.MUTE)));
                        } else {
                            sender.sendMessage(AstolfoStyle.item(e.getKey(), "(" + e.getValue().size() + " file)"));
                        }
                    }
                } else {
                    List<String> list = playlists.get(args[2]);
                    if (list == null) { sender.sendMessage(AstolfoStyle.error("Playlist tidak ditemukan: " + args[2])); return; }
                    sender.sendMessage(AstolfoStyle.header("Playlist '" + args[2] + "' (" + list.size() + ")"));
                    boolean canClick = sender instanceof Player;
                    for (int i = 0; i < list.size(); i++) {
                        String fn = list.get(i);
                        if (canClick) {
                            ((Player) sender).sendMessage(Component.text("  " + (i + 1) + ". ", AstolfoStyle.MUTE)
                                    .append(Component.text(fn, CLICK, TextDecoration.UNDERLINED)
                                            .clickEvent(ClickEvent.runCommand("/av play " + fn + " all"))
                                            .hoverEvent(HoverEvent.showText(Component.text("Klik untuk putar file ini ♡", AstolfoStyle.LAV)))));
                        } else {
                            sender.sendMessage(AstolfoStyle.item(String.valueOf(i + 1), fn));
                        }
                    }
                }
            }
            case "play" -> {
                if (args.length < 3) { sender.sendMessage(AstolfoStyle.info("Penggunaan: /av playlist play <name> [player <p> ... | world <w> | all] [shuffle]")); return; }
                if (audioEngine == null) { sender.sendMessage(AstolfoStyle.error("Audio engine nonaktif.")); return; }
                String name = args[2];
                if (!playlists.exists(name)) { sender.sendMessage(AstolfoStyle.error("Playlist tidak ditemukan: " + name)); return; }
                boolean shuffle = false;
                List<Player> targets = new ArrayList<>();
                if (args.length > 3) {
                    String tgt = args[3].toLowerCase();
                    switch (tgt) {
                        case "player" -> {
                            for (int i = 4; i < args.length; i++) {
                                if (args[i].equalsIgnoreCase("shuffle")) { shuffle = true; break; }
                                Player p = Bukkit.getPlayerExact(args[i]);
                                if (p != null) targets.add(p);
                            }
                        }
                        case "all" -> {
                            targets.addAll(Bukkit.getOnlinePlayers());
                            if (args.length > 4 && args[4].equalsIgnoreCase("shuffle")) shuffle = true;
                        }
                        case "world" -> {
                            if (args.length > 4) {
                                World w = Bukkit.getWorld(args[4]);
                                if (w != null) targets.addAll(w.getPlayers());
                                if (args.length > 5 && args[5].equalsIgnoreCase("shuffle")) shuffle = true;
                            }
                        }
                        default -> { if (tgt.equals("shuffle")) { shuffle = true; targets.addAll(Bukkit.getOnlinePlayers()); } }
                    }
                } else {
                    targets.addAll(Bukkit.getOnlinePlayers());
                }
                if (targets.isEmpty()) { sender.sendMessage(AstolfoStyle.info("Tidak ada target online.")); return; }
                List<String> order = playlists.order(name, shuffle);
                PlaybackOptions opts = new PlaybackOptions().setVolume(config.audioDefaultVolume()).setDistance(config.audioDefaultDistance());
                PlaybackHandle handle = audioEngine.playQueue(targets, order, opts, false);
                if (handle == null) { sender.sendMessage(AstolfoStyle.error("Gagal memutar playlist.")); return; }
                for (Player t : targets) playerHandles.computeIfAbsent(t.getUniqueId(), k -> new ArrayList<>()).add(handle);
                ding(sender);
                sender.sendMessage(AstolfoStyle.success("Memutar playlist '" + name + "' (" + order.size() + (shuffle ? ", shuffle" : "") + ") ke " + targets.size() + " target ✿"));
            }
            default -> playlistUsage(sender);
        }
    }

    private void playlistUsage(CommandSender sender) {
        sender.sendMessage(AstolfoStyle.header("Playlist"));
        sender.sendMessage(AstolfoStyle.info("create <name>"));
        sender.sendMessage(AstolfoStyle.info("add <name> <file> [file ...]"));
        sender.sendMessage(AstolfoStyle.info("remove <name> <file>"));
        sender.sendMessage(AstolfoStyle.info("play <name> [player <p> ... | world <w> | all] [shuffle]"));
        sender.sendMessage(AstolfoStyle.info("list [name]"));
        sender.sendMessage(AstolfoStyle.info("shuffle <name>"));
        sender.sendMessage(AstolfoStyle.info("delete <name>"));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(AstolfoStyle.header("AstolfoVoice  ✿"));
        if (sender instanceof Player p) p.sendMessage(AstolfoStyle.divider());
        sender.sendMessage(AstolfoStyle.info("play <file> player <p1> <p2> ... [volume] [pitch=P] [preset=KAWAII]"));
        sender.sendMessage(AstolfoStyle.info("play <file> world <world> [volume] [pitch=P] [preset=KAWAII]"));
        sender.sendMessage(AstolfoStyle.info("play <file> all [volume] [pitch=P] [preset=KAWAII]"));
        sender.sendMessage(AstolfoStyle.info("play <file> location <world> <x> <y> <z> [distance] [volume] [pitch=P] [preset=KAWAII]"));
        sender.sendMessage(AstolfoStyle.info("play <file> group <group>"));
        sender.sendMessage(AstolfoStyle.info("stop [all | player <name>]"));
        sender.sendMessage(AstolfoStyle.info("list  (klik file untuk putar ♡)"));
        sender.sendMessage(AstolfoStyle.info("voicerange <player> [range]"));
        sender.sendMessage(AstolfoStyle.info("status [player]"));
        sender.sendMessage(AstolfoStyle.info("reset <player>"));
        sender.sendMessage(AstolfoStyle.info("reload"));
        sender.sendMessage(AstolfoStyle.info("playlist ..."));
        sender.sendMessage(Component.text("  Preset: ", AstolfoStyle.MUTE)
                .append(Component.text("NONE PHONE RADIO MEGA CAVE KAWAII LOFI", AstolfoStyle.LAV))
                .append(Component.text("  •  pitch=1.0 normal", AstolfoStyle.MUTE)));
    }

    private void noPerm(CommandSender s) { s.sendMessage(AstolfoStyle.error("Kamu tidak punya izin~ ✗")); }
    private void ding(CommandSender s) {
        if (s instanceof Player p) {
            try { p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.6f); } catch (Throwable ignored) {}
        }
    }
    private static double parseDouble(String s, double def) { try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; } }
    private static boolean isNumber(String s) { try { Double.parseDouble(s); return true; } catch (NumberFormatException e) { return false; } }

    // ============ TAB COMPLETION (context-aware) ============
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String last;
        switch (args.length) {
            case 1 -> { for (String s : SUB) if (s.startsWith(args[0].toLowerCase())) out.add(s); }
            case 2 -> {
                if (args[0].equalsIgnoreCase("playlist")) {
                    for (String s : Arrays.asList("create", "add", "remove", "play", "list", "delete", "shuffle"))
                        if (s.startsWith(args[1].toLowerCase())) out.add(s);
                } else if (args[0].equalsIgnoreCase("voicerange") || args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("reset")) {
                    for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(p.getName());
                } else if (args[0].equalsIgnoreCase("play")) {
                    out.addAll(audioFiles(args[1]));
                }
            }
            case 3 -> {
                if (args[0].equalsIgnoreCase("play"))
                    for (String t : Arrays.asList("player", "world", "all", "location", "group")) if (t.startsWith(args[2].toLowerCase())) out.add(t);
                if (args[0].equalsIgnoreCase("playlist") && args[1].matches("list|delete|shuffle|add|remove|play"))
                    out.addAll(playlistNames(args[2]));
            }
            case 4 -> {
                if (args[0].equalsIgnoreCase("play") && args[2].equalsIgnoreCase("player"))
                    for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase().startsWith(args[3].toLowerCase())) out.add(p.getName());
                if (args[0].equalsIgnoreCase("play") && (args[2].equalsIgnoreCase("world") || args[2].equalsIgnoreCase("location")))
                    for (World w : Bukkit.getWorlds()) if (w.getName().toLowerCase().startsWith(args[3].toLowerCase())) out.add(w.getName());
                if (args[0].equalsIgnoreCase("stop") && args[1].equalsIgnoreCase("player"))
                    for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase().startsWith(args[3].toLowerCase())) out.add(p.getName());
                if (args[0].equalsIgnoreCase("playlist") && args[1].matches("add|remove"))
                    out.addAll(audioFiles(args[3]));
                if (args[0].equalsIgnoreCase("playlist") && args[1].equalsIgnoreCase("play"))
                    for (String t : Arrays.asList("player", "world", "all", "shuffle")) if (t.startsWith(args[3].toLowerCase())) out.add(t);
            }
            default -> {
                last = args[args.length - 1].toLowerCase();
                if (args[0].equalsIgnoreCase("play")) {
                    // saran player tambahan bila belum ada flag
                    boolean flagSeen = false;
                    for (int i = 3; i < args.length - 1; i++) {
                        String a = args[i].toLowerCase();
                        if (a.startsWith("preset=") || a.startsWith("pitch=") || isNumber(args[i])) { flagSeen = true; break; }
                    }
                    if (!flagSeen && args[2].equalsIgnoreCase("player"))
                        for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase().startsWith(last)) out.add(p.getName());
                    if (last.startsWith("preset=")) for (String p : PRESETS) if (p.toLowerCase().startsWith(last.substring(7))) out.add("preset=" + p);
                    else if (last.isEmpty()) out.add("preset=KAWAII");
                    if (last.startsWith("pitch")) out.add("pitch=1.0");
                    if (args[2].equalsIgnoreCase("world") && last.isEmpty()) out.add("1.0");
                }
                if (args[0].equalsIgnoreCase("playlist") && args[1].equalsIgnoreCase("play") && args.length > 4) {
                    if (args[3].equalsIgnoreCase("player")) for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase().startsWith(last)) out.add(p.getName());
                    if (args[3].equalsIgnoreCase("world")) for (World w : Bukkit.getWorlds()) if (w.getName().toLowerCase().startsWith(last)) out.add(w.getName());
                    if (last.isEmpty() || last.startsWith("shuffle")) out.add("shuffle");
                }
            }
        }
        return out;
    }

    private List<String> audioFiles(String prefix) {
        List<String> out = new ArrayList<>();
        if (audioEngine == null) return out;
        File dir = audioEngine.getAudioDir();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".mp3") || n.endsWith(".ogg") || n.endsWith(".wav"));
        if (files == null) return out;
        String p = prefix == null ? "" : prefix.toLowerCase();
        for (File f : files) if (f.getName().toLowerCase().startsWith(p)) out.add(f.getName());
        return out;
    }

    private List<String> playlistNames(String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase();
        return playlists.all().keySet().stream().filter(n -> n.toLowerCase().startsWith(p)).collect(Collectors.toList());
    }
}
