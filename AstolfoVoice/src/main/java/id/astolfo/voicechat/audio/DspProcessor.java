package id.astolfo.voicechat.audio;

import id.astolfo.voicechat.api.PlaybackOptions;
import id.astolfo.voicechat.voice.common.AudioUtils;

/**
 * DspProcessor - DSP PCM ringan untuk Tier 2 sound physics bake + sound-effect preset.
 *  - LowPassBiquad / HighPassBiquad / BandPassBiquad: muffle, phone, radio, megaphone.
 *  - ReverbNet: feedback delay network untuk gema (cave, ruang tertutup).
 *  - NoiseGate: suppress lantai noise (bukan NC penuh; RNNoise/Speex = roadmap native).
 *  - SoftClip: megaphone mild distortion.
 *
 * Semua bekerja pada short[FRAME_SIZE] mono 48k. State dipertahankan per-instance
 * (bukan thread-safe; pakai per-speaker-per-listener instance atau ThreadLocal).
 */
public final class DspProcessor {

    private final LowPassBiquad lowpass = new LowPassBiquad();
    private final HighPassBiquad highpass = new HighPassBiquad();
    private final BandPassBiquad bandpass = new BandPassBiquad();
    private final ReverbNet reverb = new ReverbNet();
    private final NoiseGate gate = new NoiseGate();

    /** Proses satu frame: apply lowpass (bila cutoff < max), reverb (bila amount>0), gate. */
    public void process(short[] pcm, float lowpassHz, float reverbAmount, boolean gateEnabled, float gateThresholdDb) {
        process(pcm, lowpassHz, reverbAmount, 1.2f, gateEnabled, gateThresholdDb);
    }

    /** Versi lengkap: reverbDecaySeconds dari config sound_physics.reverb_decay_seconds. */
    public void process(short[] pcm, float lowpassHz, float reverbAmount, float reverbDecaySeconds,
                        boolean gateEnabled, float gateThresholdDb) {
        if (lowpassHz > 0 && lowpassHz < AudioUtils.SAMPLE_RATE / 2f) {
            lowpass.setCutoff(lowpassHz);
            lowpass.processInPlace(pcm);
        }
        if (reverbAmount > 0f) {
            reverb.setAmount(reverbAmount);
            reverb.setDecay(reverbDecaySeconds);
            reverb.processInPlace(pcm);
        }
        if (gateEnabled) {
            gate.setThresholdDb(gateThresholdDb);
            gate.processInPlace(pcm);
        }
    }

    /** Apply sound-effect preset ke satu frame (setelah pitch/gain). */
    public void applyPreset(short[] pcm, PlaybackOptions.Preset preset) {
        switch (preset) {
            case PHONE -> {
                // bandpass 300-3400 Hz: sinyal telepon/radio narrow.
                bandpass.setBand(300f, 3400f);
                bandpass.processInPlace(pcm);
            }
            case RADIO -> {
                // bandpass narrow 500-2800 Hz + light noise gate.
                bandpass.setBand(500f, 2800f);
                bandpass.processInPlace(pcm);
                gate.setThresholdDb(-50f);
                gate.processInPlace(pcm);
            }
            case MEGA -> {
                // megaphone: highpass 400 Hz + gain + mild soft clip.
                highpass.setCutoff(400f);
                highpass.processInPlace(pcm);
                softClip(pcm, 1.6);
            }
            case CAVE -> {
                // gema ruang besar: lowpass 5000 + strong reverb.
                lowpass.setCutoff(5000f);
                lowpass.processInPlace(pcm);
                reverb.setAmount(0.55);
                reverb.processInPlace(pcm);
            }
            case KAWAII -> {
                // feel cerah & lembut: highpass 200 + reverb kecil + sedikit warm gain.
                highpass.setCutoff(200f);
                highpass.processInPlace(pcm);
                reverb.setAmount(0.28);
                reverb.processInPlace(pcm);
            }
            case LOFI -> {
                // lofi warm: lowpass 3500 + reverb kecil + vibe cozy.
                lowpass.setCutoff(3500f);
                lowpass.processInPlace(pcm);
                reverb.setAmount(0.22);
                reverb.processInPlace(pcm);
            }
            case NONE -> {
                // nothing
            }
        }
    }

