package id.astolfo.voicechat;

import id.astolfo.voicechat.api.PlaybackHandle;
import id.astolfo.voicechat.api.PlaybackOptions;
import id.astolfo.voicechat.audio.AudioEngine;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * AstolfoCommand — perintah /astolfo (alias av, astolfovoice).
 * Playback mp3/ogg/wav via AudioEngine (Fase 2). Non-playback fungsional penuh.
 */
public final class AstolfoCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB = Arrays.asList(
            "play", "stop", "list", "voicerange", "broadcast", "status", "reset", "reload");

    private final AstolfoVoice plugin;
    private final ServerVoiceEvents server;
    private final AstolfoConfig config;
    private final AudioEngine audioEngine;
    private final CopyOnWriteArrayList<PlaybackHandle> activeHandles = new CopyOnWriteArrayList<>();
    private final java.util.concurrent.ConcurrentHashMap<UUID, Double> voiceRangeOverrides = new java.util.concurrent.ConcurrentHashMap<>();

    public AstolfoCommand(AstolfoVoice plugin, ServerVoiceEvents server, AstolfoConfig config, AudioEngine audioEngine) {
        this.plugin = plugin;
        this.server = server;
        this.config = config;
        this.audioEngine = audioEngine;
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
            case "broadcast" -> handlePlay(sender, args);
            case "status" -> handleStatus(sender, args);
            case "reset" -> handleReset(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handlePlay(CommandSender sender, String[] args) {
        if (!sender.hasPermission("astolfo.play")) {
            sender.sendMessage(prefix() + ChatColor.RED + "Kamu tidak punya izin.");
            return;
        }
        if (audioEngine == null) {
            sender.sendMessage(prefix() + ChatColor.RED + "Audio engine nonaktif (audio.enabled=false).");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(prefix() + ChatColor.YELLOW + "Penggunaan: /astolfo play <player|world|all|location|group> ...");
            return;
        }
        String target = args[1].toLowerCase();
        List<Player> targets = new ArrayList<>();
        String file = null;
        double volume = config.audioDefaultVolume();
        double distance = config.audioDefaultDistance();

        switch (target) {
            case "player" -> {
                if (args.length < 4) { sender.sendMessage(prefix() + ChatColor.YELLOW + "Penggunaan: /astolfo play player <player> <file> [volume]"); return; }
                Player p = Bukkit.getPlayerExact(args[2]);
                if (p == null) { sender.sendMessage(prefix() + ChatColor.RED + "Player tidak ditemukan: " + args[2]); return; }
                targets.add(p);
                file = args[3];
                if (args.length > 4) volume = parseDouble(args[4], volume);
            }
            case "all" -> {
                if (args.length < 3) { sender.sendMessage(prefix() + ChatColor.YELLOW + "Penggunaan: /astolfo play all <file> [volume]"); return; }
                targets.addAll(Bukkit.getOnlinePlayers());
                file = args[2];
                if (args.length > 3) volume = parseDouble(args[3], volume);
            }
            case "world" -> {
                if (args.length < 4) { sender.sendMessage(prefix() + ChatColor.YELLOW + "Penggunaan: /astolfo play world <world> <file> [volume]"); return; }
                World w = Bukkit.getWorld(args[2]);
                if (w == null) { sender.sendMessage(prefix() + ChatColor.RED + "World tidak ditemukan: " + args[2]); return; }
                targets.addAll(w.getPlayers());
                file = args[3];
                if (args.length > 4) volume = parseDouble(args[4], volume);
            }
            case "group" -> {
                if (args.length < 4) { sender.sendMessage(prefix() + ChatColor.YELLOW + "Penggunaan: /astolfo play group <group> <file>"); return; }
                var g = server.getGroupManager().getGroupByName(args[2]);
                if (g == null) { sender.sendMessage(prefix() + ChatColor.RED + "Grup tidak ditemukan: " + args[2]); return; }
                for (UUID m : server.getGroupManager().getMembers(g.id)) {
                    Player mp = Bukkit.getPlayer(m);
                    if (mp != null) targets.add(mp);
                }
                file = args[3];
            }
            case "location" -> {
                if (args.length < 8) { sender.sendMessage(prefix() + ChatColor.YELLOW + "Penggunaan: /astolfo play location <world> <x> <y> <z> <file> [distance] [volume]"); return; }
                World w = Bukkit.getWorld(args[2]);
                if (w == null) { sender.sendMessage(prefix() + ChatColor.RED + "World tidak ditemukan: " + args[2]); return; }
                targets.addAll(w.getPlayers()); // static per player; locational full = Fase 2 lanjut
                file = args[6];
                if (args.length > 7) distance = parseDouble(args[7], distance);
                if (args.length > 8) volume = parseDouble(args[8], volume);
            }
            default -> {
                sender.sendMessage(prefix() + ChatColor.YELLOW + "Target tidak dikenal: " + target);
                return;
            }
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
        activeHandles.add(handle);
        sender.sendMessage(prefix() + ChatColor.GREEN + "Memutar '" + resolved.getName() + "' ke "
                + targets.size() + " target (" + target + ").");
    }

    private void handleStop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("astolfo.stop")) {
            sender.sendMessage(prefix() + ChatColor.RED + "Kamu tidak punya izin.");
            return;
        }
        if (audioEngine == null) return;
        if (args.length < 2 || args[1].equalsIgnoreCase("all")) {
            int n = activeHandles.size();
            audioEngine.stopAll();
            activeHandles.clear();
            sender.sendMessage(prefix() + ChatColor.GREEN + "Stop " + n + " playback.");
        } else {
            sender.sendMessage(prefix() + ChatColor.YELLOW + "Stop per-target/id menyusul; pakai /astolfo stop all.");
            audioEngine.stopAll();
            activeHandles.clear();
        }
    }

    private void handleList(CommandSender sender) {
        File audioDir = new File(plugin.getDataFolder(), config.audioFolder());
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

    private void handleVoiceRange(CommandSender sender, String[] args) {
        if (!sender.hasPermission("astolfo.voicerange")) {
            sender.sendMessage(prefix() + ChatColor.RED + "Kamu tidak punya izin.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(prefix() + ChatColor.YELLOW + "Penggunaan: /astolfo voicerange <player> [range]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(prefix() + ChatColor.RED + "Player tidak ditemukan: " + args[1]);
            return;
        }
        if (args.length == 2) {
            Double override = voiceRangeOverrides.get(target.getUniqueId());
            double range = override != null ? override : config.voiceRange();
            sender.sendMessage(prefix() + ChatColor.AQUA + target.getName() + " voice range: " + range);
        } else {
            try {
                double range = Double.parseDouble(args[2]);
                range = Math.min(range, config.maxPlayerRangeOverride());
                voiceRangeOverrides.put(target.getUniqueId(), range);
                sender.sendMessage(prefix() + ChatColor.GREEN + target.getName() + " voice range di-set " + range);
            } catch (NumberFormatException e) {
                sender.sendMessage(prefix() + ChatColor.RED + "Range tidak valid: " + args[2]);
            }
        }
    }

    private void handleStatus(CommandSender sender, String[] args) {
        if (args.length > 1) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(prefix() + ChatColor.RED + "Player tidak ditemukan: " + args[1]);
                return;
            }
            sendPlayerStatus(sender, target);
        } else {
            int connected = server.getPlayerStateManager().allStates().size();
            int active = audioEngine != null ? audioEngine.activeCount() : 0;
            sender.sendMessage(prefix() + ChatColor.AQUA + "Status: " + connected + " player tracked, "
                    + server.getGroupManager().allGroups().size() + " grup, " + active + " playback aktif");
        }
    }

    private void sendPlayerStatus(CommandSender sender, Player target) {
        PlayerState state = server.getPlayerStateManager().getState(target.getUniqueId());
        Double range = voiceRangeOverrides.get(target.getUniqueId());
        if (state == null) {
            sender.sendMessage(prefix() + ChatColor.YELLOW + target.getName() + ": belum ter-track.");
            return;
        }
        sender.sendMessage(prefix() + ChatColor.AQUA + target.getName()
                + " | disabled=" + state.isDisabled()
                + " | disconnected=" + state.isDisconnected()
                + " | group=" + (state.hasGroup() ? state.getGroup() : "-")
                + " | range=" + (range != null ? range : config.voiceRange()));
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("astolfo.reset")) {
            sender.sendMessage(prefix() + ChatColor.RED + "Kamu tidak punya izin.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(prefix() + ChatColor.YELLOW + "Penggunaan: /astolfo reset <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(prefix() + ChatColor.RED + "Player tidak ditemukan: " + args[1]);
            return;
        }
        voiceRangeOverrides.remove(target.getUniqueId());
        sender.sendMessage(prefix() + ChatColor.GREEN + "Reset " + target.getName() + " (voice range & override).");
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("astolfo.reload")) {
            sender.sendMessage(prefix() + ChatColor.RED + "Kamu tidak punya izin.");
            return;
        }
        plugin.reloadConfig();
        sender.sendMessage(prefix() + ChatColor.GREEN + "Config di-reload (socket tidak restart kecuali port/MTU berubah).");
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(prefix() + ChatColor.AQUA + "AstolfoVoice command:");
        sender.sendMessage(ChatColor.GRAY + " /astolfo play player <player> <file> [volume]");
        sender.sendMessage(ChatColor.GRAY + " /astolfo play world <world> <file> [volume]");
        sender.sendMessage(ChatColor.GRAY + " /astolfo play all <file> [volume]");
        sender.sendMessage(ChatColor.GRAY + " /astolfo play location <world> <x> <y> <z> <file> [distance] [volume]");
        sender.sendMessage(ChatColor.GRAY + " /astolfo play group <group> <file>");
        sender.sendMessage(ChatColor.GRAY + " /astolfo stop [all]");
        sender.sendMessage(ChatColor.GRAY + " /astolfo list");
        sender.sendMessage(ChatColor.GRAY + " /astolfo voicerange <player> [range]");
        sender.sendMessage(ChatColor.GRAY + " /astolfo status [player]");
        sender.sendMessage(ChatColor.GRAY + " /astolfo reset <player>");
        sender.sendMessage(ChatColor.GRAY + " /astolfo reload");
    }

    private String prefix() {
        return ChatColor.LIGHT_PURPLE + "[Astolfo] " + ChatColor.WHITE;
    }

    private double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUB) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("play") || args[0].equalsIgnoreCase("broadcast"))) {
            for (String t : Arrays.asList("player", "world", "all", "location", "group")) {
                if (t.startsWith(args[1].toLowerCase())) out.add(t);
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("voicerange")
                || args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("reset"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(p.getName());
            }
        }
        return out;
    }
}
