package id.astolfo.voicechat.voice.common;

/**
 * Konstanta & util konversi audio. Byte-exact kompatibel SVC.
 * SAMPLE_RATE=48000, FRAME_SIZE=960 (20ms), MAX_OPUS_PAYLOAD_SIZE=1275.
 */
public final class AudioUtils {

    public static final int SAMPLE_RATE = 48000;
    public static final int FRAME_SIZE = (SAMPLE_RATE / 1000) * 20; // 960
    public static final int MAX_OPUS_PAYLOAD_SIZE = 1275;

    private AudioUtils() {
    }

    /** short[] (little-endian) → byte[]. */
    public static byte[] shortsToBytes(short[] shorts) {
        byte[] bytes = new byte[shorts.length * 2];
        for (int i = 0; i < shorts.length; i++) {
            bytes[i * 2] = (byte) (shorts[i] & 0xFF);
            bytes[i * 2 + 1] = (byte) ((shorts[i] >> 8) & 0xFF);
        }
        return bytes;
    }

    /** byte[] (little-endian) → short[]. */
    public static short[] bytesToShorts(byte[] bytes) {
        short[] shorts = new short[bytes.length / 2];
        for (int i = 0; i < shorts.length; i++) {
            int lo = bytes[i * 2] & 0xFF;
            int hi = bytes[i * 2 + 1] & 0xFF;
            shorts[i] = (short) ((hi << 8) | lo);
        }
        return shorts;
    }

    /** short[] → float[] normalized [-1,1]. */
    public static float[] shortsToFloatsNormalized(short[] shorts) {
        float[] floats = new float[shorts.length];
        for (int i = 0; i < shorts.length; i++) {
            floats[i] = shorts[i] / 32768F;
        }
        return floats;
    }

    /** float[] normalized [-1,1] → short[] (clamped). */
    public static short[] floatsToShortsNormalized(float[] floats) {
        short[] shorts = new short[floats.length];
        for (int i = 0; i < floats.length; i++) {
            float v = floats[i] * 32768F;
            if (v > 32767F) v = 32767F;
            if (v < -32768F) v = -32768F;
            shorts[i] = (short) v;
        }
        return shorts;
    }

    /** Audio level (peak dB) dari short[]. -128 = silent. */
    public static float getHighestAudioLevel(short[] samples) {
        int max = 0;
        for (short s : samples) {
            int a = Math.abs(s);
            if (a > max) max = a;
        }
        if (max == 0) {
            return -128F;
        }
        return (float) (20D * Math.log10(max / 32768D));
    }

    /** Audio level (RMS dB) dari short[]. Lebih stabil dari peak. */
    public static float getRmsAudioLevel(short[] samples) {
        if (samples.length == 0) {
            return -128F;
        }
        double sum = 0D;
        for (short s : samples) {
            double v = s / 32768D;
            sum += v * v;
        }
        double rms = Math.sqrt(sum / samples.length);
        if (rms <= 0D) {
            return -128F;
        }
        return (float) (20D * Math.log10(rms));
    }

    public static double dbToLinear(double db) {
        return Math.pow(10D, db / 20D);
    }

    public static double linearToDb(double linear) {
        return 20D * Math.log10(linear);
    }

    /** Mix dua sinyal dengan clip. */
    public static short[] combineAudio(short[] a, short[] b) {
        int len = Math.max(a.length, b.length);
        short[] out = new short[len];
        for (int i = 0; i < len; i++) {
            int va = i < a.length ? a[i] : 0;
            int vb = i < b.length ? b[i] : 0;
            int v = va + vb;
            if (v > 32767) v = 32767;
            if (v < -32768) v = -32768;
            out[i] = (short) v;
        }
        return out;
    }
}
