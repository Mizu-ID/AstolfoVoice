package id.astolfo.voicechat.net;

import id.astolfo.voicechat.voice.common.FriendlyByteBuf;
import id.astolfo.voicechat.voice.common.PlayerState;

/**
 * PlayerStatePacket (outgoing, voicechat:state) — update state satu player.
 */
public class PlayerStatePacket implements Packet {

    public static final Key PLAYER_STATE = new Key("state");

    private PlayerState playerState;

    public PlayerStatePacket() {
    }

    public PlayerStatePacket(PlayerState playerState) {
        this.playerState = playerState;
    }

    public PlayerState getPlayerState() {
        return playerState;
    }

    @Override
    public Key getID() {
        return PLAYER_STATE;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        playerState.toBytes(buf);
    }

    public static PlayerStatePacket fromBytes(FriendlyByteBuf buf) {
        PlayerStatePacket p = new PlayerStatePacket();
        p.playerState = PlayerState.fromBytes(buf);
        return p;
    }
}
