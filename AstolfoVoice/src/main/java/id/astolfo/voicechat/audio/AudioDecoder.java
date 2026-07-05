package id.astolfo.voicechat.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * AudioDecoder — decode mp3/ogg/wav ke PCM mono 16-bit pada sample-rate asli.
 * Resample ke 48k dilakukan terpisah oleh Resampler.
 *
 * Format output: short[] (mono), sampleRate dari format asli.
 */
public final class AudioDecoder {

    public static final class Decoded {
        public final short[] samples;   // mono PCM 16-bit
        public final int sampleRate;    // sample rate asli

        public Decoded(short[] samples, int sampleRate) {
            this.samples = samples;
            this.sampleRate = sampleRate;
        }
    }

    private AudioDecoder() {
    }

    /** Decode file berdasarkan ekstensi. */
    public static Decoded decode(File file) throws Exception {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".wav")) return decodeWav(file);
        if (name.endsWith(".mp3")) return decodeMp3(file);
        if (name.endsWith(".ogg")) return decodeOgg(file);
        throw new IllegalArgumentException("Unsupported audio format: " + name);
    }

    // ---- WAV via javax.sound ----
    private static Decoded decodeWav(File file) throws Exception {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(file)) {
            return toMonoShorts(in, in.getFormat());
        }
    }

    // ---- MP3 via JLayer ----
    private static Decoded decodeMp3(File file) throws Exception {
        javazoom.jl.decoder.Bitstream bitstream = new javazoom.jl.decoder.Bitstream(
                new BufferedInputStream(new FileInputStream(file)));
        // SampleBuffer(sampleRate, channels) — diisi oleh decoder via setOutputBuffer.
        javazoom.jl.decoder.SampleBuffer sb = new javazoom.jl.decoder.SampleBuffer(48000, 2);
        javazoom.jl.decoder.Decoder decoder = new javazoom.jl.decoder.Decoder();
        decoder.setOutputBuffer(sb);
        List<short[]> frames = new ArrayList<>();
        int sampleRate = 48000;
        int channels = 2;
        try {
            javazoom.jl.decoder.Header h;
            while ((h = bitstream.readFrame()) != null) {
                decoder.decodeFrame(h, bitstream);
                sampleRate = sb.getSampleFrequency();
                channels = sb.getChannelCount();
                short[] buf = sb.getBuffer();
                int len = sb.getBufferLength();
                short[] frame = new short[len];
                System.arraycopy(buf, 0, frame, 0, len);
                frames.add(frame);
                sb.clear_buffer();
                bitstream.closeFrame();
            }
        } finally {
            bitstream.close();
        }
        return flattenToMono(frames, sampleRate, channels);
    }

    // ---- OGG via AudioSystem SPI (bila tersedia) ----
    private static Decoded decodeOgg(File file) throws Exception {
        try (InputStream fin = new BufferedInputStream(new FileInputStream(file))) {
            AudioInputStream in = AudioSystem.getAudioInputStream(fin);
            return toMonoShorts(in, in.getFormat());
        } catch (Exception e) {
            throw new IllegalArgumentException("OGG decode butuh SPI vorbis di classpath server. "
                    + "Konversi ke wav/mp3 untuk sekarang. Detail: " + e.getMessage(), e);
        }
    }

    // ---- Helpers ----

    private static Decoded toMonoShorts(AudioInputStream in, AudioFormat fmt) throws Exception {
        AudioFormat target = new AudioFormat(fmt.getSampleRate(), 16, fmt.getChannels(), true, false);
        try (AudioInputStream converted = AudioSystem.getAudioInputStream(target, in)) {
            byte[] bytes = converted.readAllBytes();
            int channels = fmt.getChannels();
            short[] mono = bytesToMonoShorts(bytes, channels);
            return new Decoded(mono, (int) fmt.getSampleRate());
        }
    }

    private static short[] bytesToMonoShorts(byte[] bytes, int channels) {
        int frameBytes = 2 * channels;
        int frames = bytes.length / frameBytes;
        short[] out = new short[frames];
        for (int i = 0; i < frames; i++) {
            int sum = 0;
            for (int c = 0; c < channels; c++) {
                int lo = bytes[i * frameBytes + c * 2] & 0xFF;
                int hi = bytes[i * frameBytes + c * 2 + 1];
                sum += (short) ((hi << 8) | lo);
            }
            out[i] = (short) (sum / channels);
        }
        return out;
    }

    private static Decoded flattenToMono(List<short[]> frames, int sampleRate, int channels) {
        int total = 0;
        for (short[] f : frames) total += f.length / channels;
        short[] out = new short[total];
        int pos = 0;
        for (short[] f : frames) {
            int n = f.length / channels;
            for (int i = 0; i < n; i++) {
                int sum = 0;
                for (int c = 0; c < channels; c++) {
                    sum += f[i * channels + c];
                }
                out[pos++] = (short) (sum / channels);
            }
        }
        return new Decoded(out, sampleRate);
    }
}
