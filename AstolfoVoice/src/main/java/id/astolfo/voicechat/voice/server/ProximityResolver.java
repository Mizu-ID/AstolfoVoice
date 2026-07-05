package id.astolfo.voicechat.voice.server;

import id.astolfo.voicechat.audio.DspProcessor;
import id.astolfo.voicechat.audio.OpusManager;
import id.astolfo.voicechat.config.AstolfoConfig;
import id.astolfo.voicechat.physics.SoundPhysicsEngine;
import id.astolfo.voicechat.voice.common.AudioUtils;
import id.astolfo.voicechat.voice.common.MicPacket;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ProximityResolver — routing mic packet + sound physics advanced + Tier 2 DSP +
 * private channel routing.
 *
 * Urutan routing per mic packet:
 *  1) Group sound (bila sender di grup) → anggota grup.
 *  2) Private channel (bila sender di channel privat) → anggota lain channel,
 *     jarak tak terbatas antar anggota (distance besar FINITE, bukan Float.MAX_VALUE
 *     yang bikin client SVC NaN/overflow). Bila audibleNearby=true, lanjut proximity.
 *  3) Proximity broadcast ke world, terapkan sound physics.
 *
 * Tier 1 (default): SoundPath → modulasi distance + whisper flag.
 * Tier 2 (opt-in per world): decode→DspProcessor→re-encode ("bergema/mendam beneran").
 * Dynamic range RMS (bisik→teriak) lewat decode opus.
 */
public final class ProximityResolver {

    private final AstolfoConfig config;
    private final GroupManager groupManager;
    private final SoundPhysicsEngine physics;
    private final OpusManager opus;
    private final ConcurrentHashMap<String, DspProcessor> dspStates = new ConcurrentHashMap<>();

    public ProximityResolver(AstolfoConfig config, GroupManager groupManager, OpusManager opus) {
        this.config = config;
        this.groupManager = groupManager;
        this.opus = opus;
        this.physics = new SoundPhysicsEngine(config);
    }

