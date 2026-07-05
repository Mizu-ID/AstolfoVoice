package id.astolfo.voicechat.voice.common;

import java.net.SocketAddress;
import java.util.UUID;

/**
 * ClientConnection — koneksi UDP terverifikasi (state CONNECTED).
 * Disimpan di server.connections setelah handshake selesai.
 */
public final class ClientConnection {

    private final UUID playerUUID;
    private final SocketAddress address;
    private long lastKeepAliveResponse;

    public ClientConnection(UUID playerUUID, SocketAddress address) {
        this.playerUUID = playerUUID;
        this.address = address;
        this.lastKeepAliveResponse = System.currentTimeMillis();
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public SocketAddress getAddress() {
        return address;
    }

    public long getLastKeepAliveResponse() {
        return lastKeepAliveResponse;
    }

    public void setLastKeepAliveResponse(long lastKeepAliveResponse) {
        this.lastKeepAliveResponse = lastKeepAliveResponse;
    }
}
