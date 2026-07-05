package id.astolfo.voicechat.voice.common;

import java.util.UUID;

/**
 * PingPacket (0x7) — bidirectional, untuk pengukuran latency & ping eksternal.
 * Fields: UUID id, long timestamp.
 */
public class PingPacket implements Packet {

    public static final int TYPE = 0x7;

    private UUID id;
    private long timestamp;

    public PingPacket() {
    }

    public PingPacket(UUID id, long timestamp) {
        this.id = id;
        this.timestamp = timestamp;
    }

    public UUID getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public int getTypeId() {
        return TYPE;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUuid(id);
        buf.writeLong(timestamp);
    }

    public static PingPacket fromBytes(FriendlyByteBuf buf) {
        PingPacket p = new PingPacket();
        p.id = buf.readUuid();
        p.timestamp = buf.readLong();
        return p;
    }
}
