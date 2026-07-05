package id.astolfo.voicechat.audio;

import id.astolfo.voicechat.api.PlaybackOptions;
import id.astolfo.voicechat.api.impl.PlaybackHandleImpl;
import id.astolfo.voicechat.voice.common.AudioUtils;
import id.astolfo.voicechat.voice.common.ClientConnection;
import id.astolfo.voicechat.voice.common.LocationSoundPacket;
import id.astolfo.voicechat.voice.common.NetworkMessage;
import id.astolfo.voicechat.voice.common.PlayerSoundPacket;
import id.astolfo.voicechat.voice.server.VoiceServer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * StreamingAudioPlayer - mutar PCM 48k mono ke listener via Opus, paced 20ms/frame.
 *
 * Mode playback:
 *  - play()            : PlayerSoundPacket (non-locational), sequenceNumber < 0 = audio channel.
 *  - playLocation()    : LocationSoundPacket (posisi dunia) per-listener, distance = attenuasi
 *                        jarak per-listener dari posisi sumber (v0.2.1). Client SVC posisikan
 *                        suara di koordinat itu, jadi terdengar "dari arah X" + meredam sesuai
 *                        jarak listener ke sumber.
 *
 * Sound effect (v0.2.1):
 *  - pitch: rate-based shift (Resampler.changePitch) sebelum encode.
 *  - preset: DspProcessor.applyPreset (PHONE/RADIO/MEGA/CAVE) per frame.
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

    /** Mulai playback non-locational ke daftar player. Return handle. */
    public PlaybackHandleImpl play(List<Player> targets, short[] pcm48k, PlaybackOptions options, String file,
                                   UUID sourceChannelId) {
        return stream(targets, pcm48k, options, file, sourceChannelId, null);
    }

    /**
     * Mulai playback LOCATIONAL ke daftar player. Suara diposisikan di `source` (koordinat
     * dunia), tiap listener dapat distance berbeda sesuai jarak eye ke source -> client SVC
     * attenuate & posisi kanan/kiri natural. Return handle.
     */
    public PlaybackHandleImpl playLocation(List<Player> targets, short[] pcm48k, PlaybackOptions options,
                                           String file, UUID sourceChannelId, Location source) {
        return stream(targets, pcm48k, options, file, sourceChannelId, source);
    }

    private PlaybackHandleImpl stream(List<Player> targets, short[] pcm48k, PlaybackOptions options, String file,
                                      UUID sourceChannelId, Location source) {
        PlaybackHandleImpl handle = new PlaybackHandleImpl(file);
        Thread t = Thread.ofVirtual().name("astolfo-play-", 0).unstarted(() -> {
            // Pre-process sekali: pitch + gain. Preset diterapkan per-frame (stateful DSP).
            short[] data = Resampler.alignToFrame(pcm48k);
            if (options.getPitch() != 1.0) {
                data = Resampler.changePitch(data, options.getPitch());
                data = Resampler.alignToFrame(data);
            }
            data = Resampler.applyGain(data, options.getVolume());
            int frames = data.length / AudioUtils.FRAME_SIZE;
            UUID channelId = sourceChannelId != null ? sourceChannelId
                    : new UUID(0L, CHANNEL_ID_SEQ.incrementAndGet());
            long start = System.nanoTime();
            float baseDistance = (float) options.getDistance();
            DspProcessor presetDsp = options.getPreset() != PlaybackOptions.Preset.NONE ? new DspProcessor() : null;
            boolean locational = source != null && source.getWorld() != null;
            for (int f = 0; f < frames && handle.isRunning(); f++) {
                short[] frame = new short[AudioUtils.FRAME_SIZE];
                System.arraycopy(data, f * AudioUtils.FRAME_SIZE, frame, 0, AudioUtils.FRAME_SIZE);
                if (presetDsp != null) {
                    presetDsp.applyPreset(frame, options.getPreset());
                }
                byte[] opusData = opus.encode(frame);
                if (opusData.length == 0) continue;
                for (Player p : targets) {
                    if (!p.isOnline()) continue;
                    ClientConnection conn = connections.get(p.getUniqueId());
                    if (conn == null) continue;
                    if (locational) {
                        // distance per-listener: attenuasi sesuai jarak listener ke source.
                        float dist = baseDistance;
                        try {
                            double d = p.getEyeLocation().distance(source);
                            // client SVC: distance = max attenuation radius; bila d > distance -> silent.
                            // Pakai baseDistance sebagai radius dengar, plus sedikit toleransi.
                            dist = (float) Math.max(baseDistance, d + 0.5);
                        } catch (Throwable ignored) {
                            // world beda / unload -> skip listener ini.
                            continue;
                        }
                        if (!sameWorld(p, source)) continue;
                        LocationSoundPacket pkt = new LocationSoundPacket(
                                channelId, channelId, source.getX(), source.getY(), source.getZ(),
                                opusData, -1L, dist, options.getCategory());
                        voiceServer.send(conn, new NetworkMessage(pkt));
                    } else {
                        PlayerSoundPacket pkt = new PlayerSoundPacket(channelId, channelId, opusData, -1L, false, baseDistance, options.getCategory());
                        voiceServer.send(conn, new NetworkMessage(pkt));
                    }
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

    private static boolean sameWorld(Player p, Location source) {
        return p.getWorld() != null && p.getWorld().equals(source.getWorld());
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
