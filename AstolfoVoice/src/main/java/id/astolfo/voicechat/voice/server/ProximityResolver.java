package id.astolfo.voicechat.voice.server;

import id.astolfo.voicechat.audio.DspProcessor;
import id.astolfo.voicechat.audio.NoiseSuppressor;
import id.astolfo.voicechat.audio.OpusManager;
import id.astolfo.voicechat.config.AstolfoConfig;
import id.astolfo.voicechat.physics.SoundPhysicsEngine;
import id.astolfo.voicechat.voice.common.AudioUtils;
import id.astolfo.voicechat.voice.common.MicPacket;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ProximityResolver - routing mic packet + sound physics advanced + Tier 2 DSP +
 * noise cancellation server-side + private channel routing.
 *
 * Urutan routing per mic packet:
 *  0) Mute gate + decode Opus (sekali) + noise cancellation per-sender (sekali,
 *     bukan per-listener - hemat CPU) bila Tier 2 aktif di world sender.
 *  1) Group sound (bila sender di grup) -> anggota grup (audio NC-clean bila ada).
 *  2) Private channel (bila sender di channel privat) -> anggota lain channel,
 *     jarak tak terbatas antar anggota (distance besar FINITE, bukan Float.MAX_VALUE
 *     yang bikin client SVC NaN/overflow). Bila audibleNearby=true, lanjut proximity.
 *  3) Proximity broadcast ke world, terapkan sound physics (cache + smoothing
 *     per pasangan via pairKey).
 *
 * Tier 1 (default): SoundPath -> modulasi distance + whisper flag.
 * Tier 2 (opt-in per world): decode -> NC -> DspProcessor -> re-encode
 * ("bergema/mendam beneran" + suara bersih).
 * Dynamic range RMS (bisik->teriak) lewat decode opus.
 *
 * Engine NC: SPECTRAL (NoiseSuppressor pure-Java, default), GATE (noise gate
 * ringan), RNNOISE (native, roadmap - otomatis fallback ke SPECTRAL + log sekali).
 */
public final class ProximityResolver {

    private final AstolfoConfig config;
    private final GroupManager groupManager;
    private final SoundPhysicsEngine physics;
    private final OpusManager opus;
    private final ConcurrentHashMap<String, DspProcessor> dspStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, NoiseSuppressor> suppressors = new ConcurrentHashMap<>();
    private final AtomicBoolean rnnoiseWarned = new AtomicBoolean(false);

    public ProximityResolver(AstolfoConfig config, GroupManager groupManager, OpusManager opus) {
        this.config = config;
        this.groupManager = groupManager;
        this.opus = opus;
        this.physics = new SoundPhysicsEngine(config);
    }

