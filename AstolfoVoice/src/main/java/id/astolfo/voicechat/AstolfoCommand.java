package id.astolfo.voicechat;

import id.astolfo.voicechat.config.AstolfoConfig;
import id.astolfo.voicechat.voice.common.PlayerState;
import id.astolfo.voicechat.voice.server.GroupManager;
import id.astolfo.voicechat.voice.server.ServerVoiceEvents;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

/**
 * AstolfoCommand — perintah /astolfo (alias av, astolfovoice).
 * Subcommand: play (player|world|all|location|group), stop, list,
 *             voicerange, broadcast, status, reset, reload.
 *
 * Playback (mp3/ogg/wav) diaktifkan penuh di Fase 2 setelah audio engine
 * (decoder + resampler + streaming) terimplementasi. Saat ini play mengembalikan
 * info file tersedia + status engine. Command non-playback fungsional sekarang.
 */
public final class AstolfoCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB = Arrays.asList(
            "play", "stop", "list", "voicerange", "broadcast", "status", "reset", "reload");

    private final AstolfoVoice plugin;
    private final ServerVoiceEvents server;
    private final AstolfoConfig config;
    private final java.util.concurrent.ConcurrentHashMap<UUID, Double> voiceRangeOverrides = new java.util.concurrent.ConcurrentHashMap<>();

    public AstolfoCommand(AstolfoVoice plugin, ServerVoiceEvents server, AstolfoConfig config) {
        this.plugin = plugin;
        this.server = server;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "play" -> handlePlay(sender, args);
            case "stop" -> sender.sendMessage(prefix() + "Stop: gunakan /astolfo stop [player|world|all|id] (Fase 2 playback engine).");
            case "list" -> handleList(sender);
            case "voicerange" -> handleVoiceRange(sender, args);
            case "broadcast" -> handleBroadcast(sender, args);
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
        if (args.length < 2) {
            sender.sendMessage(prefix() + ChatColor.YELLOW + "Penggunaan: /astolfo play <player|world|all|location|group> ...");
            return;
        }
        String target = args[1].toLowerCase();
        File audioDir = new File(plugin.getDataFolder(), config.audioFolder());
        String file = switch (target) {
            case "player" -> args.length > 3 ? args[3] : null;
            case "all" -> args.length > 2 ? args[2] : null;
            case "world" -> args.length > 3 ? args[3] : null;
            case "group" -> args.length > 3 ? args[3] : null;
            case "location" -> args.length > 6 ? args[6] : null;
            default -> null;
        };
        if (file == null) {
            sender.sendMessage(prefix() + ChatColor.YELLOW + "File audio tidak dispesifikkan. Contoh: /astolfo play player agus magis1.mp3");
            return;
        }
        File resolved = resolveAudioFile(audioDir, file);
        if (resolved == null || !resolved.exists()) {
            sender.sendMessage(prefix() + ChatColor.RED + "File audio tidak ditemukan: " + file
                    + " (cari di " + audioDir.getPath() + ", format mp3/ogg/wav)");
            return;
        }
        // TODO Fase 2: decode + resample + streaming audio engine (Concentrus Opus + decoder).
        sender.sendMessage(prefix() + ChatColor.GREEN + "Play " + target + " '" + resolved.getName()
                + "' dijadwalkan. Audio engine streaming (Fase 2) akan memutar saat aktif.");
    }

    private File resolveAudioFile(File dir, String name) {
        if (name == null) return null;
        // coba langsung
        File direct = new File(dir, name);
        if (direct.exists()) return direct;
        // cari dengan ekstensi umum
        for (String ext : new String[]{"", ".mp3", ".ogg", ".wav"}) {
            File f = new File(dir, name + ext);
            if (f.exists()) return f;
        }
        // case-insensitive
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().equalsIgnoreCase(name) || f.getName().equalsIgnoreCase(name + ".mp3")
                        || f.getName().equalsIgnoreCase(name + ".ogg") || f.getName().equalsIgnoreCase(name + ".wav")) {
                    return f;
                }
            }
        }
        return null;
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
                range = Math.min(range, config.shoutRange() * 2); // batas atas aman
                voiceRangeOverrides.put(target.getUniqueId(), range);
                sender.sendMessage(prefix() + ChatColor.GREEN + target.getName() + " voice range di-set " + range);
            } catch (NumberFormatException e) {
                sender.sendMessage(prefix() + ChatColor.RED + "Range tidak valid: " + args[2]);
            }
        }
    }

    private void handleBroadcast(CommandSender sender, String[] args) {
        if (!sender.hasPermission("astolfo.broadcast")) {
            sender.sendMessage(prefix() + ChatColor.RED + "Kamu tidak punya izin.");
            return;
        }
        // alias play; delegasi ke handlePlay
        handlePlay(sender, args);
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
            sender.sendMessage(prefix() + ChatColor.AQUA + "Status server: " + connected + " player tracked, "
                    + server.getGroupManager().allGroups().size() + " grup");
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
        sender.sendMessage(ChatColor.GRAY + " /astolfo play location <world> <x> <y> <z> <file> [distance]");
        sender.sendMessage(ChatColor.GRAY + " /astolfo play group <group> <file>");
        sender.sendMessage(ChatColor.GRAY + " /astolfo stop [player|world|all|id]");
        sender.sendMessage(ChatColor.GRAY + " /astolfo list");
        sender.sendMessage(ChatColor.GRAY + " /astolfo voicerange <player> [range]");
        sender.sendMessage(ChatColor.GRAY + " /astolfo broadcast <player|world|all> <file>");
        sender.sendMessage(ChatColor.GRAY + " /astolfo status [player]");
        sender.sendMessage(ChatColor.GRAY + " /astolfo reset <player>");
        sender.sendMessage(ChatColor.GRAY + " /astolfo reload");
    }

    private String prefix() {
        return ChatColor.LIGHT_PURPLE + "[Astolfo] " + ChatColor.WHITE;
    }

    // ---- Tab complete ----
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

    public Double getVoiceRangeOverride(UUID uuid) {
        return voiceRangeOverrides.get(uuid);
    }
}
