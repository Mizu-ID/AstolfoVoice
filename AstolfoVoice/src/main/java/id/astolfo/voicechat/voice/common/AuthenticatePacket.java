package id.astolfo.voicechat.voice.common;

import java.util.UUID;

/**
 * AuthenticatePacket (0x5, TTL 10s) — client→server via UDP.
 * Fields: UUID playerUUID, Secret(16B raw).
 */
public class AuthenticatePacket implements Packet {

    public static final int TYPE = 0x5;

    private UUID playerUUID;
    private Secret secret;

    public AuthenticatePacket() {
    }

    public AuthenticatePacket(UUID playerUUID, Secret secret) {
        this.playerUUID = playerUUID;
        this.secret = secret;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public Secret getSecret() {
        return secret;
    }

    @Override
    public int getTypeId() {
        return TYPE;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUuid(playerUUID);
        secret.toBytes(buf);
    }

    public static AuthenticatePacket fromBytes(FriendlyByteBuf buf) {
        AuthenticatePacket p = new AuthenticatePacket();
        p.playerUUID = buf.readUuid();
        p.secret = Secret.fromBytes(buf);
        return p;
    }
}
