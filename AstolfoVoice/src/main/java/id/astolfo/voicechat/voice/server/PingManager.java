package id.astolfo.voicechat.voice.server;

import id.astolfo.voicechat.voice.common.PingPacket;

import java.net.SocketAddress;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PingManager — ping eksternal (server list monitoring) + pengukuran latency.
 * Menerima PingPacket UDP dengan id+timestamp, balas dengan paket serupa.
 */
public final class PingManager {

    private final ConcurrentHashMap<SocketAddress, Long> lastPings = new ConcurrentHashMap<>();

    public boolean onPacket(SocketAddress socketAddress, UUID playerID) {
        // Ping eksternal: paket tanpa secret. Untuk MVP, log + return consumed.
        lastPings.put(socketAddress, System.currentTimeMillis());
        return true;
    }

    public PingPacket createPing() {
        return new PingPacket(UUID.randomUUID(), System.currentTimeMillis());
    }

    public void onPongPacket(PingPacket packet) {
        // Client membalas ping → update latency (di-hook caller).
    }
}