    public void processMic(Player sender, MicPacket mic, ServerVoiceEvents server, long timestamp) {
        UUID senderUuid = sender.getUniqueId();

        // Dynamic range: decode opus → RMS dB → t.
        double loudnessDb = -128;
        boolean hasDecoded = false;
        short[] decodedPcm = null;
        if (config.dynamicRangeEnabled() && opus != null) {
            decodedPcm = opus.decode(mic.getOpusData());
            if (decodedPcm != null) {
                loudnessDb = AudioUtils.getRmsAudioLevel(decodedPcm);
                hasDecoded = true;
            }
        }
        DynamicRange dr = computeDynamicRange(loudnessDb, mic.isWhispering(), hasDecoded, sender);

        // 1) Group sound
        UUID groupId = groupManager.getGroupOf(senderUuid);
        if (groupId != null) {
            List<UUID> members = groupManager.getMembers(groupId);
            for (UUID member : members) {
                if (member.equals(senderUuid)) continue;
                Player listener = org.bukkit.Bukkit.getPlayer(member);
                if (listener == null) continue;
                float distance = config.bypassPhysicsForGroups() ? (float) config.voiceRange() : dr.range;
                server.sendPlayerSound(listener, senderUuid, mic.getOpusData(), dr.whisper, distance, null);
            }
            return;
        }

        // 2) Private channel
        var pcOptions = PrivateChannelRegistry.optionsOf(senderUuid);
        List<UUID> pcMembers = PrivateChannelRegistry.membersOf(senderUuid);
        if (pcMembers != null && pcOptions != null) {
            // Distance besar FINITE (bukan Float.MAX_VALUE) supaya client SVC tidak
            // overflow/NaN. maxPlayerRangeOverride default 1_000_000 → attenuation ~0.
            float privateDistance = (float) Math.min(config.maxPlayerRangeOverride(), 1_000_000d);
            for (UUID member : pcMembers) {
                if (member.equals(senderUuid)) continue;
                Player listener = org.bukkit.Bukkit.getPlayer(member);
                if (listener == null) continue;
                server.sendPlayerSound(listener, senderUuid, mic.getOpusData(), dr.whisper, privateDistance, "astolfo_private");
            }
            // Bila audibleNearby=false → sekitar TIDAK dengar: return (skip proximity).
            if (!pcOptions.isAudibleNearby()) {
                return;
            }
        }

        Location senderLoc = sender.getEyeLocation();
        if (senderLoc.getWorld() == null) return;

        boolean tier2 = isTier2(senderLoc.getWorld().getName());
        // Broadcast range: untuk private channel audibleNearby, batasi ke range channel.
        double broadcastRange = config.broadcastRange() < 0 ? dr.range + 1 : config.broadcastRange();
        broadcastRange = Math.max(broadcastRange, config.shoutRange() + 8);
        if (pcOptions != null && pcOptions.isAudibleNearby() && pcOptions.getRange() > 0) {
            broadcastRange = Math.min(broadcastRange, pcOptions.getRange());
        }

        // 3) Proximity broadcast ke world
        for (Player listener : senderLoc.getWorld().getPlayers()) {
            if (listener.getUniqueId().equals(senderUuid)) continue;
            if (!server.isCompatible(listener)) continue;
            // skip anggota channel yang sudah dapat paket privat di atas (dapat dua kali = echo).
            if (pcMembers != null && pcMembers.contains(listener.getUniqueId())) continue;
            Location listenerLoc = listener.getEyeLocation();
            double dist = senderLoc.distance(listenerLoc);
            if (dist > broadcastRange) continue;

            float effectiveDistance = (float) dr.range;
            boolean effectiveWhisper = dr.whisper;
            byte[] opusToSend = mic.getOpusData();

            if (config.soundPhysicsEnabled() && config.raytraceEnabled()) {
                SoundPhysicsEngine.SoundPath path = physics.compute(senderLoc, listenerLoc, dist, senderLoc.getWorld());
                if (!path.audible) continue;
                effectiveDistance += path.distanceBias;
                if (path.gain > 0f && path.gain < 1f) {
                    // gain kecil → jarak efektif bertambah (terdengar lebih jauh/redam).
                    effectiveDistance += (float) (dist * (1.0 / path.gain - 1.0) * 0.5);
                }
                if (path.muffled && effectiveDistance > dr.range) {
                    effectiveWhisper = effectiveWhisper || path.lowpassHz < 1200f;
                }

                if (tier2 && hasDecoded && decodedPcm != null
                        && (path.lowpassHz < AudioUtils.SAMPLE_RATE / 2f || path.reverb > 0f || path.muffled)) {
                    short[] baked = decodedPcm.clone();
                    String key = senderUuid + ">" + listener.getUniqueId();
                    DspProcessor dsp = dspStates.computeIfAbsent(key, k -> new DspProcessor());
                    float cutoff = path.lowpassHz < AudioUtils.SAMPLE_RATE / 2f ? path.lowpassHz : 0f;
                    float reverb = path.reverb;
                    boolean gate = config.noiseCancellationEnabled() && config.noiseSkipSilentFrames();
                    float gateDb = (float) config.noiseSilentThresholdDb();
                    dsp.process(baked, cutoff, reverb, gate, gateDb);
                    byte[] reencoded = opus.encode(baked);
                    if (reencoded != null && reencoded.length > 0) {
                        opusToSend = reencoded;
                    }
                }
            }
            server.sendPlayerSound(listener, senderUuid, opusToSend, effectiveWhisper, effectiveDistance, null);
        }
    }

    private boolean isTier2(String world) {
        if (!"TIER_2".equalsIgnoreCase(config.soundPhysicsTier())) return false;
        List<String> worlds = config.tier2Worlds();
        return worlds == null || worlds.isEmpty() || worlds.contains(world);
    }

    public void clearDspFor(UUID player) {
        dspStates.entrySet().removeIf(e -> e.getKey().startsWith(player + ">") || e.getKey().endsWith(">" + player));
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
