package id.astolfo.voicechat.voice.common;

/**
 * MicPacket (0x1, TTL 500ms) — dari client ke server.
 * Fields: byte[] opus (VarInt+len), long seq, boolean whispering.
 */
public class MicPacket implements Packet {

    public static final int TYPE = 0x1;

    private byte[] opusData;
    private long sequenceNumber;
    private boolean whispering;

    public MicPacket() {
    }

    public MicPacket(byte[] opusData, long sequenceNumber, boolean whispering) {
        this.opusData = opusData;
        this.sequenceNumber = sequenceNumber;
        this.whispering = whispering;
    }

    public byte[] getOpusData() {
        return opusData;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public boolean isWhispering() {
        return whispering;
    }

    @Override
    public int getTypeId() {
        return TYPE;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeByteArray(opusData);
        buf.writeLong(sequenceNumber);
        buf.writeBoolean(whispering);
    }

    public static MicPacket fromBytes(FriendlyByteBuf buf) {
        MicPacket p = new MicPacket();
        p.opusData = buf.readByteArray();
        p.sequenceNumber = buf.readLong();
        p.whispering = buf.readBoolean();
        return p;
    }
}
