package id.astolfo.voicechat.net;

import id.astolfo.voicechat.voice.common.FriendlyByteBuf;

import java.util.UUID;

/**
 * RemoveGroupPacket (outgoing, voicechat:remove_group).
 */
public class RemoveGroupPacket implements Packet {

    public static final Key REMOVE_GROUP = new Key("remove_group");

    private UUID groupId;

    public RemoveGroupPacket() {
    }

    public RemoveGroupPacket(UUID groupId) {
        this.groupId = groupId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    @Override
    public Key getID() {
        return REMOVE_GROUP;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUuid(groupId);
    }

    public static RemoveGroupPacket fromBytes(FriendlyByteBuf buf) {
        RemoveGroupPacket p = new RemoveGroupPacket();
        p.groupId = buf.readUuid();
        return p;
    }
}
