package id.astolfo.voicechat.voice.common;

import java.util.UUID;

/**
 * PlayerSoundPacket (0x2, TTL 10s) — server→client, suara player di lokasi sendiri.
 * Urutan byte (byte-exact SVC):
 *   UUID channelId, UUID sender, byte[] opus, long seq, float distance, byte flags, [String category(16)]
 * flags: bit0 whispering, bit1 hasCategory.
 */
public class PlayerSoundPacket extends SoundPacket {

    public static final int TYPE = 0x2;

    private float distance;
    private boolean whispering;

    public PlayerSoundPacket() {
    }

    public PlayerSoundPacket(UUID channelId, UUID sender, byte[] opusData, long sequenceNumber,
                             boolean whispering, float distance, String category) {
        super(channelId, sender, opusData, sequenceNumber, category);
        this.whispering = whispering;
        this.distance = distance;
    }

    public float getDistance() {
        return distance;
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
        buf.writeUuid(channelId);
        buf.writeUuid(sender);
        buf.writeByteArray(opusData);
        buf.writeLong(sequenceNumber);
        buf.writeFloat(distance);
        writeFlagsAndCategory(buf, whispering, true);
    }

    public static PlayerSoundPacket fromBytes(FriendlyByteBuf buf) {
        PlayerSoundPacket p = new PlayerSoundPacket();
        p.channelId = buf.readUuid();
        p.sender = buf.readUuid();
        p.opusData = buf.readByteArray();
        p.sequenceNumber = buf.readLong();
        p.distance = buf.readFloat();
        int flags = p.readFlagsAndCategory(buf);
        p.whispering = SoundFlags.isWhispering(flags);
        return p;
    }
}
