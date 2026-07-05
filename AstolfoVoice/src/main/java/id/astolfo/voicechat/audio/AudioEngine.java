package id.astolfo.voicechat.audio;

import id.astolfo.voicechat.api.PlaybackHandle;
import id.astolfo.voicechat.api.PlaybackOptions;
import id.astolfo.voicechat.api.impl.PlaybackHandleImpl;
import id.astolfo.voicechat.config.AstolfoConfig;
import id.astolfo.voicechat.voice.common.ClientConnection;
import id.astolfo.voicechat.voice.server.ServerVoiceEvents;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AudioEngine - orkestrasi decode + resample + streaming playback.
 * OpusManager (shared, ThreadLocal) + AudioDecoder + Resampler + StreamingAudioPlayer.
 * activePlaybacks di-decrement otomatis saat playback selesai (via watcher).
 *
 * v0.2.1: playToLocation() untuk pemutaran posisi per-listener (LocationSoundPacket).
 */
public final class AudioEngine {

    private final AstolfoConfig config;
    private final OpusManager opus;
    private final ServerVoiceEvents server;
    private final File audioDir;
    private final StreamingAudioPlayer streamer;
    private final AtomicInteger activePlaybacks = new AtomicInteger(0);
    private final CopyOnWriteArrayList<PlaybackHandleImpl> handles = new CopyOnWriteArrayList<>();

    public AudioEngine(AstolfoConfig config, ServerVoiceEvents server, File audioDir, OpusManager sharedOpus) {
        this.config = config;
        this.opus = sharedOpus;
        this.server = server;
        this.audioDir = audioDir;
        this.streamer = new StreamingAudioPlayer(opus, server.getVoiceServer(), server.getConnections());
    }

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

    public PlaybackHandle playToTargets(List<Player> targets, String file, PlaybackOptions options) {
        if (targets.isEmpty()) return null;
        File resolved = resolveFile(file);
        if (resolved == null) return null;
        if (activePlaybacks.get() >= config.maxConcurrentPlaybacks()) return null;
        try {
            short[] pcm48 = decodeTo48k(resolved);
            activePlaybacks.incrementAndGet();
            PlaybackHandleImpl handle = streamer.play(targets, pcm48, options, resolved.getName(), null);
            handles.add(handle);
            watchCompletion(handle);
            return handle;
        } catch (Exception e) {
            activePlaybacks.decrementAndGet();
            Bukkit.getLogger().warning("[Astolfo] Audio decode failed for " + file + ": " + e.getMessage());
            return null;
        }
    }

    /** Playback locational: suara di posisi `location`, attenuasi per-listener sesuai jarak. */
    public PlaybackHandle playToLocation(List<Player> targets, String file, PlaybackOptions options, Location location) {
        if (targets.isEmpty() || location == null || location.getWorld() == null) return null;
        File resolved = resolveFile(file);
        if (resolved == null) return null;
        if (activePlaybacks.get() >= config.maxConcurrentPlaybacks()) return null;
        try {
            short[] pcm48 = decodeTo48k(resolved);
            activePlaybacks.incrementAndGet();
            PlaybackHandleImpl handle = streamer.playLocation(targets, pcm48, options, resolved.getName(), null, location);
            handles.add(handle);
            watchCompletion(handle);
            return handle;
        } catch (Exception e) {
            activePlaybacks.decrementAndGet();
            Bukkit.getLogger().warning("[Astolfo] Audio decode failed for " + file + ": " + e.getMessage());
            return null;
        }
    }

    private short[] decodeTo48k(File resolved) throws Exception {
        AudioDecoder.Decoded decoded = AudioDecoder.decode(resolved);
        short[] pcm48 = Resampler.toTargetRate(decoded.samples, decoded.sampleRate, config.resampleQuality());
        if (config.audioNormalize()) {
            pcm48 = Resampler.normalize(pcm48);
        }
        return pcm48;
    }

    /** Watcher: decrement counter + cleanup handle saat playback selesai. */
    private void watchCompletion(PlaybackHandleImpl handle) {
        Thread.ofVirtual().name("astolfo-watch", 0).start(() -> {
            while (handle.isRunning()) {
                try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            activePlaybacks.decrementAndGet();
            handles.remove(handle);
        });
    }

    public PlaybackHandle playQueue(List<Player> targets, List<String> files, PlaybackOptions options, boolean loop) {
        if (targets.isEmpty() || files.isEmpty()) return null;
        PlaybackHandleImpl handle = new PlaybackHandleImpl("queue:" + files.size());
        Thread.ofVirtual().name("astolfo-queue", 0).start(() -> {
            do {
                for (String file : files) {
                    if (!handle.isRunning()) break;
                    File resolved = resolveFile(file);
                    if (resolved == null) continue;
                    if (activePlaybacks.get() >= config.maxConcurrentPlaybacks()) {
                        try { Thread.sleep(100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                        continue;
                    }
                    try {
                        short[] pcm48 = decodeTo48k(resolved);
                        activePlaybacks.incrementAndGet();
                        PlaybackHandleImpl part = streamer.play(targets, pcm48, options, resolved.getName(), null);
                        while (part.isRunning()) {
                            if (!handle.isRunning()) { part.stop(); break; }
                            try { Thread.sleep(20); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); handle.markStopped(); break; }
                        }
                        activePlaybacks.decrementAndGet();
                    } catch (Exception e) {
                        Bukkit.getLogger().warning("[Astolfo] Queue decode failed for " + file + ": " + e.getMessage());
                    }
                }
            } while (loop && handle.isRunning());
            handle.markStopped();
        });
        handles.add(handle);
        return handle;
    }

    public void stop(PlaybackHandle handle) {
        if (handle instanceof PlaybackHandleImpl impl) {
            impl.stop();
            handles.remove(impl);
        }
    }

    public void stopAll() {
        for (PlaybackHandleImpl h : handles) h.stop();
        handles.clear();
        activePlaybacks.set(0);
    }

    public int activeCount() { return activePlaybacks.get(); }
    public File getAudioDir() { return audioDir; }
    public OpusManager getOpus() { return opus; }
}
