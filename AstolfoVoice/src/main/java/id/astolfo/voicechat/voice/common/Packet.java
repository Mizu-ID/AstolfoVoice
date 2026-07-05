package id.astolfo.voicechat.voice.common;

/**
 * Kontrak paket UDP yang teregistrasi di NetworkMessage.
 * Setiap implementasi: baca/tulis typeID + fields sesuai PROTOCOL_REFERENCE §3.
 */
public interface Packet {

    void toBytes(FriendlyByteBuf buf);

    int getTypeId();

    /** TTL paket (ms). Paket kedaluwarsa di-drop server-side. */
    default long getTTL() {
        return 10_000L;
    }
}
