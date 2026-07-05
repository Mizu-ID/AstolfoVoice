package id.astolfo.voicechat.voice.common;

import java.util.UUID;

/**
 * GroupSoundPacket (0x3, TTL 10s) — server→client, suara dalam grup.
 * Urutan byte (byte-exact SVC):
 *   UUID channelId, UUID sender, byte[] opus, long seq, byte flags, [String category(16)]
 * flags: bit1 hasCategory (tidak pakai whisper bit).
 */
public class GroupSoundPacket extends SoundPacket {

    public static final int TYPE = 0x3;

    public GroupSoundPacket() {
    }

    public GroupSoundPacket(UUID channelId, UUID sender, byte[] opusData, long sequenceNumber, String category) {
        super(channelId, sender, opusData, sequenceNumber, category);
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
        writeFlagsAndCategory(buf, false, false);
    }

    public static GroupSoundPacket fromBytes(FriendlyByteBuf buf) {
        GroupSoundPacket p = new GroupSoundPacket();
        p.channelId = buf.readUuid();
        p.sender = buf.readUuid();
        p.opusData = buf.readByteArray();
        p.sequenceNumber = buf.readLong();
        p.readFlagsAndCategory(buf);
        return p;
    }
}
