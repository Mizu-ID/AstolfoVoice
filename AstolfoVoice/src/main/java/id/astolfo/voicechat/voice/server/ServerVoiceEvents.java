package id.astolfo.voicechat.voice.server;

import id.astolfo.voicechat.audio.OpusManager;
import id.astolfo.voicechat.config.AstolfoConfig;
import id.astolfo.voicechat.net.AddGroupPacket;
import id.astolfo.voicechat.net.JoinedGroupPacket;
import id.astolfo.voicechat.net.NetManager;
import id.astolfo.voicechat.net.RemoveGroupPacket;
import id.astolfo.voicechat.net.RequestSecretPacket;
import id.astolfo.voicechat.net.SecretPacket;
import id.astolfo.voicechat.net.UpdateStatePacket;
import id.astolfo.voicechat.net.CreateGroupPacket;
import id.astolfo.voicechat.net.JoinGroupPacket;
import id.astolfo.voicechat.net.LeaveGroupPacket;
import id.astolfo.voicechat.voice.common.AuthenticateAckPacket;
import id.astolfo.voicechat.voice.common.AuthenticatePacket;
import id.astolfo.voicechat.voice.common.ClientConnection;
import id.astolfo.voicechat.voice.common.Codec;
import id.astolfo.voicechat.voice.common.ConnectionCheckAckPacket;
import id.astolfo.voicechat.voice.common.ConnectionCheckPacket;
import id.astolfo.voicechat.voice.common.FriendlyByteBuf;
import id.astolfo.voicechat.voice.common.GroupType;
import id.astolfo.voicechat.voice.common.KeepAlivePacket;
import id.astolfo.voicechat.voice.common.MicPacket;
import id.astolfo.voicechat.voice.common.NetworkMessage;
import id.astolfo.voicechat.voice.common.PlayerState;
import id.astolfo.voicechat.voice.common.PlayerSoundPacket;
import id.astolfo.voicechat.voice.common.PingPacket;
import id.astolfo.voicechat.voice.common.Secret;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.SocketAddress;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ServerVoiceEvents — handshake state machine + dispatch paket UDP. Inti server.
 * State per UUID (PROTOCOL_REFERENCE §6):
 *   LOGGED_IN → SECRET_SENT → AWAITING_UDP_AUTH → UNCHECKED → CONNECTED → DISCONNECTED.
 */
