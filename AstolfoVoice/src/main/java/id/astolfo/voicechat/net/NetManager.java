package id.astolfo.voicechat.net;

import id.astolfo.voicechat.voice.common.FriendlyByteBuf;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * NetManager — registrasi plugin channel + kirim paket ke client.
 * Channel namespace "voicechat:*" (byte-exact SVC). Mengandalkan Compatibility
 * untuk addChannel/removeChannel/runTask supaya NMS-free.
 */
public final class NetManager {

    private final Plugin plugin;
    private final Compatibility compatibility;
    private final PacketRateLimiter rateLimiter;
    private final Set<String> channelNames = new HashSet<>();

    /** Abstraction ringan untuk operasi yang beda antar Paper/Spigot. */
    public interface Compatibility {
        void addChannel(Player player, String channel);

        void removeChannel(Player player, String channel);

        void runTask(Runnable runnable);

        boolean isCompatible(Player player);

        /** Hook ke map compatibility version server (untuk isCompatible impl). */
        interface ChannelChecker {
            boolean isCompatible(Player player);
        }
    }

    public NetManager(Plugin plugin, Compatibility compatibility, PacketRateLimiter rateLimiter) {
        this.plugin = plugin;
        this.compatibility = compatibility;
        this.rateLimiter = rateLimiter;
    }

    public Compatibility getCompatibility() {
        return compatibility;
    }

    public void onEnable() {
        channelNames.clear();
        // Incoming (client→server)
        registerIncoming(RequestSecretPacket.class, RequestSecretPacket::fromBytes);
        registerIncoming(UpdateStatePacket.class, UpdateStatePacket::fromBytes);
        registerIncoming(CreateGroupPacket.class, CreateGroupPacket::fromBytes);
        registerIncoming(JoinGroupPacket.class, JoinGroupPacket::fromBytes);
        registerIncoming(LeaveGroupPacket.class, LeaveGroupPacket::fromBytes);
        // Outgoing (server→client)
        registerOutgoing(SecretPacket.class);
        registerOutgoing(PlayerStatesPacket.class);
        registerOutgoing(PlayerStatePacket.class);
        registerOutgoing(RemovePlayerStatePacket.class);
        registerOutgoing(AddGroupPacket.class);
        registerOutgoing(RemoveGroupPacket.class);
        registerOutgoing(JoinedGroupPacket.class);
        registerOutgoing(AddCategoryPacket.class);
        registerOutgoing(RemoveCategoryPacket.class);
    }

    public void onDisable() {
        Set<String> incoming = Bukkit.getMessenger().getIncomingChannels(plugin);
        Set<String> outgoing = Bukkit.getMessenger().getOutgoingChannels(plugin);
        for (String channel : incoming) {
            Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, channel);
        }
        for (String channel : outgoing) {
            Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, channel);
        }
        channelNames.clear();
    }

    /** Daftarkan semua channel outgoing ke player (dipanggil saat join). */
    public void registerChannelsToPlayer(Player player) {
        for (String channel : Bukkit.getMessenger().getOutgoingChannels(plugin)) {
            compatibility.addChannel(player, channel);
        }
    }

    public void unregisterChannelsFromPlayer(Player player) {
        for (String channel : Bukkit.getMessenger().getOutgoingChannels(plugin)) {
            compatibility.removeChannel(player, channel);
        }
    }

    public Set<String> getChannelNames() {
        return channelNames;
    }

    private <T extends Packet> void registerIncoming(Class<T> packetClass, Function<FriendlyByteBuf, T> reader) {
        try {
            T template = packetClass.getDeclaredConstructor().newInstance();
            String channel = template.getID().toString();
            channelNames.add(channel);
            // Parameter lambda sengaja dinamai 'p' agar tidak menutupi field 'plugin'.
            Bukkit.getMessenger().registerIncomingPluginChannel(plugin, channel, (p, player, bytes) -> {
                if (!rateLimiter.allow(player.getUniqueId())) {
                    player.kickPlayer("Packet rate limit exceeded");
                    return;
                }
                try {
                    FriendlyByteBuf buf = new FriendlyByteBuf(bytes);
                    T packet = reader.apply(buf);
                    packet.onPacket(player);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to handle incoming packet " + channel + ": " + e.getMessage());
                }
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to register incoming packet " + packetClass.getSimpleName(), e);
        }
    }

    private void registerOutgoing(Class<? extends Packet> packetClass) {
        try {
            Packet template = packetClass.getDeclaredConstructor().newInstance();
            String channel = template.getID().toString();
            channelNames.add(channel);
            Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, channel);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to register outgoing packet " + packetClass.getSimpleName(), e);
        }
    }

    /** Kirim paket outgoing ke player (main-thread safe via compatibility.runTask). */
    public void sendToClient(Player player, Packet packet) {
        compatibility.runTask(() -> {
            if (!compatibility.isCompatible(player)) {
                return;
            }
            try {
                FriendlyByteBuf buf = new FriendlyByteBuf();
                packet.toBytes(buf);
                player.sendPluginMessage(plugin, packet.getID().toString(), buf.toByteArray());
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send packet " + packet.getID() + " to " + player.getName() + ": " + e.getMessage());
            }
        });
    }
}
