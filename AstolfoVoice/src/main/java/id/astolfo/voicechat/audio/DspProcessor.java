package id.astolfo.voicechat.audio;

import id.astolfo.voicechat.voice.common.AudioUtils;

/**
 * DspProcessor — DSP PCM ringan untuk Tier 2 sound physics bake.
 *  - LowPassBiquad: muffle (air, dinding) — cutoff adaptif.
 *  - ReverbNet: feedback delay network sederhana untuk gema ruang tertutup.
 *  - NoiseGate: suppress lantai noise (bukan NC penuh; RNNoise/Speex = Fase 4 native).
 *
 * Semua bekerja pada short[FRAME_SIZE] mono 48k. State dipertahankan per-instance
 * (bukan thread-safe; pakai per-speaker-per-listener instance atau ThreadLocal).
 */
public final class DspProcessor {

    private final LowPassBiquad lowpass = new LowPassBiquad();
    private final ReverbNet reverb = new ReverbNet();
    private final NoiseGate gate = new NoiseGate();

    /** Proses satu frame: apply lowpass (bila cutoff < max), reverb (bila amount>0), gate. */
    public void process(short[] pcm, float lowpassHz, float reverbAmount, boolean gateEnabled, float gateThresholdDb) {
        if (lowpassHz > 0 && lowpassHz < AudioUtils.SAMPLE_RATE / 2f) {
            lowpass.setCutoff(lowpassHz);
            lowpass.processInPlace(pcm);
        }
        if (reverbAmount > 0f) {
            reverb.setAmount(reverbAmount);
            reverb.processInPlace(pcm);
        }
        if (gateEnabled) {
            gate.setThresholdDb(gateThresholdDb);
            gate.processInPlace(pcm);
        }
    }

    public void reset() {
        lowpass.reset();
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
            double b0 = (1 - cosW) / 2, b0n = b0, b1n = 1 - cosW, b2n = (1 - cosW) / 2;
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
        private double amount = 0.0;
        private final int[] delaySamples;
        private final double[][] buffers;
        private final int[] pos;
        private static final double[] GAINS = {0.77, 0.73, 0.70, 0.66};

        ReverbNet() {
            // delay dalam ms → samples (48k). Berbeda untuk decorrelate.
            double[] ms = {29.0, 37.0, 41.0, 43.0};
            delaySamples = new int[4];
            buffers = new double[4][];
            pos = new int[4];
            for (int i = 0; i < 4; i++) {
                delaySamples[i] = (int) (ms[i] / 1000.0 * AudioUtils.SAMPLE_RATE);
                buffers[i] = new double[delaySamples[i]];
                pos[i] = 0;
            }
        }

        void setAmount(double a) { this.amount = Math.max(0, Math.min(0.6, a)); }

        void processInPlace(short[] pcm) {
            if (amount <= 0) return;
            for (int i = 0; i < pcm.length; i++) {
                double in = pcm[i] / 32768.0;
                double wet = 0;
                for (int t = 0; t < 4; t++) {
                    double d = buffers[t][pos[t]];
                    wet += d * GAINS[t];
                    // feedback + input
                    buffers[t][pos[t]] = in + d * GAINS[t] * 0.5;
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
        private double thresholdLinear = 0.0; // 0 = gate off
        private double env = 0.0;
        private static final double ATTACK = 0.5, RELEASE = 0.02;

        void setThresholdDb(double db) {
            if (db <= -120) { thresholdLinear = 0; return; }
            thresholdLinear = AudioUtils.dbToLinear(db);
        }

        void processInPlace(short[] pcm) {
            if (thresholdLinear <= 0) return;
            for (int i = 0; i < pcm.length; i++) {
                double x = Math.abs(pcm[i] / 32768.0);
                env = Math.max(x, env * 0.96); // envelope follower
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
