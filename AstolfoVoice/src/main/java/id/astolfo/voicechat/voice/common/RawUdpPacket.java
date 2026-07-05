package id.astolfo.voicechat.voice.common;

import java.net.SocketAddress;

/**
 * Paket UDP mentah yang diterima reader thread. Wrapper ringan untuk
 * menyerahkan data + address + timestamp ke processor.
 */
public final class RawUdpPacket {

    private final byte[] data;
    private final SocketAddress socketAddress;
    private final long timestamp;

    public RawUdpPacket(byte[] data, SocketAddress socketAddress, long timestamp) {
        this.data = data;
        this.socketAddress = socketAddress;
        this.timestamp = timestamp;
    }

    public byte[] getData() {
        return data;
    }

    public SocketAddress getSocketAddress() {
        return socketAddress;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
