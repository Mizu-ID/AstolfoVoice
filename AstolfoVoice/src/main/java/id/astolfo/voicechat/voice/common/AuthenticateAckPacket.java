package id.astolfo.voicechat.voice.common;

/**
 * AuthenticateAckPacket (0x6) — server→client, payload kosong.
 */
public class AuthenticateAckPacket implements Packet {

    public static final int TYPE = 0x6;

    public AuthenticateAckPacket() {
    }

    @Override
    public int getTypeId() {
        return TYPE;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        // empty
    }

    public static AuthenticateAckPacket fromBytes(FriendlyByteBuf buf) {
        return new AuthenticateAckPacket();
    }
}
