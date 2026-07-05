package id.astolfo.voicechat.audio;

import id.astolfo.voicechat.api.PlaybackOptions;
import id.astolfo.voicechat.api.impl.PlaybackHandleImpl;
import id.astolfo.voicechat.voice.common.AudioUtils;
import id.astolfo.voicechat.voice.common.ClientConnection;
import id.astolfo.voicechat.voice.common.NetworkMessage;
import id.astolfo.voicechat.voice.common.PlayerSoundPacket;
import id.astolfo.voicechat.voice.server.VoiceServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * StreamingAudioPlayer — mutar PCM 48k mono ke listener via Opus, paced 20ms/frame.
 * Paket dikirim sebagai PlayerSoundPacket dengan sequenceNumber < 0 (audio channel,
 * isFromClientAudioChannel() = true). Non-locational: distance = config default.
 *
 * Async: tiap playback jalan di virtual thread dengan pacing real-time.
 */
public final class StreamingAudioPlayer {

    private static final long FRAME_NS = 20_000_000L; // 20ms
    private static final AtomicLong CHANNEL_ID_SEQ = new AtomicLong(0);

    private final OpusManager opus;
    private final VoiceServer voiceServer;
    private final ConcurrentHashMap<UUID, ClientConnection> connections;

    public StreamingAudioPlayer(OpusManager opus, VoiceServer voiceServer,
                                ConcurrentHashMap<UUID, ClientConnection> connections) {
        this.opus = opus;
        this.voiceServer = voiceServer;
        this.connections = connections;
    }

    /** Mulai playback ke daftar player. Return handle. */
    public PlaybackHandleImpl play(List<Player> targets, short[] pcm48k, PlaybackOptions options, String file,
                                   UUID sourceChannelId) {
        PlaybackHandleImpl handle = new PlaybackHandleImpl(file);
        Thread t = Thread.ofVirtual().name("astolfo-play-", 0).unstarted(() -> {
            short[] data = Resampler.alignToFrame(pcm48k);
            data = Resampler.applyGain(data, options.getVolume());
            int frames = data.length / AudioUtils.FRAME_SIZE;
            UUID channelId = sourceChannelId != null ? sourceChannelId
                    : new UUID(0L, CHANNEL_ID_SEQ.incrementAndGet());
            long start = System.nanoTime();
            float distance = (float) options.getDistance();
            for (int f = 0; f < frames && handle.isRunning(); f++) {
                short[] frame = new short[AudioUtils.FRAME_SIZE];
                System.arraycopy(data, f * AudioUtils.FRAME_SIZE, frame, 0, AudioUtils.FRAME_SIZE);
                byte[] opusData = opus.encode(frame);
                PlayerSoundPacket pkt = new PlayerSoundPacket(channelId, channelId, opusData, -1L, false, distance, options.getCategory());
                for (Player p : targets) {
                    if (!p.isOnline()) continue;
                    ClientConnection conn = connections.get(p.getUniqueId());
                    if (conn == null) continue;
                    voiceServer.send(conn, new NetworkMessage(pkt));
                }
                // Pacing real-time 20ms
                long target = start + (long) (f + 1) * FRAME_NS;
                long sleepNs = target - System.nanoTime();
                if (sleepNs > 0) {
                    try {
                        Thread.sleep(sleepNs / 1_000_000, (int) (sleepNs % 1_000_000));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            handle.markStopped();
        });
        t.start();
        return handle;
    }

    /** Resolve player target list dari argumen command. */
    public static List<Player> resolveTargets(String scope, String arg) {
        return switch (scope.toLowerCase()) {
            case "all" -> List.copyOf(Bukkit.getOnlinePlayers());
            case "world" -> {
                var w = Bukkit.getWorld(arg);
                yield w != null ? List.copyOf(w.getPlayers()) : List.of();
            }
            case "player" -> {
                Player p = Bukkit.getPlayerExact(arg);
                yield p != null ? List.of(p) : List.of();
            }
            default -> List.of();
        };
    }
}
