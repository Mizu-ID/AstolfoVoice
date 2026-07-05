package id.astolfo.voicechat.audio;

import id.astolfo.voicechat.voice.common.AudioUtils;
import id.astolfo.voicechat.voice.common.Codec;
import org.concentus.OpusApplication;
import org.concentus.OpusDecoder;
import org.concentus.OpusEncoder;
import org.concentus.OpusException;

import java.util.concurrent.ConcurrentHashMap;

/**
 * OpusManager — encoder/decoder pool. Encoder Opus TIDAK thread-safe,
 * jadi pakai ThreadLocal per-thread instance (IMPLEMENTATION_PLAN §E.3).
 *
 * Sample rate 48000, frame 960 (20ms), mono (1 channel).
 */
public final class OpusManager {

    public static final int SAMPLE_RATE = AudioUtils.SAMPLE_RATE;
    public static final int FRAME_SIZE = AudioUtils.FRAME_SIZE;
    public static final int CHANNELS = 1;
    public static final int MAX_PAYLOAD = AudioUtils.MAX_OPUS_PAYLOAD_SIZE;

    private final Codec codec;
    private final int bitrate;

    private final ThreadLocal<OpusEncoder> encoderHolder;
    private final ThreadLocal<OpusDecoder> decoderHolder;

    public OpusManager(Codec codec, int bitrate) {
        this.codec = codec;
        this.bitrate = bitrate;
        this.encoderHolder = ThreadLocal.withInitial(this::createEncoder);
        this.decoderHolder = ThreadLocal.withInitial(this::createDecoder);
    }

    public OpusManager() {
        this(Codec.VOIP, 64);
    }

    private OpusApplication application() {
        return switch (codec) {
            case AUDIO -> OpusApplication.OPUS_APPLICATION_AUDIO;
            case RESTRICTED_LOWDELAY -> OpusApplication.OPUS_APPLICATION_RESTRICTED_LOWDELAY;
            default -> OpusApplication.OPUS_APPLICATION_VOIP;
        };
    }

    private OpusEncoder createEncoder() {
        try {
            OpusEncoder enc = new OpusEncoder(SAMPLE_RATE, CHANNELS, application());
            enc.setBitrate(bitrate * 1000);
            enc.setComplexity(10);
            return enc;
        } catch (OpusException e) {
            throw new RuntimeException("Failed to create Opus encoder", e);
        }
    }

    private OpusDecoder createDecoder() {
        try {
            return new OpusDecoder(SAMPLE_RATE, CHANNELS);
        } catch (OpusException e) {
            throw new RuntimeException("Failed to create Opus decoder", e);
        }
    }

    /** Encode short[FRAME_SIZE] mono → opus bytes. */
    public byte[] encode(short[] pcm) {
        byte[] out = new byte[MAX_PAYLOAD];
        try {
            OpusEncoder enc = encoderHolder.get();
            int len = enc.encode(pcm, 0, FRAME_SIZE, out, 0, out.length);
            if (len <= 0) {
                return new byte[0];
            }
            byte[] result = new byte[len];
            System.arraycopy(out, 0, result, 0, len);
            return result;
        } catch (OpusException e) {
            return new byte[0];
        }
    }

    /** Decode opus bytes → short[FRAME_SIZE] mono. Return null bila gagal. */
    public short[] decode(byte[] opus) {
        if (opus == null || opus.length == 0) {
            return new short[FRAME_SIZE];
        }
        try {
            short[] out = new short[FRAME_SIZE];
            OpusDecoder dec = decoderHolder.get();
            int decoded = dec.decode(opus, 0, opus.length, out, 0, FRAME_SIZE, false);
            if (decoded <= 0) {
                return null;
            }
            return out;
        } catch (OpusException e) {
            return null;
        }
    }

    public int getSampleRate() {
        return SAMPLE_RATE;
    }

    public int getFrameSize() {
        return FRAME_SIZE;
    }
}
