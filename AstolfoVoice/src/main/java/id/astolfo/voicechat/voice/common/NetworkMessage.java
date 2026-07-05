package id.astolfo.voicechat.voice.common;

import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * NetworkMessage — envelope & dispatch paket UDP. Byte-exact kompatibel SVC.
 *
 * Envelope (PROTOCOL_REFERENCE §2):
 *   [0xFF magic][UUID playerID(16B, MSB+LSB)][byte[] encryptedPayload(VarInt+len)]
 * encryptedPayload = Secret.encrypt( [typeID byte][packet.toBytes] ).
 *
 * Read server-side: cek magic → baca playerID → cari secret (via SecretProvider) →
 *   decrypt encryptedPayload → baca typeID → dispatch ke registry → packet.fromBytes.
 */
public final class NetworkMessage {

    public static final byte MAGIC_BYTE = (byte) 0b11111111;

    private final long timestamp;
    private Packet packet;
    private SocketAddress address;

    private NetworkMessage(long timestamp) {
        this.timestamp = timestamp;
    }

    public NetworkMessage(Packet packet) {
        this(System.currentTimeMillis());
        this.packet = packet;
    }

    public Packet getPacket() {
        return packet;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public SocketAddress getAddress() {
        return address;
    }

    // ---- Packet registry (byte-exact SVC) ----

    @FunctionalInterface
    public interface PacketReader {
        Packet read(FriendlyByteBuf buf);
    }

    private static final Map<Byte, PacketReader> REGISTRY = new HashMap<>();

    static {
        REGISTRY.put((byte) 0x1, MicPacket::fromBytes);
        REGISTRY.put((byte) 0x2, PlayerSoundPacket::fromBytes);
        REGISTRY.put((byte) 0x3, GroupSoundPacket::fromBytes);
        REGISTRY.put((byte) 0x4, LocationSoundPacket::fromBytes);
        REGISTRY.put((byte) 0x5, AuthenticatePacket::fromBytes);
        REGISTRY.put((byte) 0x6, AuthenticateAckPacket::fromBytes);
        REGISTRY.put((byte) 0x7, PingPacket::fromBytes);
        REGISTRY.put((byte) 0x8, KeepAlivePacket::fromBytes);
        REGISTRY.put((byte) 0x9, ConnectionCheckPacket::fromBytes);
        REGISTRY.put((byte) 0xA, ConnectionCheckAckPacket::fromBytes);
    }

    private static byte getPacketType(Packet packet) {
        byte type = (byte) packet.getTypeId();
        if (REGISTRY.containsKey(type)) {
            return type;
        }
        return -1;
    }

    // ---- Read ----

    /**
     * Parse paket UDP yang diterima server-side. Return null bila invalid/drop.
     * Hook ping eksternal via pingHandler (boleh null).
     */
    public static NetworkMessage readPacketServer(RawUdpPacket raw, SecretProvider secrets,
                                                  PingHandler pingHandler) {
        try {
            FriendlyByteBuf b = new FriendlyByteBuf(raw.getData());
            if (b.readByte() != MAGIC_BYTE) {
                return null;
            }
            UUID playerID = b.readUuid();
            if (!secrets.hasSecret(playerID)) {
                if (pingHandler != null && pingHandler.onPacket(raw.getSocketAddress(), playerID, b)) {
                    return null;
                }
                return null;
            }
            byte[] encrypted = b.readByteArray();
            return readFromBytes(raw.getSocketAddress(), secrets.getSecret(playerID), encrypted, raw.getTimestamp());
        } catch (RuntimeException e) {
            // IndexOutOfBounds / decode error → silent drop
            return null;
        }
    }

    public static NetworkMessage readFromBytes(SocketAddress address, Secret secret, byte[] encrypted, long timestamp) {
        byte[] decrypt = secret.decrypt(encrypted);
        if (decrypt == null) {
            return null;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(decrypt);
        byte type = buf.readByte();
        PacketReader reader = REGISTRY.get(type);
        if (reader == null) {
            return null;
        }
        Packet packet = reader.read(buf);
        NetworkMessage msg = new NetworkMessage(timestamp);
        msg.address = address;
        msg.packet = packet;
        return msg;
    }

    // ---- Write ----

    /** Enkripsi payload inner: [typeID][toBytes] lalu Secret.encrypt. */
    public byte[] write(Secret secret) {
        FriendlyByteBuf buf = new FriendlyByteBuf();
        byte type = getPacketType(packet);
        if (type < 0) {
            throw new IllegalArgumentException("Packet type not found: " + packet.getClass());
        }
        buf.writeByte(type);
        packet.toBytes(buf);
        return secret.encrypt(buf.toByteArray());
    }

    /** Envelope lengkap server→client: [0xFF][UUID player][byte[] encryptedPayload]. */
    public byte[] writeServer(Secret secret, UUID playerUUID) {
        byte[] encrypted = write(secret);
        FriendlyByteBuf buf = new FriendlyByteBuf();
        buf.writeByte(MAGIC_BYTE);
        buf.writeUuid(playerUUID);
        buf.writeByteArray(encrypted);
        return buf.toByteArray();
    }

    /** Hook untuk ping eksternal (server list / monitoring). */
    public interface PingHandler {
        boolean onPacket(SocketAddress socketAddress, UUID playerID, FriendlyByteBuf remaining);
    }
}
