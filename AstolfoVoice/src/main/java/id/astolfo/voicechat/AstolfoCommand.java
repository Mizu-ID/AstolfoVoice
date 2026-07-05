package id.astolfo.voicechat;

import id.astolfo.voicechat.api.PlaybackHandle;
import id.astolfo.voicechat.api.PlaybackOptions;
import id.astolfo.voicechat.audio.AudioEngine;
import id.astolfo.voicechat.audio.PlaylistManager;
import id.astolfo.voicechat.config.AstolfoConfig;
import id.astolfo.voicechat.voice.common.PlayerState;
import id.astolfo.voicechat.voice.server.ServerVoiceEvents;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

/**
 * AstolfoCommand — perintah /astolfovoice (alias /asv, /av, /astolfo).
 *
 * Urutan argumen sesuai spec user: FILE DULU, lalu target keyword, lalu nama.
 *   /astolfovoice play <file> player <player1> <player2> ...
 *   /astolfovoice play <file> world <world>
 *   /astolfovoice play <file> all
 *   /astolfovoice play <file> location <world> <x> <y> <z> [distance] [volume]
 *   /astolfovoice play <file> group <group>
 *   /astolfovoice playlist ...
 */
public final class AstolfoCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB = Arrays.asList(
            "play", "stop", "list", "voicerange", "status", "reset", "reload", "playlist");

    private final AstolfoVoice plugin;
    private final ServerVoiceEvents server;
    private final AstolfoConfig config;
    private final AudioEngine audioEngine;
    private final PlaylistManager playlists;
    // handle aktif per player (untuk stop per player)
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

    // ============ PLAY (file-first) ============
    private void handlePlay(CommandSender sender, String[] args) {
        if (!sender.hasPermission("astolfo.play")) { noPerm(sender); return; }
        if (audioEngine == null) { sender.sendMessage(prefix() + ChatColor.RED + "Audio engine nonaktif."); return; }
        // args: play <file> <target> ...
        if (args.length < 3) {
            sender.sendMessage(prefix() + ChatColor.YELLOW + "Penggunaan: /astolfovoice play <file> <player|world|all|location|group> ...");
            return;
        }
        String file = args[1];
        String target = args[2].toLowerCase();
        List<Player> targets = new ArrayList<>();
        double volume = config.audioDefaultVolume();
        double distance = config.audioDefaultDistance();

        switch (target) {
            case "player" -> {
                // play <file> player <p1> <p2> ... [volume]
                if (args.length < 4) { sender.sendMessage(prefix() + ChatColor.YELLOW + "Penggunaan: /astolfovoice play <file> player <player1> <player2> ... [volume]"); return; }
                int volIdx = -1;
                for (int i = 3; i < args.length; i++) {
                    try { Double.parseDouble(args[i]); volIdx = i; break; } catch (NumberFormatException ignored) {}
                }
                int end = volIdx >= 0 ? volIdx : args.length;
                for (int i = 3; i < end; i++) {
                    Player p = Bukkit.getPlayerExact(args[i]);
                    if (p == null) {
                        sender.sendMessage(prefix() + ChatColor.RED + "Player tidak ditemukan: " + args[i]);
                    } else {
                        targets.add(p);
                    }
                }
                if (volIdx >= 0) volume = parseDouble(args[volIdx], volume);
            }
            case "all" -> {
                targets.addAll(Bukkit.getOnlinePlayers());
                if (args.length > 3) volume = parseDouble(args[3], volume);
            }
            case "world" -> {
                if (args.length < 4) { sender.sendMessage(prefix() + ChatColor.YELLOW + "Penggunaan: /astolfovoice play <file> world <world> [volume]"); return; }
                World w = Bukkit.getWorld(args[3]);
                if (w == null) { sender.sendMessage(prefix() + ChatColor.RED + "World tidak ditemukan: " + args[3]); return; }
                targets.addAll(w.getPlayers());
                if (args.length > 4) volume = parseDouble(args[4], volume);
            }
            case "group" -> {
                if (args.length < 4) { sender.sendMessage(prefix() + ChatColor.YELLOW + "Penggunaan: /astolfovoice play <file> group <group>"); return; }
                var g = server.getGroupManager().getGroupByName(args[3]);
                if (g == null) { sender.sendMessage(prefix() + ChatColor.RED + "Grup tidak ditemukan: " + args[3]); return; }
                for (UUID m : server.getGroupManager().getMembers(g.id)) {
                    Player mp = Bukkit.getPlayer(m);
                    if (mp != null) targets.add(mp);
                }
            }
            case "location" -> {
                if (args.length < 8) { sender.sendMessage(prefix() + ChatColor.YELLOW + "Penggunaan: /astolfovoice play <file> location <world> <x> <y> <z> [distance] [volume]"); return; }
                World w = Bukkit.getWorld(args[3]);
                if (w == null) { sender.sendMessage(prefix() + ChatColor.RED + "World tidak ditemukan: " + args[3]); return; }
                targets.addAll(w.getPlayers()); // static per-player; locational penuh = lanjutan
                int off = 0;
                if (args.length > 7) { distance = parseDouble(args[7], distance); off = 1; }
                if (args.length > 7 + off) volume = parseDouble(args[7 + off], volume);
            }
            default -> { sender.sendMessage(prefix() + ChatColor.RED + "Target tidak dikenal: " + target); return; }
        }

        if (file == null) { sender.sendMessage(prefix() + ChatColor.RED + "File tidak dispesifikkan."); return; }
        if (targets.isEmpty()) { sender.sendMessage(prefix() + ChatColor.YELLOW + "Tidak ada target player online."); return; }

        File resolved = audioEngine.resolveFile(file);
        if (resolved == null) {
            sender.sendMessage(prefix() + ChatColor.RED + "File audio tidak ditemukan: " + file
                    + " (cari di plugins/AstolfoVoice/audio, format mp3/ogg/wav)");
            return;
        }
        PlaybackOptions opts = new PlaybackOptions().setVolume(volume).setDistance(distance);
        PlaybackHandle handle = audioEngine.playToTargets(targets, resolved.getName(), opts);
        if (handle == null) {
            sender.sendMessage(prefix() + ChatColor.RED + "Playback gagal (decode error atau kuota penuh). Cek log.");
            return;
        }
        for (Player t : targets) {
            playerHandles.computeIfAbsent(t.getUniqueId(), k -> new ArrayList<>()).add(handle);
        }
        sender.sendMessage(prefix() + ChatColor.GREEN + "Memutar '" + resolved.getName() + "' ke "
                + targets.size() + " target (" + target + ").");
    }

    // ============ STOP ============
    private void handleStop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("astolfo.stop")) { noPerm(sender); return; }
        if (audioEngine == null) return;
        if (args.length < 2 || args[1].equalsIgnoreCase("all")) {
            int n = audioEngine.activeCount();
            audioEngine.stopAll();
            playerHandles.clear();
            sender.sendMessage(prefix() + ChatColor.GREEN + "Stop semua playback (" + n + ").");
        } else if (args[1].equalsIgnoreCase("player") && args.length > 2) {
            Player p = Bukkit.getPlayerExact(args[2]);
            if (p == null) { sender.sendMessage(prefix() + ChatColor.RED + "Player tidak ditemukan."); return; }
            List<PlaybackHandle> list = playerHandles.remove(p.getUniqueId());
            if (list != null) for (PlaybackHandle h : list) h.stop();
            sender.sendMessage(prefix() + ChatColor.GREEN + "Stop playback untuk " + p.getName() + ".");
        } else {
            sender.sendMessage(prefix() + ChatColor.YELLOW + "Penggunaan: /astolfovoice stop [all | player <name>]");
        }
    }

    // ============ LIST ============
    private void handleList(CommandSender sender) {
        File audioDir = audioEngine != null ? audioEngine.getAudioDir() : new File(plugin.getDataFolder(), config.audioFolder());
        sender.sendMessage(prefix() + ChatColor.AQUA + "Folder audio: " + audioDir.getPath());
        File[] files = audioDir.listFiles((d, n) -> n.endsWith(".mp3") || n.endsWith(".ogg") || n.endsWith(".wav"));
        if (files == null || files.length == 0) {
            sender.sendMessage(prefix() + ChatColor.YELLOW + "Belum ada file audio. Letakkan mp3/ogg/wav di folder tersebut.");
            return;
        }
        for (File f : files) {
            sender.sendMessage(ChatColor.GRAY + " - " + f.getName() + " (" + (f.length() / 1024) + " KB)");
        }
    }

    // ============ VOICERANGE ============
    private void handleVoiceRange(CommandSender sender, String[] args) {
        if (!sender.hasPermission("astolfo.voicerange")) { noPerm(sender); return; }
        if (args.length < 2) { sender.sendMessage(prefix() + ChatColor.YELLOW + "Penggunaan: /astolfovoice voicerange <player> [range]"); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(prefix() + ChatColor.RED + "Player tidak ditemukan: " + args[1]); return; }
        if (args.length == 2) {
            Double ov = plugin.getApi().getVoiceRange(target.getUniqueId());
            sender.sendMessage(prefix() + ChatColor.AQUA + target.getName() + " voice range: " + ov);
        } else {
            try {
                double range = Double.parseDouble(args[2]);
                range = Math.min(range, config.maxPlayerRangeOverride());
                plugin.getApi().setVoiceRange(target.getUniqueId(), range);
                sender.sendMessage(prefix() + ChatColor.GREEN + target.getName() + " voice range di-set " + range);
            } catch (NumberFormatException e) {
                sender.sendMessage(prefix() + ChatColor.RED + "Range tidak valid: " + args[2]);
            }
        }
    }

    // ============ STATUS ============
    private void handleStatus(CommandSender sender, String[] args) {
        if (args.length > 1) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage(prefix() + ChatColor.RED + "Player tidak ditemukan: " + args[1]); return; }
            PlayerState state = server.getPlayerStateManager().getState(target.getUniqueId());
            double range = plugin.getApi().getVoiceRange(target.getUniqueId());
            if (state == null) { sender.sendMessage(prefix() + ChatColor.YELLOW + target.getName() + ": belum ter-track."); return; }
            sender.sendMessage(prefix() + ChatColor.AQUA + target.getName()
                    + " | connected=" + !state.isDisconnected()
                    + " | disabled=" + state.isDisabled()
                    + " | group=" + (state.hasGroup() ? state.getGroup() : "-")
                    + " | range=" + range
                    + " | world=" + (target.getWorld() != null ? target.getWorld().getName() : "-"));
        } else {
            int tracked = server.getPlayerStateManager().allStates().size();
            int active = audioEngine != null ? audioEngine.activeCount() : 0;
            int groups = server.getGroupManager().allGroups().size();
            int playlists = this.playlists.all().size();
            sender.sendMessage(prefix() + ChatColor.AQUA + "Status: " + tracked + " tracked, "
                    + groups + " grup, " + active + " playback, " + playlists + " playlist");
        }
    }

    // ============ RESET ============
    private void handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("astolfo.reset")) { noPerm(sender); return; }
        if (args.length < 2) { sender.sendMessage(prefix() + ChatColor.YELLOW + "Penggunaan: /astolfovoice reset <player>"); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(prefix() + ChatColor.RED + "Player tidak ditemukan: " + args[1]); return; }
        plugin.getApi().resetPlayer(target.getUniqueId());
        sender.sendMessage(prefix() + ChatColor.GREEN + "Reset " + target.getName() + " (voice range & override).");
    }

    // ============ RELOAD ============
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("astolfo.reload")) { noPerm(sender); return; }
        plugin.reloadConfig();
        sender.sendMessage(prefix() + ChatColor.GREEN + "Config di-reload (socket tidak restart kecuali port/MTU berubah).");
    }

    // ============ PLAYLIST ============
    private void handlePlaylist(CommandSender sender, String[] args) {
        if (!sender.hasPermission("astolfo.playlist")) { noPerm(sender); return; }
        if (args.length < 2) { playlistUsage(sender); return; }
        String sub = args[1].toLowerCase();
        switch (sub) {
            case "create" -> {
                if (args.length < 3) { sender.sendMessage(prefix() + ChatColor.YELLOW + "Penggunaan: /astolfovoice playlist create <name>"); return; }
                if (playlists.create(args[2])) sender.sendMessage(prefix() + ChatColor.GREEN + "Playlist '" + args[2] + "' dibuat.");
                else sender.sendMessage(prefix() + ChatColor.RED + "Playlist sudah ada: " + args[2]);
            }
            case "delete" -> {
                if (args.length < 3) { sender.sendMessage(prefix() + ChatColor.YELLOW + "Penggunaan: /astolfovoice playlist delete <name>"); return; }
                if (playlists.delete(args[2])) sender.sendMessage(prefix() + ChatColor.GREEN + "Playlist '" + args[2] + "' dihapus.");
                else sender.sendMessage(prefix() + ChatColor.RED + "Playlist tidak ditemukan: " + args[2]);
            }
            case "add" -> {
                if (args.length < 4) { sender.sendMessage(prefix() + ChatColor.YELLOW + "Penggunaan: /astolfovoice playlist add <name> <file> [file ...]"); return; }
                String name = args[2];
                int added = 0;
                for (int i = 3; i < args.length; i++) {
                    if (audioEngine != null && audioEngine.resolveFile(args[i]) == null) {
                        sender.sendMessage(prefix() + ChatColor.RED + "File tidak ditemukan: " + args[i] + " (skip)");
                        continue;
                    }
                    playlists.add(name, args[i]);
                    added++;
                }
                sender.sendMessage(prefix() + ChatColor.GREEN + "Tambah " + added + " file ke playlist '" + name + "'.");
            }
            case "remove" -> {
                if (args.length < 4) { sender.sendMessage(prefix() + ChatColor.YELLOW + "Penggunaan: /astolfovoice playlist remove <name> <file>"); return; }
                if (playlists.remove(args[2], args[3])) sender.sendMessage(prefix() + ChatColor.GREEN + "File dihapus dari playlist.");
                else sender.sendMessage(prefix() + ChatColor.RED + "File/playlist tidak ditemukan.");
            }
            case "shuffle" -> {
                if (args.length < 3) { sender.sendMessage(prefix() + ChatColor.YELLOW + "Penggunaan: /astolfovoice playlist shuffle <name>"); return; }
                if (playlists.shuffle(args[2])) sender.sendMessage(prefix() + ChatColor.GREEN + "Playlist '" + args[2] + "' di-shuffle.");
                else sender.sendMessage(prefix() + ChatColor.RED + "Playlist tidak ditemukan/kosong: " + args[2]);
            }
            case "list" -> {
                if (args.length < 3) {
                    Map<String, List<String>> all = playlists.all();
                    if (all.isEmpty()) { sender.sendMessage(prefix() + ChatColor.YELLOW + "Belum ada playlist."); return; }
                    for (var e : all.entrySet()) {
                        sender.sendMessage(ChatColor.AQUA + e.getKey() + ChatColor.GRAY + " (" + e.getValue().size() + " file)");
                    }
                } else {
                    List<String> list = playlists.get(args[2]);
                    if (list == null) { sender.sendMessage(prefix() + ChatColor.RED + "Playlist tidak ditemukan: " + args[2]); return; }
                    sender.sendMessage(prefix() + ChatColor.AQUA + "Playlist '" + args[2] + "' (" + list.size() + " file):");
                    for (int i = 0; i < list.size(); i++) {
                        sender.sendMessage(ChatColor.GRAY + " " + (i + 1) + ". " + list.get(i));
                    }
                }
            }
            case "play" -> {
                // playlist play <name> [player <p1> <p2> ... | world <w> | all] [shuffle]
                if (args.length < 3) { sender.sendMessage(prefix() + ChatColor.YELLOW + "Penggunaan: /astolfovoice playlist play <name> [player <p1> ... | world <w> | all] [shuffle]"); return; }
                if (audioEngine == null) { sender.sendMessage(prefix() + ChatColor.RED + "Audio engine nonaktif."); return; }
                String name = args[2];
                if (!playlists.exists(name)) { sender.sendMessage(prefix() + ChatColor.RED + "Playlist tidak ditemukan: " + name); return; }
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
                        default -> {
                            if (tgt.equals("shuffle")) { shuffle = true; targets.addAll(Bukkit.getOnlinePlayers()); }
                        }
                    }
                } else {
                    targets.addAll(Bukkit.getOnlinePlayers());
                }
                if (targets.isEmpty()) { sender.sendMessage(prefix() + ChatColor.YELLOW + "Tidak ada target online."); return; }
                List<String> order = playlists.order(name, shuffle);
                PlaybackOptions opts = new PlaybackOptions().setVolume(config.audioDefaultVolume()).setDistance(config.audioDefaultDistance());
                PlaybackHandle handle = audioEngine.playQueue(targets, order, opts, false);
                if (handle == null) { sender.sendMessage(prefix() + ChatColor.RED + "Gagal memutar playlist."); return; }
                for (Player t : targets) playerHandles.computeIfAbsent(t.getUniqueId(), k -> new ArrayList<>()).add(handle);
                sender.sendMessage(prefix() + ChatColor.GREEN + "Memutar playlist '" + name + "' (" + order.size()
                        + " file" + (shuffle ? ", shuffle" : "") + ") ke " + targets.size() + " target.");
            }
            default -> playlistUsage(sender);
        }
    }

    private void playlistUsage(CommandSender sender) {
        sender.sendMessage(prefix() + ChatColor.AQUA + "Playlist:");
        sender.sendMessage(ChatColor.GRAY + " /astolfovoice playlist create <name>");
        sender.sendMessage(ChatColor.GRAY + " /astolfovoice playlist add <name> <file> [file ...]");
        sender.sendMessage(ChatColor.GRAY + " /astolfovoice playlist remove <name> <file>");
        sender.sendMessage(ChatColor.GRAY + " /astolfovoice playlist play <name> [player <p> ... | world <w> | all] [shuffle]");
        sender.sendMessage(ChatColor.GRAY + " /astolfovoice playlist list [name]");
        sender.sendMessage(ChatColor.GRAY + " /astolfovoice playlist shuffle <name>");
        sender.sendMessage(ChatColor.GRAY + " /astolfovoice playlist delete <name>");
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(prefix() + ChatColor.AQUA + "AstolfoVoice command (alias: /asv, /av, /astolfo):");
        sender.sendMessage(ChatColor.GRAY + " /astolfovoice play <file> player <player1> <player2> ... [volume]");
        sender.sendMessage(ChatColor.GRAY + " /astolfovoice play <file> world <world> [volume]");
        sender.sendMessage(ChatColor.GRAY + " /astolfovoice play <file> all [volume]");
        sender.sendMessage(ChatColor.GRAY + " /astolfovoice play <file> location <world> <x> <y> <z> [distance] [volume]");
        sender.sendMessage(ChatColor.GRAY + " /astolfovoice play <file> group <group>");
        sender.sendMessage(ChatColor.GRAY + " /astolfovoice stop [all | player <name>]");
        sender.sendMessage(ChatColor.GRAY + " /astolfovoice list");
        sender.sendMessage(ChatColor.GRAY + " /astolfovoice voicerange <player> [range]");
        sender.sendMessage(ChatColor.GRAY + " /astolfovoice status [player]");
        sender.sendMessage(ChatColor.GRAY + " /astolfovoice reset <player>");
        sender.sendMessage(ChatColor.GRAY + " /astolfovoice reload");
        sender.sendMessage(ChatColor.GRAY + " /astolfovoice playlist ...");
    }

    private String prefix() { return ChatColor.LIGHT_PURPLE + "[Astolfo] " + ChatColor.WHITE; }
    private void noPerm(CommandSender s) { s.sendMessage(prefix() + ChatColor.RED + "Kamu tidak punya izin."); }
    private double parseDouble(String s, double def) { try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; } }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUB) if (s.startsWith(args[0].toLowerCase())) out.add(s);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("playlist")) {
            for (String s : Arrays.asList("create","add","remove","play","list","delete","shuffle")) if (s.startsWith(args[1].toLowerCase())) out.add(s);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("voicerange") || args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("reset"))) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(p.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("play")) {
            for (String t : Arrays.asList("player","world","all","location","group")) if (t.startsWith(args[2].toLowerCase())) out.add(t);
        } else if (args.length == 4 && args[0].equalsIgnoreCase("play") && args[2].equalsIgnoreCase("player")) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase().startsWith(args[3].toLowerCase())) out.add(p.getName());
        }
        return out;
    }
}
