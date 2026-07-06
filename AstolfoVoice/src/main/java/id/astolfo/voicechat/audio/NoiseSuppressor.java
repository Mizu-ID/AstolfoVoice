package id.astolfo.voicechat.audio;

/**
 * NoiseSuppressor - noise cancellation spektral pure-Java (engine SPECTRAL).
 *
 * Metode: STFT streaming (FFT 512, hop 256, sqrt-Hann analysis+synthesis, 50%
 * overlap-add = rekonstruksi sempurna) + spectral subtraction adaptif:
 *  - Noise floor per-bin di-track dengan "minimum statistics lite": turun instan
 *    ke magnitude terkecil (celah antar kata), naik pelan (~0.6%/hop) mengikuti
 *    perubahan lantai noise. Tidak butuh kalibrasi/frame training.
 *  - Over-subtraction: alpha = 1.2 + 1.3*strength (strength dari config
 *    noise_cancellation.strength, 0..1).
 *  - Spectral floor: gain minimum 0.02..0.22 tergantung strength (anti
 *    "musical noise" total-silence artifact).
 *  - Gain smoothing temporal per-bin (anti flutter).
 *
 * Bukan RNNoise (neural) - itu roadmap native. Ini kualitas kelas Speex/WebRTC
 * classic, cukup untuk hum kipas, hiss, dan noise stasioner.
 *
 * Latency tambahan: 256 sample (~5.3 ms) + pad awal satu kali 192 sample.
 * TIDAK thread-safe: pakai satu instance per speaker (per sender UUID).
 */
public final class NoiseSuppressor {

    private static final int FFT_SIZE = 512;
    private static final int HOP = FFT_SIZE / 2;
    private static final int BINS = FFT_SIZE / 2 + 1;
    /** Kenaikan noise floor per hop (adaptasi naik pelan). */
    private static final double NOISE_RISE = 1.006;
    private static final double EPS = 1e-9;

    private final double[] window = new double[FFT_SIZE];
    private final double[] anaBuf = new double[FFT_SIZE];
    private final double[] olaBuf = new double[FFT_SIZE];
    private final double[] noise = new double[BINS];
    private final double[] gainPrev = new double[BINS];
    private final double[] re = new double[FFT_SIZE];
    private final double[] im = new double[FFT_SIZE];

    // FIFO input/output granularitas sample (frame 960 bukan kelipatan hop 256).
    private double[] inQueue = new double[4096];
    private int inLen = 0;
    private double[] outQueue = new double[4096];
    private int outLen = 0;