    /** Soft clip (tanh-ish) untuk megaphone: distortion halus, anti hard-clip. */
    private static void softClip(short[] pcm, double drive) {
        for (int i = 0; i < pcm.length; i++) {
            double x = pcm[i] / 32768.0 * drive;
            // tanh approximation: x/(1+|x|) - cepat & monoton.
            double y = x / (1.0 + Math.abs(x));
            pcm[i] = (short) (y * 32767.0);
        }
    }

    public void reset() {
        lowpass.reset();
        highpass.reset();
        bandpass.reset();
        reverb.reset();
        gate.reset();
    }

    // ---------- Low-pass biquad (RBJ) ----------
    static final class LowPassBiquad {
        private double a0, a1, a2, b1, b2;
        private double x1, x2, y1, y2;

        void setCutoff(double hz) {
            double w0 = 2 * Math.PI * hz / AudioUtils.SAMPLE_RATE;
            double cosW = Math.cos(w0), sinW = Math.sin(w0);
            double alpha = sinW / (2 * 0.707); // Q=0.707
            double b0n = (1 - cosW) / 2, b1n = 1 - cosW, b2n = (1 - cosW) / 2;
            double a0n = 1 + alpha, a1n = -2 * cosW, a2n = 1 - alpha;
            a0 = b0n / a0n; a1 = b1n / a0n; a2 = b2n / a0n; b1 = a1n / a0n; b2 = a2n / a0n;
        }

        void processInPlace(short[] pcm) {
            for (int i = 0; i < pcm.length; i++) {
                double x = pcm[i];
                double y = a0 * x + a1 * x1 + a2 * x2 - b1 * y1 - b2 * y2;
                x2 = x1; x1 = x; y2 = y1; y1 = y;
                pcm[i] = clamp(y);
            }
        }

        void reset() { x1 = x2 = y1 = y2 = 0; }
    }

    // ---------- High-pass biquad (RBJ) ----------
    static final class HighPassBiquad {
        private double a0, a1, a2, b1, b2;
        private double x1, x2, y1, y2;

        void setCutoff(double hz) {
            double w0 = 2 * Math.PI * hz / AudioUtils.SAMPLE_RATE;
            double cosW = Math.cos(w0), sinW = Math.sin(w0);
            double alpha = sinW / (2 * 0.707);
            double b0n = (1 + cosW) / 2, b1n = -(1 + cosW), b2n = (1 + cosW) / 2;
            double a0n = 1 + alpha, a1n = -2 * cosW, a2n = 1 - alpha;
            a0 = b0n / a0n; a1 = b1n / a0n; a2 = b2n / a0n; b1 = a1n / a0n; b2 = a2n / a0n;
        }

        void processInPlace(short[] pcm) {
            for (int i = 0; i < pcm.length; i++) {
                double x = pcm[i];
                double y = a0 * x + a1 * x1 + a2 * x2 - b1 * y1 - b2 * y2;
                x2 = x1; x1 = x; y2 = y1; y1 = y;
                pcm[i] = clamp(y);
            }
        }

        void reset() { x1 = x2 = y1 = y2 = 0; }
    }

    // ---------- Band-pass biquad (RBJ, constant 0 dB peak gain) ----------
    static final class BandPassBiquad {
        private double a0, a1, a2, b1, b2;
        private double x1, x2, y1, y2;