public final class ServerVoiceEvents implements NetManager.Compatibility.ChannelChecker,
        VoiceServer.PacketHandler,
        RequestSecretPacket.Handler,
        UpdateStatePacket.Handler,
        CreateGroupPacket.Handler,
        JoinGroupPacket.Handler,
        LeaveGroupPacket.Handler {

    private final Logger logger;
    private final AstolfoConfig config;
    private final VoiceServer voiceServer;
    private final NetManager netManager;
    private final PlayerStateManager playerStateManager;
    private final GroupManager groupManager;
    private final CategoryManager categoryManager;
    private final PingManager pingManager;
    private final ProximityResolver proximityResolver;

    // Handshake state
    private final ConcurrentHashMap<UUID, Integer> clientCompatibilities = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ClientConnection> unCheckedConnections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ClientConnection> connections = new ConcurrentHashMap<>();

    private final AtomicLong seq = new AtomicLong(0);
    private volatile boolean running = false;

    public ServerVoiceEvents(Logger logger, AstolfoConfig config, VoiceServer voiceServer, NetManager netManager, OpusManager opus) {
        this.logger = logger;
        this.config = config;
        this.voiceServer = voiceServer;
        this.netManager = netManager;
        this.playerStateManager = new PlayerStateManager();
        this.groupManager = new GroupManager();
        this.categoryManager = new CategoryManager();
        this.pingManager = new PingManager();
        this.proximityResolver = new ProximityResolver(config, groupManager, opus);
    }

    // ---- Lifecycle ----

    public void init() {
        RequestSecretPacket.setHandler(this);
        UpdateStatePacket.setHandler(this);
        CreateGroupPacket.setHandler(this);
        JoinGroupPacket.setHandler(this);
        LeaveGroupPacket.setHandler(this);

        running = true;
        voiceServer.startProcessor();
        org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("AstolfoVoice");
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                this::keepAliveLoop, 20L, Math.max(1L, config.keepAlive() / 50L));
    }

    public void close() {
        running = false;
        connections.clear();
        unCheckedConnections.clear();
    }

    // ---- ChannelChecker ----
    @Override
    public boolean isCompatible(Player player) {
        return clientCompatibilities.containsKey(player.getUniqueId());
    }

    // ---- RequestSecretPacket.Handler ----
    @Override
    public void onRequestSecret(Player player, int compatibilityVersion) {
        UUID uuid = player.getUniqueId();
        clientCompatibilities.put(uuid, compatibilityVersion);

        if (compatibilityVersion != config.defaultCompatibilityVersion()) {
            logger.info("Player " + player.getName() + " uses compatibility version " + compatibilityVersion
                    + " (server=" + config.defaultCompatibilityVersion() + ")");
        }

        if (!voiceServer.hasSecret(uuid)) {
            voiceServer.putSecret(uuid, new Secret());
        }
        Secret secret = voiceServer.getSecret(uuid);

        Codec codec;
        try {
            codec = Codec.valueOf(config.codecName());
        } catch (IllegalArgumentException e) {
            codec = Codec.VOIP;
        }

        String host = config.voiceHost();
        if (host == null || host.isEmpty()) {
            host = "";
        }

        SecretPacket packet = new SecretPacket(
                secret, config.port(), uuid, codec, config.mtuSize(),
                config.voiceRange(), config.keepAlive(), config.groupsEnabled(),
                host, config.allowRecording());
        netManager.sendToClient(player, packet);
    }

    // ---- UpdateStatePacket.Handler ----
    @Override
    public void onUpdateState(Player player, boolean disabled) {
        playerStateManager.onUpdateStatePacket(player, disabled);
    }

    // ---- CreateGroupPacket.Handler ----
    @Override
    public void onCreateGroup(Player player, String name, String password, GroupType type) {
        if (!config.groupsEnabled()) return;
        GroupManager.Group group = groupManager.createGroup(name, password, false, false, type);
        org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("AstolfoVoice");
        Bukkit.getScheduler().runTask(plugin, () -> {
            AddGroupPacket add = new AddGroupPacket(group.toClientGroup());
            for (Player p : Bukkit.getOnlinePlayers()) {
                netManager.sendToClient(p, add);
            }
        });
        joinPlayer(player, group.id, password);
    }

    // ---- JoinGroupPacket.Handler ----
    @Override
    public void onJoinGroup(Player player, UUID groupId, String password) {
        if (!config.groupsEnabled()) return;
        joinPlayer(player, groupId, password);
    }

    private void joinPlayer(Player player, UUID groupId, String password) {
        boolean ok = groupManager.joinGroup(player.getUniqueId(), groupId, password);
        if (ok) {
            PlayerState state = playerStateManager.getState(player.getUniqueId());
            if (state != null) state.setGroup(groupId);
            netManager.sendToClient(player, new JoinedGroupPacket(groupId, false));
        } else {
            netManager.sendToClient(player, new JoinedGroupPacket(null, true));
        }
    }

    // ---- LeaveGroupPacket.Handler ----
    @Override
    public void onLeaveGroup(Player player) {
        groupManager.leaveGroup(player.getUniqueId());
        PlayerState state = playerStateManager.getState(player.getUniqueId());
        if (state != null) state.setGroup(null);
        netManager.sendToClient(player, new JoinedGroupPacket(null, false));
    }

    public void removeGroup(UUID groupId) {
        groupManager.removeGroup(groupId);
        org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("AstolfoVoice");
        Bukkit.getScheduler().runTask(plugin, () -> {
            RemoveGroupPacket rm = new RemoveGroupPacket(groupId);
            for (Player p : Bukkit.getOnlinePlayers()) {
                netManager.sendToClient(p, rm);
            }
        });
    }

    // ---- VoiceServer.PacketHandler (UDP) ----

    @Override
    public void onPacket(NetworkMessage message) {
        try {
            Object p = message.getPacket();
            if (p instanceof AuthenticatePacket ap) {
                onAuthenticate(ap, message.getAddress());
            } else if (p instanceof ConnectionCheckPacket) {
                onConnectionCheck(message);
            } else if (p instanceof KeepAlivePacket) {
                onKeepAliveResponse(message);
            } else if (p instanceof PingPacket ping) {
                pingManager.onPongPacket(ping);
            } else if (p instanceof MicPacket mic) {
                onMicPacket(mic, message);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error handling UDP packet", e);
        }
    }

    @Override
    public boolean onPing(SocketAddress socketAddress, UUID playerID, FriendlyByteBuf remaining) {
        return pingManager.onPacket(socketAddress, playerID);
    }

    private void onAuthenticate(AuthenticatePacket packet, SocketAddress address) {
        UUID uuid = packet.getPlayerUUID();
        Secret expected = voiceServer.getSecret(uuid);
        if (expected == null || !expected.equals(packet.getSecret())) {
            logger.fine("Authenticate failed for " + uuid);
            return;
        }
        ClientConnection conn = new ClientConnection(uuid, address);
        unCheckedConnections.put(uuid, conn);
        voiceServer.send(conn, new NetworkMessage(new AuthenticateAckPacket()));
        playerStateManager.setDisconnected(uuid, false);
    }

    private void onConnectionCheck(NetworkMessage message) {
        UUID uuid = null;
        for (var e : unCheckedConnections.entrySet()) {
            if (e.getValue().getAddress().equals(message.getAddress())) {
                uuid = e.getKey();
                break;
            }
        }
        if (uuid == null) return;
        ClientConnection conn = unCheckedConnections.remove(uuid);
        if (conn == null) return;
        connections.put(uuid, conn);
        voiceServer.send(conn, new NetworkMessage(new ConnectionCheckAckPacket()));
        logger.info("Player " + uuid + " connected to voice chat");
    }

    private void onKeepAliveResponse(NetworkMessage message) {
        for (var e : connections.entrySet()) {
            if (e.getValue().getAddress().equals(message.getAddress())) {
                e.getValue().setLastKeepAliveResponse(System.currentTimeMillis());
                break;
            }
        }
    }

    private void onMicPacket(MicPacket mic, NetworkMessage message) {
        UUID senderUuid = null;
        for (var e : connections.entrySet()) {
            if (e.getValue().getAddress().equals(message.getAddress())) {
                senderUuid = e.getKey();
                break;
            }
        }
        if (senderUuid == null) return;
        Player sender = Bukkit.getPlayer(senderUuid);
        if (sender == null) return;
        proximityResolver.processMic(sender, mic, this, message.getTimestamp());
    }

    /** Kirim PlayerSoundPacket ke listener (dipanggil ProximityResolver). */
    public void sendPlayerSound(Player listener, UUID senderUuid, byte[] opus, boolean whispering, float distance, String category) {
        ClientConnection conn = connections.get(listener.getUniqueId());
        if (conn == null) return;
        long sequence = seq.incrementAndGet();
        PlayerSoundPacket sound = new PlayerSoundPacket(senderUuid, senderUuid, opus, sequence, whispering, distance, category);
        voiceServer.send(conn, new NetworkMessage(sound));
    }

    // ---- Keepalive loop ----
    private void keepAliveLoop() {
        if (!running) return;
        long now = System.currentTimeMillis();
        long timeout = config.keepAlive() * 10L;
        for (var e : new java.util.HashMap<>(connections).entrySet()) {
            UUID uuid = e.getKey();
            ClientConnection conn = e.getValue();
            if (now - conn.getLastKeepAliveResponse() > timeout) {
                connections.remove(uuid);
                voiceServer.removeSecret(uuid);
                playerStateManager.setDisconnected(uuid, true);
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    voiceServer.putSecret(uuid, new Secret());
                    onRequestSecret(p, clientCompatibilities.getOrDefault(uuid, config.defaultCompatibilityVersion()));
                }
                continue;
            }
            voiceServer.send(conn, new NetworkMessage(new KeepAlivePacket()));
        }
    }

    // ---- Quit/disconnect ----
    public void disconnectClient(UUID uuid) {
        connections.remove(uuid);
        unCheckedConnections.remove(uuid);
        voiceServer.removeSecret(uuid);
        clientCompatibilities.remove(uuid);
        playerStateManager.setDisconnected(uuid, true);
    }

    // ---- Accessors ----
    public PlayerStateManager getPlayerStateManager() {
        return playerStateManager;
    }

    public GroupManager getGroupManager() {
        return groupManager;
    }

    public CategoryManager getCategoryManager() {
        return categoryManager;
    }

    public ProximityResolver getProximityResolver() {
        return proximityResolver;
    }

    public VoiceServer getVoiceServer() {
        return voiceServer;
    }

    public ConcurrentHashMap<UUID, ClientConnection> getConnections() {
        return connections;
    }
}
