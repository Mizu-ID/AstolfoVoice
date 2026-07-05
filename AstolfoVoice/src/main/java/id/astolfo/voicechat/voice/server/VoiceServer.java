package id.astolfo.voicechat.voice.server;

import id.astolfo.voicechat.voice.common.ClientConnection;
import id.astolfo.voicechat.voice.common.NetworkMessage;
import id.astolfo.voicechat.voice.common.RawUdpPacket;
import id.astolfo.voicechat.voice.common.Secret;
import id.astolfo.voicechat.voice.common.SecretProvider;
import id.astolfo.voicechat.voice.common.Utils;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * VoiceServer — UDP server (DatagramSocket) + dispatch paket. Async penuh.
 *
 * Thread model (IMPLEMENTATION_PLAN §E):
 *  - 1 UDP reader thread (daemon, blocking receive) → enqueue RawUdpPacket.
 *  - Virtual thread per packet processor (decode → dispatch ke handler).
 *  - Send: DatagramSocket.send (thread-safe).
 *
 * Mengimplementasikan SecretProvider untuk NetworkMessage.readPacketServer.
 */
public final class VoiceServer implements SecretProvider {

    private final Logger logger;
    private final int port;
    private final String bindAddress;
    private volatile PacketHandler handler;

    private DatagramSocket socket;
    private Thread readerThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final ExecutorService processorPool;
    private final LinkedBlockingQueue<RawUdpPacket> queue;

    // State handshake (diisi oleh ServerVoiceEvents via SecretProvider)
    private final ConcurrentHashMap<UUID, Secret> secrets = new ConcurrentHashMap<>();

    public VoiceServer(Logger logger, int port, String bindAddress, PacketHandler handler,
                       boolean virtualThreads, int queueCapacity) {
        this.logger = logger;
        this.port = port;
        this.bindAddress = bindAddress;
        this.handler = handler;
        this.processorPool = virtualThreads
                ? Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("astolfo-udp-", 0).factory())
                : Executors.newFixedThreadPool(4);
        this.queue = queueCapacity > 0 ? new LinkedBlockingQueue<>(queueCapacity) : new LinkedBlockingQueue<>();
    }

    /** Set handler setelah server events dibuat (handler boleh null saat construct). */
    public void setHandler(PacketHandler handler) {
        this.handler = handler;
    }

    // ---- SecretProvider ----
    @Override
    public boolean hasSecret(UUID playerUUID) {
        return secrets.containsKey(playerUUID);
    }

    @Override
    public Secret getSecret(UUID playerUUID) {
        return secrets.get(playerUUID);
    }

    public Secret putSecret(UUID playerUUID, Secret secret) {
        return secrets.put(playerUUID, secret);
    }

    public Secret removeSecret(UUID playerUUID) {
        return secrets.remove(playerUUID);
    }

    public void clearSecrets() {
        secrets.clear();
    }

    // ---- Lifecycle ----

    public synchronized void start() throws Exception {
        if (running.get()) return;
        InetSocketAddress bind = resolveBind();
        socket = new DatagramSocket(bind);
        socket.setReceiveBufferSize(Utils.MAX_VOICE_CHAT_PACKET_SIZE * 4);
        running.set(true);
        logger.info("Voice UDP server starting on " + socket.getLocalSocketAddress());
        readerThread = new Thread(this::readerLoop, "astolfo-udp-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public synchronized void close() {
        running.set(false);
        if (socket != null) {
            socket.close();
        }
        if (readerThread != null) {
            readerThread.interrupt();
        }
        processorPool.shutdown();
        try {
            processorPool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        secrets.clear();
    }

    private InetSocketAddress resolveBind() throws Exception {
        if (bindAddress == null || bindAddress.isEmpty() || "*".equals(bindAddress)) {
            return new InetSocketAddress((InetAddress) null, port);
        }
        return new InetSocketAddress(InetAddress.getByName(bindAddress), port);
    }

    private void readerLoop() {
        byte[] buf = new byte[Utils.MAX_VOICE_CHAT_PACKET_SIZE * 2 + 64];
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                DatagramPacket dp = new DatagramPacket(buf, buf.length);
                socket.receive(dp);
                byte[] data = new byte[dp.getLength()];
                System.arraycopy(dp.getData(), dp.getOffset(), data, 0, dp.getLength());
                RawUdpPacket raw = new RawUdpPacket(data, dp.getSocketAddress(), System.currentTimeMillis());
                if (!queue.offer(raw)) {
                    // queue penuh → drop (pertahankan latency > kelengkapan)
                    logger.warning("UDP queue full, dropping packet from " + dp.getSocketAddress());
                }
            } catch (Exception e) {
                if (running.get()) {
                    logger.log(Level.WARNING, "Error receiving UDP packet", e);
                }
            }
        }
    }

    /** Proses antrian — dijalankan oleh scheduler sendiri. */
    public void processLoop() {
        while (running.get()) {
            try {
                RawUdpPacket raw = queue.poll(100, TimeUnit.MILLISECONDS);
                if (raw == null) continue;
                processorPool.submit(() -> process(raw));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void process(RawUdpPacket raw) {
        PacketHandler h = this.handler;
        if (h == null) return;
        try {
            NetworkMessage msg = NetworkMessage.readPacketServer(raw, this, h::onPing);
            if (msg == null) return;
            h.onPacket(msg);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error processing UDP packet from " + raw.getSocketAddress(), e);
        }
    }

    // ---- Send ----

    /** Kirim paket terenkripsi ke koneksi client. */
    public void send(ClientConnection connection, NetworkMessage message) {
        try {
            UUID uuid = connection.getPlayerUUID();
            Secret secret = secrets.get(uuid);
            if (secret == null) return;
            byte[] data = message.writeServer(secret, uuid);
            socket.send(new DatagramPacket(data, data.length, connection.getAddress()));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to send UDP packet to " + connection.getPlayerUUID(), e);
        }
    }

    public void startProcessor() {
        Thread t = new Thread(this::processLoop, "astolfo-udp-processor");
        t.setDaemon(true);
        t.start();
    }

    /** Handler dispatch paket terparse + ping eksternal. */
    public interface PacketHandler {
        void onPacket(NetworkMessage message);

        /** Return true jika ping ditangani (packet dianggap consumed). */
        boolean onPing(SocketAddress socketAddress, UUID playerID, id.astolfo.voicechat.voice.common.FriendlyByteBuf remaining);
    }
}
