package id.astolfo.voicechat.net;

import id.astolfo.voicechat.voice.common.Codec;
import id.astolfo.voicechat.voice.common.FriendlyByteBuf;
import id.astolfo.voicechat.voice.common.Secret;

import java.util.UUID;

/**
 * SecretPacket (outgoing, voicechat:secret) — byte-exact SVC.
 * Urutan: Secret(16B raw), int serverPort, UUID playerUUID, byte codecOrdinal,
 *         int mtuSize, double voiceChatDistance, int keepAlive, boolean groupsEnabled,
 *         String voiceHost(UTF), boolean allowRecording.
 */
public class SecretPacket implements Packet {

    public static final Key SECRET = new Key("secret");

    private Secret secret;
    private int serverPort;
    private UUID playerUUID;
    private Codec codec;
    private int mtuSize;
    private double voiceChatDistance;
    private int keepAlive;
    private boolean groupsEnabled;
    private String voiceHost;
    private boolean allowRecording;

    public SecretPacket() {
    }

    public SecretPacket(Secret secret, int serverPort, UUID playerUUID, Codec codec, int mtuSize,
                        double voiceChatDistance, int keepAlive, boolean groupsEnabled,
                        String voiceHost, boolean allowRecording) {
        this.secret = secret;
        this.serverPort = serverPort;
        this.playerUUID = playerUUID;
        this.codec = codec;
        this.mtuSize = mtuSize;
        this.voiceChatDistance = voiceChatDistance;
        this.keepAlive = keepAlive;
        this.groupsEnabled = groupsEnabled;
        this.voiceHost = voiceHost == null ? "" : voiceHost;
        this.allowRecording = allowRecording;
    }

    public Secret getSecret() {
        return secret;
    }

    public int getServerPort() {
        return serverPort;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public Codec getCodec() {
        return codec;
    }

    public int getMtuSize() {
        return mtuSize;
    }

    public double getVoiceChatDistance() {
        return voiceChatDistance;
    }

    public int getKeepAlive() {
        return keepAlive;
    }

    public boolean groupsEnabled() {
        return groupsEnabled;
    }

    public String getVoiceHost() {
        return voiceHost;
    }

    public boolean allowRecording() {
        return allowRecording;
    }

    @Override
    public Key getID() {
        return SECRET;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        secret.toBytes(buf);
        buf.writeInt(serverPort);
        buf.writeUuid(playerUUID);
        buf.writeByte(codec.ordinal());
        buf.writeInt(mtuSize);
        buf.writeDouble(voiceChatDistance);
        buf.writeInt(keepAlive);
        buf.writeBoolean(groupsEnabled);
        buf.writeString(voiceHost);
        buf.writeBoolean(allowRecording);
    }

    public static SecretPacket fromBytes(FriendlyByteBuf buf) {
        SecretPacket p = new SecretPacket();
        p.secret = Secret.fromBytes(buf);
        p.serverPort = buf.readInt();
        p.playerUUID = buf.readUuid();
        p.codec = Codec.values()[buf.readByte()];
        p.mtuSize = buf.readInt();
        p.voiceChatDistance = buf.readDouble();
        p.keepAlive = buf.readInt();
        p.groupsEnabled = buf.readBoolean();
        p.voiceHost = buf.readString();
        p.allowRecording = buf.readBoolean();
        return p;
    }
}
