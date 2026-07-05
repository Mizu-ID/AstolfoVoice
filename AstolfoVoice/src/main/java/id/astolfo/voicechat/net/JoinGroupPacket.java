package id.astolfo.voicechat.net;

import id.astolfo.voicechat.voice.common.FriendlyByteBuf;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * JoinGroupPacket (incoming, voicechat:set_group — nama channel SVC "set_group", BUKAN join_group).
 * Urutan: UUID group, boolean hasPassword, [String password(512)].
 */
public class JoinGroupPacket implements Packet {

    public static final Key SET_GROUP = new Key("set_group");

    public interface Handler {
        void onJoinGroup(Player player, UUID group, String password);
    }

    private static Handler handler;

    public static void setHandler(Handler h) {
        handler = h;
    }

    private UUID group;
    private String password;

    public JoinGroupPacket() {
    }

    public JoinGroupPacket(UUID group, String password) {
        this.group = group;
        this.password = password;
    }

    public UUID getGroup() {
        return group;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public Key getID() {
        return SET_GROUP;
    }

    @Override
    public void onPacket(Player player) {
        if (handler != null) {
            handler.onJoinGroup(player, group, password);
        }
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUuid(group);
        buf.writeBoolean(password != null);
        if (password != null) {
            buf.writeString(password, 512);
        }
    }

    public static JoinGroupPacket fromBytes(FriendlyByteBuf buf) {
        JoinGroupPacket p = new JoinGroupPacket();
        p.group = buf.readUuid();
        if (buf.readBoolean()) {
            p.password = buf.readString(512);
        }
        return p;
    }
}