        void setBand(double lowHz, double highHz) {
            double center = Math.sqrt(lowHz * highHz);
            double bw = highHz - lowHz;
            double w0 = 2 * Math.PI * center / AudioUtils.SAMPLE_RATE;
            double cosW = Math.cos(w0), sinW = Math.sin(w0);
            double Q = center / bw;
            if (Q < 0.1) Q = 0.1;
            double alpha = sinW / (2 * Q);
            // constant 0 dB peak gain bandpass
            double b0n = alpha, b1n = 0, b2n = -alpha;
            double a0n = 1 + alpha, a1n = -2 * cosW, a2n = 1 - alpha;
            a0 = b0n / a0n; a1 = b1n / a0n; a2 = b2n / a0n; b1 = a1n / a0n; b2 = a2n / a0n;
        }

        void processInPlace(short[] pcm) {
            for (int i = 0; i < pcm.length; i++) {
                double x = pcm[i];
                double y = a0 * x + a1 * x1 + a2 * x2 - b1 * y1 - b2 * y2;
                x2 = x1; x1 = x; y2 = y1; y1 = y;
                pcm[i] = clamp(y);
            }
        }

        void reset() { x1 = x2 = y1 = y2 = 0; }
    }

    // ---------- Reverb: feedback delay network (4 tap) ----------
    static final class ReverbNet {
        private static final double[] DELAY_MS = {29.0, 37.0, 41.0, 43.0};

        private double amount = 0.0;
        private double decaySeconds = -1;
        private final int[] delaySamples;
        private final double[][] buffers;
        private final int[] pos;
        private final double[] gains = {0.77, 0.73, 0.70, 0.66};

        ReverbNet() {
            delaySamples = new int[4];
            buffers = new double[4][];
            pos = new int[4];
            for (int i = 0; i < 4; i++) {
                delaySamples[i] = (int) (DELAY_MS[i] / 1000.0 * AudioUtils.SAMPLE_RATE);
                buffers[i] = new double[delaySamples[i]];
                pos[i] = 0;
            }
        }

        void setAmount(double a) { this.amount = Math.max(0, Math.min(0.6, a)); }

        /** Map decay RT60 (detik) ke feedback gain per tap: g = 10^(-3*delay/decay). */
        void setDecay(double seconds) {
            if (seconds <= 0 || seconds == decaySeconds) return;
            decaySeconds = seconds;
            for (int i = 0; i < 4; i++) {
                double g = Math.pow(10.0, -3.0 * (DELAY_MS[i] / 1000.0) / seconds);
                gains[i] = Math.max(0.3, Math.min(0.85, g));
            }
        }

        void processInPlace(short[] pcm) {
            if (amount <= 0) return;
            for (int i = 0; i < pcm.length; i++) {
                double in = pcm[i] / 32768.0;
                double wet = 0;
                for (int t = 0; t < 4; t++) {
                    double d = buffers[t][pos[t]];
                    wet += d * gains[t];
                    buffers[t][pos[t]] = in + d * gains[t] * 0.5;
                    pos[t] = (pos[t] + 1) % delaySamples[t];
                }
                wet /= 4.0;
                double out = in * (1 - amount) + wet * amount;
                pcm[i] = (short) (clamp(out * 32768.0));
            }
        }

        void reset() { for (double[] b : buffers) java.util.Arrays.fill(b, 0); java.util.Arrays.fill(pos, 0); }
    }

    // ---------- Noise gate ----------
    static final class NoiseGate {
        private double thresholdLinear = 0.0;
        private double env = 0.0;

        void setThresholdDb(double db) {
            if (db <= -120) { thresholdLinear = 0; return; }
            thresholdLinear = AudioUtils.dbToLinear(db);
        }

        void processInPlace(short[] pcm) {
            if (thresholdLinear <= 0) return;
            for (int i = 0; i < pcm.length; i++) {
                double x = Math.abs(pcm[i] / 32768.0);
                env = Math.max(x, env * 0.96);
                double gain = env >= thresholdLinear ? 1.0 : (env / thresholdLinear * 0.3);
                pcm[i] = (short) (pcm[i] * gain);
            }
        }

        void reset() { env = 0; }
    }

    private static short clamp(double v) {
        if (v > 32767) return 32767;
        if (v < -32768) return -32768;
        return (short) v;
    }
}
