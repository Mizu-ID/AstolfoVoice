package id.astolfo.voicechat.voice.common;

/**
 * ConnectionCheckAckPacket (0xA) — server→client, payload kosong.
 * Dikirim sebagai balasan ConnectionCheckPacket (koneksi terverifikasi penuh).
 */
public class ConnectionCheckAckPacket implements Packet {

    public static final int TYPE = 0xA;

    public ConnectionCheckAckPacket() {
    }

    @Override
    public int getTypeId() {
        return TYPE;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        // empty
    }

    public static ConnectionCheckAckPacket fromBytes(FriendlyByteBuf buf) {
        return new ConnectionCheckAckPacket();
    }
}
