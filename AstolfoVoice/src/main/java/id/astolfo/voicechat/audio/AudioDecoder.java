package id.astolfo.voicechat.audio;

import com.jcraft.jogg.Page;
import com.jcraft.jogg.Packet;
import com.jcraft.jogg.StreamState;
import com.jcraft.jogg.SyncState;
import com.jcraft.jorbis.Block;
import com.jcraft.jorbis.Comment;
import com.jcraft.jorbis.DspState;
import com.jcraft.jorbis.Info;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
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
 *
 * Implementasi:
 *  - WAV: javax.sound.sampled (JDK built-in).
 *  - MP3: JLayer (javazoom.jl) pure-Java decoder.
 *  - OGG: JOrbis low-level public API (SyncState/StreamState/DspState/Block).
 *    Tidak bergantung pada SPI vorbis server dan tidak pakai VorbisFile.read
 *    (yang package-private) — jadi OGG jalan tanpa dependensi ekstra & tanpa reflection.
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

    private static final int OGG_BUF = 4096;

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
        Bitstream bitstream = new Bitstream(new BufferedInputStream(new FileInputStream(file)));
        SampleBuffer sb = new SampleBuffer(48000, 2);
        Decoder decoder = new Decoder();
        decoder.setOutputBuffer(sb);
        List<short[]> frames = new ArrayList<>();
        int sampleRate = 48000;
        int channels = 2;
        try {
            Header h;
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

    // ---- OGG via JOrbis low-level public API (no VorbisFile, no SPI) ----
    private static Decoded decodeOgg(File file) throws Exception {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            SyncState oy = new SyncState();
            StreamState os = new StreamState();
            Info vi = new Info();
            Comment vc = new Comment();
            DspState vd = new DspState();
            Block vb = new Block(vd);

            oy.init();
            vi.init();
            vc.init();

            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            // conv buffer: enough for ~1024 samples * 2 channels * 2 bytes
            byte[] conv = new byte[4096];
            int convSamps = conv.length / 4; // assume <=2ch; bounded below

            boolean streamInit = false;
            boolean headersDone = false;
            int headerCount = 0;
            int channels = 1;
            int rate = 48000;
            boolean eos = false;

            try {
                while (!eos) {
                    int index = oy.buffer(OGG_BUF);
                    int r = in.read(oy.data, index, OGG_BUF);
                    if (r <= 0) {
                        oy.wrote(0);
                        eos = true;
                    } else {
                        oy.wrote(r);
                    }

                    Page og = new Page();
                    while (oy.pageout(og) == 1) {
                        if (!streamInit) {
                            os.init(og.serialno());
                            streamInit = true;
                        }
                        os.pagein(og);

                        Packet op = new Packet();
                        while (os.packetout(op) == 1) {
                            if (!headersDone) {
                                int res = vi.synthesis_headerin(vc, op);
                                if (res != 0 && headerCount == 0) {
                                    throw new IllegalArgumentException("Stream is not Vorbis (header 0 rejected)");
                                }
                                headerCount++;
                                if (headerCount == 3) {
                                    headersDone = true;
                                    channels = vi.channels;
                                    rate = vi.rate;
                                    if (channels < 1) channels = 1;
                                    vd.synthesis_init(vi);
                                    vb.init(vd);
                                    // bound conv buffer by actual channels
                                    convSamps = conv.length / (2 * channels);
                                }
                            } else {
                                if (vb.synthesis(op) == 0) {
                                    vd.synthesis_blockin(vb);
                                }
                                float[][][] pcm = new float[1][][];
                                int[] idx = new int[channels];
                                int samples;
                                while ((samples = vd.synthesis_pcmout(pcm, idx)) > 0) {
                                    int bout = Math.min(samples, convSamps);
                                    for (int s = 0; s < bout; s++) {
                                        for (int ch = 0; ch < channels; ch++) {
                                            int val = (int) (pcm[0][ch][idx[ch] + s] * 32767.0);
                                            if (val > 32767) val = 32767;
                                            if (val < -32768) val = -32768;
                                            conv[2 * (s * channels + ch)] = (byte) val;
                                            conv[2 * (s * channels + ch) + 1] = (byte) (val >>> 8);
                                        }
                                    }
                                    out.write(conv, 0, bout * channels * 2);
                                    vd.synthesis_read(bout);
                                }
                            }
                        }
                    }
                }
            } finally {
                os.clear();
                vb.clear();
                vd.clear();
                vi.clear();
                oy.clear();
            }

            if (!headersDone) {
                throw new IllegalArgumentException("OGG stream missing Vorbis headers");
            }
            short[] mono = bytesLeToMonoShorts(out.toByteArray(), channels);
            return new Decoded(mono, rate);
        }
    }

    // ---- Helpers ----

    private static Decoded toMonoShorts(AudioInputStream in, AudioFormat fmt) throws Exception {
        AudioFormat target = new AudioFormat(fmt.getSampleRate(), 16, fmt.getChannels(), true, false);
        try (AudioInputStream converted = AudioSystem.getAudioInputStream(target, in)) {
            byte[] bytes = converted.readAllBytes();
            int channels = fmt.getChannels();
            short[] mono = bytesLeToMonoShorts(bytes, channels);
            return new Decoded(mono, (int) fmt.getSampleRate());
        }
    }

    /** Convert little-endian 16-bit interleaved bytes → mono shorts (channel-averaged). */
    private static short[] bytesLeToMonoShorts(byte[] bytes, int channels) {
        if (channels < 1) channels = 1;
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
        if (channels < 1) channels = 1;
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
