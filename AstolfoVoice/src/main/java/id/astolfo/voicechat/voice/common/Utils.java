package id.astolfo.voicechat.voice.common;

/**
 * Konstanta & util umum (PROTOCOL_REFERENCE §2). Byte-exact kompatibel SVC.
 */
public final class Utils {

    public static final int MAX_VOICE_CHAT_PACKET_SIZE = 2048;

    private Utils() {
    }

    public static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
