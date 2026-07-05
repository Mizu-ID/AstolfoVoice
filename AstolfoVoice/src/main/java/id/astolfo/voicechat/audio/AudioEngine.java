package id.astolfo.voicechat.audio;

import id.astolfo.voicechat.api.PlaybackHandle;
import id.astolfo.voicechat.api.PlaybackOptions;
import id.astolfo.voicechat.api.impl.PlaybackHandleImpl;
import id.astolfo.voicechat.config.AstolfoConfig;
import id.astolfo.voicechat.voice.common.ClientConnection;
import id.astolfo.voicechat.voice.common.Codec;
import id.astolfo.voicechat.voice.server.ServerVoiceEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AudioEngine — orkestrasi decode + resample + streaming playback.
 * Memakai OpusManager (ThreadLocal) + AudioDecoder + Resampler + StreamingAudioPlayer.
 * Membatasi jumlah playback concurrent (config.max_concurrent_playbacks).
 */
public final class AudioEngine {

    private final AstolfoConfig config;
    private final OpusManager opus;
    private final ServerVoiceEvents server;
    private final File audioDir;
    private final StreamingAudioPlayer streamer;
    private final AtomicInteger activePlaybacks = new AtomicInteger(0);
    private final CopyOnWriteArrayList<PlaybackHandleImpl> handles = new CopyOnWriteArrayList<>();

    public AudioEngine(AstolfoConfig config, ServerVoiceEvents server, File audioDir) {
        this.config = config;
        Codec codec;
        try {
            codec = Codec.valueOf(config.codecName());
        } catch (IllegalArgumentException e) {
            codec = Codec.VOIP;
        }
        this.opus = new OpusManager(codec, config.opusBitrate());
        this.server = server;
        this.audioDir = audioDir;
        this.streamer = new StreamingAudioPlayer(opus, server.getVoiceServer(), server.getConnections());
    }

    /** Resolve file audio (case-insensitive, boleh tanpa ekstensi). */
    public File resolveFile(String name) {
        if (name == null) return null;
        File direct = new File(audioDir, name);
        if (direct.exists()) return direct;
        for (String ext : new String[]{"", ".mp3", ".ogg", ".wav"}) {
            File f = new File(audioDir, name + ext);
            if (f.exists()) return f;
        }
        File[] files = audioDir.listFiles();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (n.equalsIgnoreCase(name) || n.equalsIgnoreCase(name + ".mp3")
                        || n.equalsIgnoreCase(name + ".ogg") || n.equalsIgnoreCase(name + ".wav")) {
                    return f;
                }
            }
        }
        return null;
    }

    /** Play ke daftar target. Return handle, atau null bila file tidak ada / kuota penuh. */
    public PlaybackHandle playToTargets(List<Player> targets, String file, PlaybackOptions options) {
        if (targets.isEmpty()) return null;
        File resolved = resolveFile(file);
        if (resolved == null) return null;
        if (activePlaybacks.get() >= config.maxConcurrentPlaybacks()) return null;
        try {
            AudioDecoder.Decoded decoded = AudioDecoder.decode(resolved);
            short[] pcm48 = Resampler.toTargetRate(decoded.samples, decoded.sampleRate, config.resampleQuality());
            if (config.audioNormalize()) {
                pcm48 = Resampler.normalize(pcm48);
            }
            activePlaybacks.incrementAndGet();
            PlaybackHandleImpl handle = streamer.play(targets, pcm48, options, resolved.getName(), null);
            handles.add(handle);
            return handle;
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Astolfo] Audio decode failed for " + file + ": " + e.getMessage());
            return null;
        }
    }

    public void stop(PlaybackHandle handle) {
        if (handle instanceof PlaybackHandleImpl impl) {
            impl.stop();
            handles.remove(impl);
            activePlaybacks.decrementAndGet();
        }
    }

    public void stopAll() {
        for (PlaybackHandleImpl h : handles) {
            h.stop();
        }
        handles.clear();
        activePlaybacks.set(0);
    }

    public int activeCount() {
        return activePlaybacks.get();
    }
}
