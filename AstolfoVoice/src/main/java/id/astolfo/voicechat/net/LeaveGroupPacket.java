package id.astolfo.voicechat.net;

import id.astolfo.voicechat.voice.common.FriendlyByteBuf;
import org.bukkit.entity.Player;

/**
 * LeaveGroupPacket (incoming, voicechat:leave_group) — payload kosong.
 */
public class LeaveGroupPacket implements Packet {

    public static final Key LEAVE_GROUP = new Key("leave_group");

    public interface Handler {
        void onLeaveGroup(Player player);
    }

    private static Handler handler;

    public static void setHandler(Handler h) {
        handler = h;
    }

    public LeaveGroupPacket() {
    }

    @Override
    public Key getID() {
        return LEAVE_GROUP;
    }

    @Override
    public void onPacket(Player player) {
        if (handler != null) {
            handler.onLeaveGroup(player);
        }
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        // empty
    }

    public static LeaveGroupPacket fromBytes(FriendlyByteBuf buf) {
        return new LeaveGroupPacket();
    }
}
