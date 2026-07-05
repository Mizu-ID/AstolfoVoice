package id.astolfo.voicechat.voice.common;

/**
 * ConnectionCheckPacket (0x9) — client→server, payload kosong.
 * Server memindahkan koneksi dari unChecked → checked, lalu balas ConnectionCheckAckPacket.
 */
public class ConnectionCheckPacket implements Packet {

    public static final int TYPE = 0x9;

    public ConnectionCheckPacket() {
    }

    @Override
    public int getTypeId() {
        return TYPE;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        // empty
    }

    public static ConnectionCheckPacket fromBytes(FriendlyByteBuf buf) {
        return new ConnectionCheckPacket();
    }
}
