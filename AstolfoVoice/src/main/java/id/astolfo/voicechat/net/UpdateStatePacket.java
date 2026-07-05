package id.astolfo.voicechat.net;

import id.astolfo.voicechat.voice.common.FriendlyByteBuf;
import org.bukkit.entity.Player;

/**
 * UpdateStatePacket (incoming, voicechat:update_state) — client set disabled.
 * Fields: boolean disabled.
 */
public class UpdateStatePacket implements Packet {

    public static final Key UPDATE_STATE = new Key("update_state");

    public interface Handler {
        void onUpdateState(Player player, boolean disabled);
    }

    private static Handler handler;

    public static void setHandler(Handler h) {
        handler = h;
    }

    private boolean disabled;

    public UpdateStatePacket() {
    }

    public UpdateStatePacket(boolean disabled) {
        this.disabled = disabled;
    }

    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public Key getID() {
        return UPDATE_STATE;
    }

    @Override
    public void onPacket(Player player) {
        if (handler != null) {
            handler.onUpdateState(player, disabled);
        }
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(disabled);
    }

    public static UpdateStatePacket fromBytes(FriendlyByteBuf buf) {
        UpdateStatePacket p = new UpdateStatePacket();
        p.disabled = buf.readBoolean();
        return p;
    }
}