    public NoiseSuppressor() {
        for (int i = 0; i < FFT_SIZE; i++) {
            // sqrt-Hann periodik: jumlah kuadrat pada overlap 50% = 1 (COLA).
            window[i] = Math.sqrt(0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / FFT_SIZE)));
        }
        reset();
    }

    /**
     * Proses satu frame PCM mono (biasanya 960 sample). Return frame BARU dengan
     * panjang sama, delay internal ~256 sample. strength 0..1 = agresivitas.
     */
    public short[] process(short[] frame, double strength) {
        double s = Math.max(0.0, Math.min(1.0, strength));
        pushInput(frame);

        while (inLen >= HOP) {
            hop(s);
        }

        short[] out = new short[frame.length];
        int deficit = frame.length - outLen;
        if (deficit > 0) {
            // startup: pad depan nol (pre-roll STFT), sisanya dari queue.
            for (int i = 0; i < outLen; i++) {
                out[deficit + i] = toShort(outQueue[i]);
            }
            outLen = 0;
        } else {
            for (int i = 0; i < frame.length; i++) {
                out[i] = toShort(outQueue[i]);
            }
            System.arraycopy(outQueue, frame.length, outQueue, 0, outLen - frame.length);
            outLen -= frame.length;
        }
        return out;
    }

    private void pushInput(short[] frame) {
        if (inLen + frame.length > inQueue.length) {
            double[] bigger = new double[Math.max(inQueue.length * 2, inLen + frame.length)];
            System.arraycopy(inQueue, 0, bigger, 0, inLen);
            inQueue = bigger;
        }
        for (short v : frame) {
            inQueue[inLen++] = v / 32768.0;
        }
    }

    private void hop(double strength) {
        // Geser jendela analisis: buang HOP lama, masuk HOP baru dari input queue.
        System.arraycopy(anaBuf, HOP, anaBuf, 0, FFT_SIZE - HOP);
        System.arraycopy(inQueue, 0, anaBuf, FFT_SIZE - HOP, HOP);
        System.arraycopy(inQueue, HOP, inQueue, 0, inLen - HOP);
        inLen -= HOP;

        for (int i = 0; i < FFT_SIZE; i++) {
            re[i] = anaBuf[i] * window[i];
            im[i] = 0.0;
        }
        fft(re, im, false);

        double alpha = 1.2 + 1.3 * strength;                 // over-subtraction
        double floorGain = 0.02 + (1.0 - strength) * 0.2;    // spectral floor

        for (int b = 0; b < BINS; b++) {
            double mag = Math.sqrt(re[b] * re[b] + im[b] * im[b]);
            // Minimum statistics lite: turun instan, naik pelan, tak melebihi mag.
            noise[b] = mag < noise[b] ? mag : Math.min(noise[b] * NOISE_RISE + EPS, mag);
            double g;
            if (mag <= EPS) {
                g = floorGain;
            } else {
                g = (mag - alpha * noise[b]) / mag;
                if (g < floorGain) g = floorGain;
            }
            // Smoothing temporal: naik cepat (speech onset), turun lebih pelan.
            g = g > gainPrev[b] ? gainPrev[b] * 0.4 + g * 0.6 : gainPrev[b] * 0.7 + g * 0.3;
            gainPrev[b] = g;
            re[b] *= g;
            im[b] *= g;
            int mirror = FFT_SIZE - b;
            if (b > 0 && mirror < FFT_SIZE) {
                re[mirror] *= g;
                im[mirror] *= g;
            }
        }

        fft(re, im, true);

        // Overlap-add dengan synthesis window, emit HOP sample siap keluar.
        for (int i = 0; i < FFT_SIZE; i++) {
            olaBuf[i] += re[i] * window[i];
        }
        ensureOutCapacity(outLen + HOP);
        System.arraycopy(olaBuf, 0, outQueue, outLen, HOP);
        outLen += HOP;
        System.arraycopy(olaBuf, HOP, olaBuf, 0, FFT_SIZE - HOP);
        java.util.Arrays.fill(olaBuf, FFT_SIZE - HOP, FFT_SIZE, 0.0);
    }

    private void ensureOutCapacity(int needed) {
        if (needed <= outQueue.length) return;
        double[] bigger = new double[Math.max(outQueue.length * 2, needed)];
        System.arraycopy(outQueue, 0, bigger, 0, outLen);
        outQueue = bigger;
    }

    public void reset() {
        java.util.Arrays.fill(anaBuf, 0.0);
        java.util.Arrays.fill(olaBuf, 0.0);
        java.util.Arrays.fill(noise, Double.MAX_VALUE); // hop pertama = estimasi awal
        java.util.Arrays.fill(gainPrev, 1.0);
        inLen = 0;
        outLen = 0;
    }

    private static short toShort(double v) {
        double x = v * 32768.0;
        if (x > 32767.0) return 32767;
        if (x < -32768.0) return -32768;
        return (short) x;
    }

    /** FFT radix-2 iteratif in-place. inverse=true termasuk skala 1/N. */
    private static void fft(double[] re, double[] im, boolean inverse) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double tr = re[i]; re[i] = re[j]; re[j] = tr;
                double ti = im[i]; im[i] = im[j]; im[j] = ti;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = 2.0 * Math.PI / len * (inverse ? 1 : -1);
            double wr = Math.cos(ang), wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1.0, ci = 0.0;
                int half = len >> 1;
                for (int k = 0; k < half; k++) {
                    int a = i + k, b = i + k + half;
                    double xr = re[b] * cr - im[b] * ci;
                    double xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr;
                    im[b] = im[a] - xi;
                    re[a] += xr;
                    im[a] += xi;
                    double ncr = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = ncr;
                }
            }
        }
        if (inverse) {
            for (int i = 0; i < n; i++) {
                re[i] /= n;
                im[i] /= n;
            }
        }
    }
}
