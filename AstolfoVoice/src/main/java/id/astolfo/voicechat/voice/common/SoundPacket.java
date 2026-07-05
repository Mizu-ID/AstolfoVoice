package id.astolfo.voicechat.voice.common;

import java.util.UUID;

/**
 * Basis umum untuk paket audio server→client. Tidak menangani serialisasi
 * (tiap subclass menulis field sesuai urutan byte-exact SVC — lihat PROTOCOL_REFERENCE §3),
 * karena urutan field berbeda per tipe (PlayerSound: ...seq, distance, flags;
 * LocationSound: ...location, data, seq, distance, flags; GroupSound: ...seq, flags).
 *
 * Konvensi SVC: sequenceNumber < 0 menandakan paket dari audio channel (server playback).
 */
public abstract class SoundPacket implements Packet {

    protected UUID channelId;
    protected UUID sender;
    protected byte[] opusData;
    protected long sequenceNumber;
    protected String category;

    protected SoundPacket() {
    }

    protected SoundPacket(UUID channelId, UUID sender, byte[] opusData, long sequenceNumber, String category) {
        this.channelId = channelId;
        this.sender = sender;
        this.opusData = opusData;
        this.sequenceNumber = sequenceNumber;
        this.category = category;
    }

    public UUID getChannelId() {
        return channelId;
    }

    public UUID getSender() {
        return sender;
    }

    public byte[] getOpusData() {
        return opusData;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public String getCategory() {
        return category;
    }

    public boolean isFromClientAudioChannel() {
        return sequenceNumber < 0;
    }

    /** Tulis byte flags + [category] di akhir paket. */
    protected void writeFlagsAndCategory(FriendlyByteBuf buf, boolean whispering, boolean hasWhisperBit) {
        int flags = 0;
        if (hasWhisperBit && whispering) {
            flags |= SoundFlags.WHISPERING;
        }
        if (category != null) {
            flags |= SoundFlags.HAS_CATEGORY;
        }
        buf.writeByte(flags);
        if (category != null) {
            buf.writeString(category == null ? "" : category, 16);
        }
    }

    /** Baca flags byte; set category jika ada. Return flags mentah. */
    protected int readFlagsAndCategory(FriendlyByteBuf buf) {
        int flags = buf.readUnsignedByte();
        if (SoundFlags.hasCategory(flags)) {
            category = buf.readString(16);
        } else {
            category = null;
        }
        return flags;
    }
}
