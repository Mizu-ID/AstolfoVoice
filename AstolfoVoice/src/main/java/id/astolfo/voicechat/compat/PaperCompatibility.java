package id.astolfo.voicechat.compat;

import id.astolfo.voicechat.net.NetManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Compatibility layer minimal, NMS-free. Mendeteksi Paper vs Spigot dan
 * mengimplementasikan addChannel/removeChannel/runTask/isCompatible.
 *
 * - addChannel/removeChannel: kirim "minecraft:register"/"minecraft:unregister"
 *   plugin message berisi daftar channel (konvensi yang didukung client SVC).
 * - runTask: Bukkit scheduler sync task.
 * - isCompatible: delegasi ke ChannelChecker (map compatibility server).
 */
public final class PaperCompatibility implements NetManager.Compatibility {

    private final Plugin plugin;
    private final ChannelChecker channelChecker;
    private static final byte[] REGISTER = "minecraft:register".getBytes(StandardCharsets.UTF_8);
    private static final byte[] UNREGISTER = "minecraft:unregister".getBytes(StandardCharsets.UTF_8);

    public PaperCompatibility(Plugin plugin, ChannelChecker channelChecker) {
        this.plugin = plugin;
        this.channelChecker = channelChecker;
    }

    /** Cek apakah runtime Paper. */
    public static boolean isPaper() {
        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
            return true;
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    @Override
    public void addChannel(Player player, String channel) {
        sendChannelList(player, REGISTER, channel);
    }

    @Override
    public void removeChannel(Player player, String channel) {
        sendChannelList(player, UNREGISTER, channel);
    }

    private void sendChannelList(Player player, byte[] prefix, String channel) {
        try {
            byte[] name = channel.getBytes(StandardCharsets.UTF_8);
            byte[] payload = new byte[prefix.length + 1 + name.length];
            System.arraycopy(prefix, 0, payload, 0, prefix.length);
            payload[prefix.length] = (byte) '\n';
            System.arraycopy(name, 0, payload, prefix.length + 1, name.length);
            String channelName = (prefix == REGISTER) ? "minecraft:register" : "minecraft:unregister";
            player.sendPluginMessage(plugin, channelName, payload);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to (un)register channel " + channel + " for " + player.getName(), e);
        }
    }

    @Override
    public void runTask(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    @Override
    public boolean isCompatible(Player player) {
        return channelChecker != null && channelChecker.isCompatible(player);
    }
}
