package id.astolfo.voicechat.voice.common;

/**
 * KeepAlivePacket (0x8) — bidirectional, payload kosong.
 * Server kirim tiap keep_alive ms; client balas untuk update lastKeepAliveResponse.
 */
public class KeepAlivePacket implements Packet {

    public static final int TYPE = 0x8;

    public KeepAlivePacket() {
    }

    @Override
    public int getTypeId() {
        return TYPE;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        // empty
    }

    public static KeepAlivePacket fromBytes(FriendlyByteBuf buf) {
        return new KeepAlivePacket();
    }
}
