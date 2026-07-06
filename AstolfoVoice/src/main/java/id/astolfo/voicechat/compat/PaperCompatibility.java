package id.astolfo.voicechat.compat;

import id.astolfo.voicechat.net.NetManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Compatibility layer minimal, NMS-free. Mendeteksi Paper vs Spigot dan
 * mengimplementasikan runTask/isCompatible.
 *
 * Catatan channel announcement: Bukkit MELARANG plugin mengirim pada channel
 * reserved "minecraft:register"/"minecraft:unregister" (ChannelNotRegistered-
 * Exception). Announcement channel voicechat:* ke client ditangani otomatis
 * oleh CraftBukkit saat join, karena NetManager mendaftarkan SEMUA channel
 * (termasuk outgoing) sebagai incoming — lihat NetManager.registerOutgoing.
 *
 * - runTask: langsung bila sudah di main thread, selain itu Bukkit scheduler.
 * - isCompatible: delegasi ke ChannelChecker (map compatibility server).
 */
public final class PaperCompatibility implements NetManager.Compatibility {

    private final Plugin plugin;
    private final ChannelChecker channelChecker;

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
