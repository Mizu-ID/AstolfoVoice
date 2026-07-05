package id.astolfo.voicechat.voice.common;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

/**
 * Secret (AES-128-GCM) untuk enkripsi payload UDP. Byte-exact kompatibel SVC.
 *
 * Wire (per PROTOCOL_REFERENCE §2):
 *   encryptedPayload = IV(12B random) + cipher.doFinal(plaintext)
 *   Key = 16 byte, mode AES/GCM/NoPadding, tag 128-bit.
 *
 * Serialisasi pada FriendlyByteBuf (mis. di dalam AuthenticatePacket):
 *   RAW 16 byte langsung (bukan VarInt length) — sama persis SVC Secret.toBytes(ByteBuf).
 */
public final class Secret {

    public static final int SECRET_SIZE_BYTES = 16;
    public static final int IV_SIZE_BYTES = 12;
    public static final int TAG_LEN_BITS = 128;
    public static final String CIPHER = "AES/GCM/NoPadding";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] secret;
    private final SecretKeySpec keySpec;

    public Secret() {
        this.secret = new byte[SECRET_SIZE_BYTES];
        RANDOM.nextBytes(this.secret);
        this.keySpec = new SecretKeySpec(this.secret, "AES");
    }

    public Secret(byte[] secret) {
        if (secret == null || secret.length != SECRET_SIZE_BYTES) {
            throw new IllegalArgumentException("Secret key must be " + SECRET_SIZE_BYTES + " bytes");
        }
        this.secret = secret.clone();
        this.keySpec = new SecretKeySpec(this.secret, "AES");
    }

    public byte[] getSecret() {
        return secret.clone();
    }

    public SecretKeySpec getKeySpec() {
        return keySpec;
    }

    public boolean equals(Object o) {
        if (!(o instanceof Secret)) {
            return false;
        }
        // Constant-time-ish compare.
        byte[] other = ((Secret) o).secret;
        if (other.length != secret.length) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < secret.length; i++) {
            r |= secret[i] ^ other[i];
        }
        return r == 0;
    }

    @Override
    public int hashCode() {
        int h = 0;
        for (byte b : secret) {
            h = 31 * h + b;
        }
        return h;
    }

    // ---- Serialisasi pada FriendlyByteBuf (raw 16 byte, byte-exact SVC) ----

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBytes(secret);
    }

    public static Secret fromBytes(FriendlyByteBuf buf) {
        byte[] bytes = buf.readBytes(SECRET_SIZE_BYTES);
        return new Secret(bytes);
    }

    // ---- Enkripsi / dekripsi ----

    private static byte[] generateIV() {
        byte[] iv = new byte[IV_SIZE_BYTES];
        RANDOM.nextBytes(iv);
        return iv;
    }

    /** Enkripsi plaintext → IV(12) + ciphertext. */
    public byte[] encrypt(byte[] plaintext) {
        try {
            byte[] iv = generateIV();
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LEN_BITS, iv));
            byte[] enc = cipher.doFinal(plaintext);
            byte[] payload = new byte[IV_SIZE_BYTES + enc.length];
            System.arraycopy(iv, 0, payload, 0, IV_SIZE_BYTES);
            System.arraycopy(enc, 0, payload, IV_SIZE_BYTES, enc.length);
            return payload;
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt UDP payload", e);
        }
    }

    /** Dekripsi IV(12)+ciphertext → plaintext. Return null jika gagal (silent drop). */
    public byte[] decrypt(byte[] payload) {
        if (payload == null || payload.length < IV_SIZE_BYTES + 1) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_SIZE_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_SIZE_BYTES);
            byte[] data = new byte[payload.length - IV_SIZE_BYTES];
            System.arraycopy(payload, IV_SIZE_BYTES, data, 0, data.length);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LEN_BITS, iv));
            return cipher.doFinal(data);
        } catch (Exception e) {
            // Silent drop seperti SVC. Jangan reveal info ke attacker.
            return null;
        }
    }
}
