package id.astolfo.voicechat.voice.common;

/**
 * Akses secret per player UUID. Server mengimplementasikan ini;
 * NetworkMessage memakai untuk dekripsi UDP tanpa coupling ke kelas server penuh.
 */
public interface SecretProvider {

    boolean hasSecret(java.util.UUID playerUUID);

    Secret getSecret(java.util.UUID playerUUID);
}
