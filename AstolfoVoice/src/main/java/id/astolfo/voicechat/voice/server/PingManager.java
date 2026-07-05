package id.astolfo.voicechat.voice.server;

import id.astolfo.voicechat.voice.common.PingPacket;

import java.net.SocketAddress;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PingManager — ping eksternal (server list monitoring) + pengukuran latency
 * round-trip client. Menerima PingPacket UDP dengan id+timestamp.
 *
 * - onPacket: ping eksternal (paket tanpa secret, dari server list pinger).
 *   Return consumed=true biar reader tidak coba decrypt.
 * - createPing: ping yang dikirim server untuk probe latency client.
 * - onPongPacket: client membalas ping yang dikirim server → hitung RTT,
 *   simpan latency terakhir per player (dipakai untuk telemetry/diagnostik).
 */
public final class PingManager {

    private final ConcurrentHashMap<SocketAddress, Long> lastPings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> latencyMillis = new ConcurrentHashMap<>();
    // ping id → timestamp kirim, untuk match balasan client.
    private final ConcurrentHashMap<UUID, Long> pendingPings = new ConcurrentHashMap<>();

    public boolean onPacket(SocketAddress socketAddress, UUID playerID) {
        // Ping eksternal: paket tanpa secret. Catat untuk rate/monitoring, return consumed.
        lastPings.put(socketAddress, System.currentTimeMillis());
        return true;
    }

    public PingPacket createPing() {
        UUID id = UUID.randomUUID();
        long now = System.currentTimeMillis();
        pendingPings.put(id, now);
        return new PingPacket(id, now);
    }

    public void onPongPacket(PingPacket packet) {
        // Client membalas ping → hitung RTT dari timestamp kirim yang tersimpan.
        Long sentAt = pendingPings.remove(packet.getId());
        if (sentAt != null) {
            long rtt = System.currentTimeMillis() - sentAt;
            latencyMillis.put(packet.getId(), rtt);
        }
    }

    /** Latency RTT terakhir untuk player (ms), -1 bila belum ada. */
    public long getLatency(UUID playerId) {
        return latencyMillis.getOrDefault(playerId, -1L);
    }

    public void clearFor(UUID playerId) {
        latencyMillis.remove(playerId);
    }
}
