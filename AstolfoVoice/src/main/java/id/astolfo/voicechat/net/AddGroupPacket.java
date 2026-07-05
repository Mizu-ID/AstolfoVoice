package id.astolfo.voicechat.net;

import id.astolfo.voicechat.voice.common.ClientGroup;
import id.astolfo.voicechat.voice.common.FriendlyByteBuf;

/**
 * AddGroupPacket (outgoing, voicechat:add_group) — tambah grup ke daftar client.
 */
public class AddGroupPacket implements Packet {

    public static final Key ADD_GROUP = new Key("add_group");

    private ClientGroup group;

    public AddGroupPacket() {
    }

    public AddGroupPacket(ClientGroup group) {
        this.group = group;
    }

    public ClientGroup getGroup() {
        return group;
    }

    @Override
    public Key getID() {
        return ADD_GROUP;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        group.toBytes(buf);
    }

    public static AddGroupPacket fromBytes(FriendlyByteBuf buf) {
        AddGroupPacket p = new AddGroupPacket();
        p.group = ClientGroup.fromBytes(buf);
        return p;
    }
}