    public void processMic(Player sender, MicPacket mic, ServerVoiceEvents server, long timestamp) {
        UUID senderUuid = sender.getUniqueId();

        // Mute gate: sender mute tidak mengirim suara ke siapapun (server-side).
        if (MuteHolder.isMuted(senderUuid)) {
            return;
        }

        boolean tier2 = isTier2(sender.getWorld().getName());
        boolean ncWanted = tier2 && config.noiseCancellationEnabled();

        // Decode Opus sekali: dipakai dynamic range (RMS) + NC + Tier 2 bake.
        double loudnessDb = -128;
        boolean hasDecoded = false;
        short[] decodedPcm = null;
        if ((config.dynamicRangeEnabled() || ncWanted) && opus != null) {
            decodedPcm = opus.decode(mic.getOpusData());
            if (decodedPcm != null) {
                loudnessDb = AudioUtils.getRmsAudioLevel(decodedPcm);
                hasDecoded = true;
            }
        }
        DynamicRange dr = computeDynamicRange(loudnessDb, mic.isWhispering(), hasDecoded, sender);

        // Noise cancellation per-sender (sekali per packet). skip_silent_frames:
        // frame di bawah threshold dilewatkan apa adanya (hemat CPU).
        short[] cleanPcm = decodedPcm;
        byte[] cleanOpus = null; // encode sekali, reuse untuk listener tanpa DSP per-pasangan
        boolean ncApplied = false;
        if (ncWanted && hasDecoded
                && !(config.noiseSkipSilentFrames() && loudnessDb <= config.noiseSilentThresholdDb())) {
            short[] processed = applyNoiseCancellation(senderUuid, decodedPcm);
            if (processed != null) {
                cleanPcm = processed;
                cleanOpus = opus.encode(processed);
                ncApplied = cleanOpus != null && cleanOpus.length > 0;
            }
        }
        byte[] baseOpus = ncApplied ? cleanOpus : mic.getOpusData();
        short[] basePcm = ncApplied ? cleanPcm : decodedPcm;

        // 1) Group sound
        UUID groupId = groupManager.getGroupOf(senderUuid);
        if (groupId != null) {
            List<UUID> members = groupManager.getMembers(groupId);
            for (UUID member : members) {
                if (member.equals(senderUuid)) continue;
                Player listener = Bukkit.getPlayer(member);
                if (listener == null) continue;
                // Bypass physics: distance besar FINITE (bukan voiceRange) supaya grup jernih
                // tanpa attenuasi proximity, kaya SVC asli. whisper grup selalu false.
                float distance = config.bypassPhysicsForGroups()
                        ? (float) Math.min(config.maxPlayerRangeOverride(), 1_000_000d)
                        : dr.range;
                boolean groupWhisper = config.bypassPhysicsForGroups() ? false : dr.whisper;
                server.sendPlayerSound(listener, senderUuid, baseOpus, groupWhisper, distance, null);
            }
            return;
        }

        // 2) Private channel
        var pcOptions = PrivateChannelRegistry.optionsOf(senderUuid);
        List<UUID> pcMembers = PrivateChannelRegistry.membersOf(senderUuid);
        if (pcMembers != null && pcOptions != null) {
            // Distance besar FINITE (bukan Float.MAX_VALUE) supaya client SVC tidak
            // overflow/NaN. maxPlayerRangeOverride default 1_000_000 -> attenuation ~0.
            float privateDistance = (float) Math.min(config.maxPlayerRangeOverride(), 1_000_000d);
            for (UUID member : pcMembers) {
                if (member.equals(senderUuid)) continue;
                Player listener = Bukkit.getPlayer(member);
                if (listener == null) continue;
                server.sendPlayerSound(listener, senderUuid, baseOpus, dr.whisper, privateDistance, "astolfo_private");
            }
            // Bila audibleNearby=false -> sekitar TIDAK dengar: return (skip proximity).
            if (!pcOptions.isAudibleNearby()) {
                return;
            }
        }

        Location senderLoc = sender.getEyeLocation();
        if (senderLoc.getWorld() == null) return;

        // Broadcast range: untuk private channel audibleNearby, batasi ke range channel.
        double broadcastRange = config.broadcastRange() < 0 ? dr.range + 1 : config.broadcastRange();
        broadcastRange = Math.max(broadcastRange, config.shoutRange() + 8);
        if (pcOptions != null && pcOptions.isAudibleNearby() && pcOptions.getRange() > 0) {
            broadcastRange = Math.min(broadcastRange, pcOptions.getRange());
        }

        boolean debugRay = config.debugEnabled() && config.logRaytrace();
        boolean debugDsp = config.debugEnabled() && config.logDspTiming();

        // 3) Proximity broadcast ke world
        for (Player listener : senderLoc.getWorld().getPlayers()) {
            if (listener.getUniqueId().equals(senderUuid)) continue;
            if (!server.isCompatible(listener)) continue;
            // Spectator gate (#9): bila spectator_interaction=false, spectator gak dengar/bicara ke player biasa.
            if (!config.spectatorInteraction()) {
                boolean senderSpec = sender.getGameMode() == org.bukkit.GameMode.SPECTATOR;
                boolean listenerSpec = listener.getGameMode() == org.bukkit.GameMode.SPECTATOR;
                if (senderSpec || listenerSpec) continue;
            }
            // skip anggota channel yang sudah dapat paket privat di atas (dapat dua kali = echo).
            if (pcMembers != null && pcMembers.contains(listener.getUniqueId())) continue;
            Location listenerLoc = listener.getEyeLocation();
            double dist = senderLoc.distance(listenerLoc);
            if (dist > broadcastRange) continue;

            float effectiveDistance = (float) dr.range;
            boolean effectiveWhisper = dr.whisper;
            byte[] opusToSend = baseOpus;

            if (config.soundPhysicsEnabled() && config.raytraceEnabled()) {
                String pairKey = senderUuid + ">" + listener.getUniqueId();
                SoundPhysicsEngine.SoundPath path =
                        physics.compute(senderLoc, listenerLoc, dist, senderLoc.getWorld(), pairKey);
                if (debugRay) {
                    Bukkit.getLogger().info(String.format(Locale.ROOT,
                            "[Astolfo] ray %s dist=%.1f gain=%.2f lowpass=%.0f reverb=%.2f bias=%.1f audible=%b",
                            pairKey, dist, path.gain, path.lowpassHz, path.reverb, path.distanceBias, path.audible));
                }
                if (!path.audible) continue;
                effectiveDistance += path.distanceBias;
                if (path.gain > 0f && path.gain < 1f) {
                    // gain kecil -> jarak efektif bertambah (terdengar lebih jauh/redam).
                    effectiveDistance += (float) (dist * (1.0 / path.gain - 1.0) * 0.5);
                }
                if (path.muffled && effectiveDistance > dr.range) {
                    effectiveWhisper = effectiveWhisper || path.lowpassHz < 1200f;
                }

                if (tier2 && hasDecoded && basePcm != null
                        && (path.lowpassHz < AudioUtils.SAMPLE_RATE / 2f || path.reverb > 0f || path.muffled)) {
                    long t0 = debugDsp ? System.nanoTime() : 0L;
                    short[] baked = basePcm.clone();
                    DspProcessor dsp = dspStates.computeIfAbsent(pairKey, k -> new DspProcessor());
                    float cutoff = path.lowpassHz < AudioUtils.SAMPLE_RATE / 2f ? path.lowpassHz : 0f;
                    if (cutoff <= 0f && path.muffled) {
                        // muffled tanpa cutoff spesifik dari physics -> default muffle config.
                        cutoff = (float) config.muffleLowpassHz();
                    }
                    // reverb_gain menskala density fisika ke amount DSP (default 0.25 -> ~0.5x density).
                    float reverbAmount = (float) Math.min(0.6, path.reverb * config.reverbGain() * 2.0);
                    dsp.process(baked, cutoff, reverbAmount, (float) config.reverbDecaySeconds(), false, 0f);
                    byte[] reencoded = opus.encode(baked);
                    if (reencoded != null && reencoded.length > 0) {
                        opusToSend = reencoded;
                    }
                    if (debugDsp) {
                        Bukkit.getLogger().info(String.format(Locale.ROOT,
                                "[Astolfo] dsp %s cutoff=%.0f reverb=%.2f took=%.2fms",
                                pairKey, cutoff, reverbAmount, (System.nanoTime() - t0) / 1e6));
                    }
                }
            }
            server.sendPlayerSound(listener, senderUuid, opusToSend, effectiveWhisper, effectiveDistance, null);
        }
    }

