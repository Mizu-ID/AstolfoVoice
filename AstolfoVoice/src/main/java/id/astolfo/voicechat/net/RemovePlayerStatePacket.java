package id.astolfo.voicechat.net;

import id.astolfo.voicechat.voice.common.FriendlyByteBuf;

import java.util.UUID;

/**
 * RemovePlayerStatePacket (outgoing, voicechat:remove_state) — hapus state player.
 */
public class RemovePlayerStatePacket implements Packet {

    public static final Key REMOVE_PLAYER_STATE = new Key("remove_state");

    private UUID id;

    public RemovePlayerStatePacket() {
    }

    public RemovePlayerStatePacket(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public Key getID() {
        return REMOVE_PLAYER_STATE;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUuid(id);
    }

    public static RemovePlayerStatePacket fromBytes(FriendlyByteBuf buf) {
        RemovePlayerStatePacket p = new RemovePlayerStatePacket();
        p.id = buf.readUuid();
        return p;
    }
}
