package id.astolfo.voicechat.voice.common;

/**
 * Flag byte untuk PlayerSound / GroupSound / LocationSound (PROTOCOL_REFERENCE §3).
 *   bit0 (0b01) = whispering        (hanya PlayerSound memakai ini)
 *   bit1 (0b10) = hasCategory       (jika set, baca String category max 16)
 * Location/Group tidak memakai whisper bit, hanya hasCategory.
 */
public final class SoundFlags {

    public static final int WHISPERING = 0b01;
    public static final int HAS_CATEGORY = 0b10;

    private SoundFlags() {
    }

    public static boolean isWhispering(int flags) {
        return (flags & WHISPERING) != 0;
    }

    public static boolean hasCategory(int flags) {
        return (flags & HAS_CATEGORY) != 0;
    }

    public static int set(int flags, int mask, boolean value) {
        if (value) {
            return flags | mask;
        }
        return flags & ~mask;
    }
}
