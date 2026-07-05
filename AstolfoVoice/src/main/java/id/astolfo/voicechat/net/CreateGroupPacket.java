package id.astolfo.voicechat.net;

import id.astolfo.voicechat.voice.common.FriendlyByteBuf;
import id.astolfo.voicechat.voice.common.GroupType;
import org.bukkit.entity.Player;

/**
 * CreateGroupPacket (incoming, voicechat:create_group) — client buat grup.
 * Urutan: String name(24), boolean hasPassword, [String password(24)], short typeOrdinal.
 */
public class CreateGroupPacket implements Packet {

    public static final Key CREATE_GROUP = new Key("create_group");

    public interface Handler {
        void onCreateGroup(Player player, String name, String password, GroupType type);
    }

    private static Handler handler;

    public static void setHandler(Handler h) {
        handler = h;
    }

    private String name;
    private String password;
    private GroupType type;

    public CreateGroupPacket() {
    }

    public CreateGroupPacket(String name, String password, GroupType type) {
        this.name = name;
        this.password = password;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getPassword() {
        return password;
    }

    public GroupType getType() {
        return type;
    }

    @Override
    public Key getID() {
        return CREATE_GROUP;
    }

    @Override
    public void onPacket(Player player) {
        if (handler != null) {
            handler.onCreateGroup(player, name, password, type);
        }
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeString(name, NetConstants.MAX_GROUP_NAME_LENGTH);
        buf.writeBoolean(password != null);
        if (password != null) {
            buf.writeString(password, NetConstants.MAX_GROUP_NAME_LENGTH);
        }
        buf.writeShort(type.toInt());
    }

    public static CreateGroupPacket fromBytes(FriendlyByteBuf buf) {
        CreateGroupPacket p = new CreateGroupPacket();
        p.name = buf.readString(NetConstants.MAX_GROUP_NAME_LENGTH);
        p.password = null;
        if (buf.readBoolean()) {
            p.password = buf.readString(NetConstants.MAX_GROUP_NAME_LENGTH);
        }
        p.type = GroupType.fromInt(buf.readShort());
        return p;
    }
}
