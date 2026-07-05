package id.astolfo.voicechat.net;

import id.astolfo.voicechat.voice.common.FriendlyByteBuf;

import java.util.UUID;

/**
 * JoinedGroupPacket (outgoing, voicechat:joined_group) — hasil join grup.
 * Urutan: boolean hasGroup, [UUID group], boolean wrongPassword.
 */
public class JoinedGroupPacket implements Packet {

    public static final Key JOINED_GROUP = new Key("joined_group");

    private UUID group;
    private boolean wrongPassword;

    public JoinedGroupPacket() {
    }

    public JoinedGroupPacket(UUID group, boolean wrongPassword) {
        this.group = group;
        this.wrongPassword = wrongPassword;
    }

    public UUID getGroup() {
        return group;
    }

    public boolean isWrongPassword() {
        return wrongPassword;
    }

    @Override
    public Key getID() {
        return JOINED_GROUP;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(group != null);
        if (group != null) {
            buf.writeUuid(group);
        }
        buf.writeBoolean(wrongPassword);
    }

    public static JoinedGroupPacket fromBytes(FriendlyByteBuf buf) {
        JoinedGroupPacket p = new JoinedGroupPacket();
        if (buf.readBoolean()) {
            p.group = buf.readUuid();
        }
        p.wrongPassword = buf.readBoolean();
        return p;
    }
}