    /**
     * Terapkan engine NC dari config ke satu frame PCM. Return frame baru
     * (SPECTRAL punya delay internal ~5ms) atau frame yang sama (GATE, in-place copy).
     */
    private short[] applyNoiseCancellation(UUID senderUuid, short[] pcm) {
        String engine = config.noiseEngine().toUpperCase(Locale.ROOT);
        if ("RNNOISE".equals(engine)) {
            if (rnnoiseWarned.compareAndSet(false, true)) {
                Bukkit.getLogger().warning("[Astolfo] noise_cancellation.engine=RNNOISE belum tersedia "
                        + "(native lib roadmap) - fallback otomatis ke SPECTRAL.");
            }
            engine = "SPECTRAL";
        }
        double strength = Math.max(0.0, Math.min(1.0, config.noiseStrength()));
        if ("GATE".equals(engine)) {
            // gate ringan: pakai DspProcessor per-sender khusus NC (state envelope kontinu).
            short[] copy = pcm.clone();
            DspProcessor dsp = dspStates.computeIfAbsent("nc:" + senderUuid, k -> new DspProcessor());
            dsp.process(copy, 0f, 0f, true, (float) config.noiseSilentThresholdDb());
            return copy;
        }
        // SPECTRAL (default): NoiseSuppressor streaming per-sender.
        NoiseSuppressor sup = suppressors.computeIfAbsent(senderUuid, k -> new NoiseSuppressor());
        return sup.process(pcm, strength);
    }

    private boolean isTier2(String world) {
        if (!"TIER_2".equalsIgnoreCase(config.soundPhysicsTier())) return false;
        List<String> worlds = config.tier2Worlds();
        return worlds == null || worlds.isEmpty() || worlds.contains(world);
    }

    /** Bersihkan semua state per-player: DSP pasangan, suppressor NC, cache physics. */
    public void clearDspFor(UUID player) {
        dspStates.entrySet().removeIf(e -> e.getKey().startsWith(player + ">")
                || e.getKey().endsWith(">" + player)
                || e.getKey().equals("nc:" + player));
        suppressors.remove(player);
        physics.clearFor(player);
    }

    private static final class DynamicRange {
        final float range;
        final boolean whisper;
        DynamicRange(float range, boolean whisper) {
            this.range = range;
            this.whisper = whisper;
        }
    }

    private DynamicRange computeDynamicRange(double loudnessDb, boolean micWhisper, boolean hasDecoded, Player sender) {
        double baseRange = config.voiceRange();
        if (!config.dynamicRangeEnabled() || !hasDecoded) {
            double r = micWhisper ? config.whisperRange() : baseRange;
            return new DynamicRange((float) r, micWhisper);
        }
        if (loudnessDb <= config.noiseSilentThresholdDb()) {
            return new DynamicRange((float) config.whisperRange(), true);
        }
        double t = (loudnessDb - config.loudnessWhisperDb()) / (config.loudnessShoutDb() - config.loudnessWhisperDb());
        t = clamp(t, 0, 1);
        double range;
        boolean whisper;
        if (t < 0.5) {
            double u = t * 2.0;
            range = lerp(config.whisperRange(), baseRange, u);
            whisper = u < config.whisperIconThresholdFactor() / 2.0
                    || range < config.whisperRange() * config.whisperIconThresholdFactor();
        } else {
            double u = (t - 0.5) * 2.0;
            boolean canShout = sender.hasPermission(config.shoutPermission());
            range = canShout ? lerp(baseRange, config.shoutRange(), u) : baseRange;
            whisper = false;
        }
        double override = AstolfoOverride.get(sender.getUniqueId());
        if (override > 0) {
            range = Math.min(range, override);
        }
        return new DynamicRange((float) range, whisper);
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
