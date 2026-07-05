package id.astolfo.voicechat.voice.common;

import java.util.UUID;

/**
 * LocationSoundPacket (0x4, TTL 10s) — server→client, suara di posisi dunia tertentu.
 * Urutan byte (byte-exact SVC):
 *   UUID channelId, UUID sender,
 *   double x, double y, double z,
 *   byte[] opus, long seq, float distance,
 *   byte flags, [String category(16)]
 */
public class LocationSoundPacket extends SoundPacket {

    public static final int TYPE = 0x4;

    private double x;
    private double y;
    private double z;
    private float distance;

    public LocationSoundPacket() {
    }

    public LocationSoundPacket(UUID channelId, UUID sender, double x, double y, double z,
                               byte[] opusData, long sequenceNumber, float distance, String category) {
        super(channelId, sender, opusData, sequenceNumber, category);
        this.x = x;
        this.y = y;
        this.z = z;
        this.distance = distance;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getDistance() {
        return distance;
    }

    @Override
    public int getTypeId() {
        return TYPE;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUuid(channelId);
        buf.writeUuid(sender);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeByteArray(opusData);
        buf.writeLong(sequenceNumber);
        buf.writeFloat(distance);
        writeFlagsAndCategory(buf, false, false);
    }

    public static LocationSoundPacket fromBytes(FriendlyByteBuf buf) {
        LocationSoundPacket p = new LocationSoundPacket();
        p.channelId = buf.readUuid();
        p.sender = buf.readUuid();
        p.x = buf.readDouble();
        p.y = buf.readDouble();
        p.z = buf.readDouble();
        p.opusData = buf.readByteArray();
        p.sequenceNumber = buf.readLong();
        p.distance = buf.readFloat();
        p.readFlagsAndCategory(buf);
        return p;
    }
}
