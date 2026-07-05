package id.astolfo.voicechat.net;

import id.astolfo.voicechat.voice.common.FriendlyByteBuf;
import id.astolfo.voicechat.voice.common.PlayerState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * PlayerStatesPacket (outgoing, voicechat:states) — snapshot penuh state player.
 * Urutan: int count, lalu count × PlayerState.
 */
public class PlayerStatesPacket implements Packet {

    public static final Key PLAYER_STATES = new Key("states");

    private Collection<PlayerState> playerStates;

    public PlayerStatesPacket() {
    }

    public PlayerStatesPacket(Collection<PlayerState> playerStates) {
        this.playerStates = playerStates;
    }

    public Collection<PlayerState> getPlayerStates() {
        return playerStates;
    }

    @Override
    public Key getID() {
        return PLAYER_STATES;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(playerStates.size());
        for (PlayerState state : playerStates) {
            state.toBytes(buf);
        }
    }

    public static PlayerStatesPacket fromBytes(FriendlyByteBuf buf) {
        PlayerStatesPacket p = new PlayerStatesPacket();
        int count = buf.readInt();
        List<PlayerState> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(PlayerState.fromBytes(buf));
        }
        p.playerStates = list;
        return p;
    }
}
