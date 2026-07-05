package id.astolfo.voicechat.audio;

import id.astolfo.voicechat.voice.common.AudioUtils;

/**
 * Resampler - ubah sample rate PCM mono ke 48000 (target Opus SVC) + pitch shift.
 * Kualitas: LOW (linear), MEDIUM/HIGH (lanczos windowed sinc).
 *
 * Pitch shift (v0.2.1) berbasis rate: pitch>1 = sinyal lebih tinggi & cepat,
 * pitch<1 = lebih rendah & lambat. Implementasi: resample dari 48000 ke
 * 48000/pitch, laluanggap hasil sebagai 48000 (efek tarik-tegang waktu + frekuensi).
 */
public final class Resampler {

    private static final int TARGET = AudioUtils.SAMPLE_RATE; // 48000

    private Resampler() {
    }

    public static short[] toTargetRate(short[] input, int inRate, String quality) {
        if (inRate == TARGET) {
            return input;
        }
        if (quality == null) quality = "HIGH";
        return switch (quality.toUpperCase()) {
            case "LOW" -> linear(input, inRate, TARGET);
            default -> lanczos(input, inRate, TARGET, quality.equalsIgnoreCase("HIGH") ? 16 : 8);
        };
    }

    /**
     * Pitch shift berbasis rate. ratio=1.0 -> tidak berubah. ratio=2.0 -> naik 1 oktaf
     * (cepat & tinggi). ratio=0.5 -> turun 1 oktaf (lambat & rendah).
     * Output tetap pada 48000 sample rate.
     */
    public static short[] changePitch(short[] input, double ratio) {
        if (ratio == 1.0 || input.length == 0) return input;
        // Resample dari 48000 ke 48000/ratio, laluanggap sebagai 48000.
        double virtualRate = TARGET / ratio;
        return lanczos(input, (int) Math.round(virtualRate), TARGET, 8);
    }

    /** Linear interpolation (cepat, kualitas rendah). */
    private static short[] linear(short[] input, int inRate, int outRate) {
        double ratio = (double) outRate / inRate;
        int outLen = (int) (input.length * ratio);
        short[] out = new short[outLen];
        for (int i = 0; i < outLen; i++) {
            double srcPos = i / ratio;
            int i0 = (int) srcPos;
            int i1 = Math.min(i0 + 1, input.length - 1);
            double frac = srcPos - i0;
            double v = input[i0] * (1 - frac) + input[i1] * frac;
            out[i] = clamp(v);
        }
        return out;
    }

    /** Lanczos windowed sinc (kualitas tinggi, lebih lambat). */
    private static short[] lanczos(short[] input, int inRate, int outRate, int a) {
        double ratio = (double) inRate / outRate;
        int outLen = (int) (input.length / ratio);
        short[] out = new short[outLen];
        for (int i = 0; i < outLen; i++) {
            double center = i * ratio;
            int start = (int) Math.floor(center) - a + 1;
            int end = (int) Math.floor(center) + a;
            double sum = 0;
            double weightSum = 0;
            for (int j = start; j <= end; j++) {
                if (j < 0 || j >= input.length) continue;
                double dist = center - j;
                if (dist == 0) {
                    sum += input[j];
                    weightSum += 1;
                    continue;
                }
                double w = sinc(dist) * sinc(dist / a);
                sum += input[j] * w;
                weightSum += w;
            }
            out[i] = clamp(weightSum != 0 ? sum / weightSum : 0);
        }
        return out;
    }

    private static double sinc(double x) {
        double pix = Math.PI * x;
        if (pix == 0) return 1;
        return Math.sin(pix) / pix;
    }

    private static short clamp(double v) {
        if (v > 32767) return 32767;
        if (v < -32768) return -32768;
        return (short) v;
    }

    /** Apply linear gain (volume 0.0-1.0+). */
    public static short[] applyGain(short[] input, double gain) {
        if (gain == 1.0) return input;
        short[] out = new short[input.length];
        for (int i = 0; i < input.length; i++) {
            out[i] = clamp(input[i] * gain);
        }
        return out;
    }

    /** Normalize peak ke ~0.95 untuk hindari clipping. */
    public static short[] normalize(short[] input) {
        int max = 0;
        for (short s : input) {
            int a = Math.abs(s);
            if (a > max) max = a;
        }
        if (max == 0 || max >= 31200) return input;
        double target = 31200.0;
        double g = target / max;
        return applyGain(input, g);
    }

    /** Potong / pad ke kelipatan FRAME_SIZE (960). */
    public static short[] alignToFrame(short[] input) {
        int frames = input.length / AudioUtils.FRAME_SIZE;
        int aligned = frames * AudioUtils.FRAME_SIZE;
        if (aligned == input.length) return input;
        short[] out = new short[aligned];
        System.arraycopy(input, 0, out, 0, Math.min(aligned, input.length));
        return out;
    }
}
