package id.astolfo.voicechat.net;

import id.astolfo.voicechat.voice.common.FriendlyByteBuf;
import org.bukkit.entity.Player;

/**
 * RequestSecretPacket (incoming, voicechat:request_secret) — client minta secret.
 * Fields: int compatibilityVersion.
 */
public class RequestSecretPacket implements Packet {

    public static final Key REQUEST_SECRET = new Key("request_secret");

    /** Dipasang NetManager saat onEnable. */
    public interface Handler {
        void onRequestSecret(Player player, int compatibilityVersion);
    }

    private static Handler handler;

    public static void setHandler(Handler h) {
        handler = h;
    }

    private int compatibilityVersion;

    public RequestSecretPacket() {
    }

    public RequestSecretPacket(int compatibilityVersion) {
        this.compatibilityVersion = compatibilityVersion;
    }

    public int getCompatibilityVersion() {
        return compatibilityVersion;
    }

    @Override
    public Key getID() {
        return REQUEST_SECRET;
    }

    @Override
    public void onPacket(Player player) {
        if (handler != null) {
            handler.onRequestSecret(player, compatibilityVersion);
        }
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(compatibilityVersion);
    }

    public static RequestSecretPacket fromBytes(FriendlyByteBuf buf) {
        RequestSecretPacket p = new RequestSecretPacket();
        p.compatibilityVersion = buf.readInt();
        return p;
    }
}
