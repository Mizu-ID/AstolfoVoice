package id.astolfo.voicechat.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * AstolfoConfig — pembungkus config.yml (SnakeYAML bawaan Paper).
 * Memuat semua section yang dipakai server sesuai IMPLEMENTATION_PLAN §C.
 */
public final class AstolfoConfig {

    private final YamlConfiguration yaml;

    public AstolfoConfig(YamlConfiguration yaml) {
        this.yaml = yaml;
    }

    public static AstolfoConfig load(File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        if (file.exists()) {
            try {
                yaml.load(file);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load config.yml: " + e.getMessage(), e);
            }
        }
        return new AstolfoConfig(yaml);
    }

    public void saveDefaultIfMissing(File file, InputStream defaults) {
        if (file.exists()) return;
        try {
            file.getParentFile().mkdirs();
            Files.copy(defaults, file.toPath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save default config.yml", e);
        }
    }

    private ConfigurationSection sec(String path) {
        return yaml.getConfigurationSection(path);
    }

    private String s(String path, String def) {
        return yaml.getString(path, def);
    }

    private int i(String path, int def) {
        return yaml.getInt(path, def);
    }

    private double d(String path, double def) {
        return yaml.getDouble(path, def);
    }

    private boolean b(String path, boolean def) {
        return yaml.getBoolean(path, def);
    }

    // ---- network ----
    public int port() {
        return i("network.port", 24454);
    }

    public String bindAddress() {
        return s("network.bind_address", "");
    }

    public String voiceHost() {
        return s("network.voice_host", "");
    }

    public int mtuSize() {
        return i("network.mtu_size", 1275);
    }

    public int tcpRateLimit() {
        return i("network.tcp_rate_limit", 16);
    }

    public int keepAlive() {
        return i("network.keep_alive", 1000);
    }

    public long loginTimeout() {
        return i("network.login_timeout", 10000);
    }

    public boolean allowPings() {
        return b("network.allow_pings", true);
    }

    // ---- audio_quality ----
    public String codecName() {
        return s("audio_quality.codec", "VOIP");
    }

    public int opusBitrate() {
        return i("audio_quality.opus_bitrate", 64);
    }

    public boolean allowRecording() {
        return b("audio_quality.allow_recording", true);
    }

    // ---- voice_range ----
    public double voiceRange() {
        return d("voice_range.voice_range", 48D);
    }

    public double whisperRange() {
        return d("voice_range.whisper_range", 4D);
    }

    public double shoutRange() {
        return d("voice_range.shout_range", 96D);
    }

    public String shoutPermission() {
        return s("voice_range.shout_permission", "astolfo.shout");
    }

    public boolean dynamicRangeEnabled() {
        return b("voice_range.dynamic_range_enabled", true);
    }

    public double loudnessWhisperDb() {
        return d("voice_range.loudness_whisper_db", -45D);
    }

    public double loudnessShoutDb() {
        return d("voice_range.loudness_shout_db", -15D);
    }

    public double broadcastRange() {
        return d("voice_range.broadcast_range", -1D);
    }

    public double maxPlayerRangeOverride() {
        return d("voice_range.max_player_range_override", 1_000_000D);
    }

    public double whisperIconThresholdFactor() {
        return d("voice_range.whisper_icon_threshold_factor", 1.5D);
    }

    // ---- groups / spectator / force ----
    public boolean groupsEnabled() {
        return b("groups.enable_groups", true);
    }

    public boolean spectatorInteraction() {
        return b("spectator.spectator_interaction", false);
    }

    public boolean spectatorPlayerPossession() {
        return b("spectator.spectator_player_possession", false);
    }

    public boolean forceVoiceChat() {
        return b("force_voice_chat", false);
    }

    // ---- noise_cancellation ----
    public boolean noiseCancellationEnabled() {
        return b("noise_cancellation.enabled", false);
    }

    public String noiseEngine() {
        return s("noise_cancellation.engine", "RNNOISE");
    }

    public boolean noiseFallbackToSpeex() {
        return b("noise_cancellation.fallback_to_speex", true);
    }

    public boolean noiseSkipSilentFrames() {
        return b("noise_cancellation.skip_silent_frames", true);
    }

    public double noiseSilentThresholdDb() {
        return d("noise_cancellation.silent_threshold_db", -50D);
    }

    // ---- sound_physics ----
    public boolean soundPhysicsEnabled() {
        return b("sound_physics.enabled", true);
    }

    public String soundPhysicsTier() {
        return s("sound_physics.tier", "TIER_1");
    }

    public boolean raytraceEnabled() {
        return b("sound_physics.raytrace", true);
    }

    public boolean asyncRaytrace() {
        return b("sound_physics.async_raytrace", false);
    }

    public int maxOcclusionBlocks() {
        return i("sound_physics.max_occlusion_blocks", 8);
    }

    public double occlusionPenaltyPerBlock() {
        return d("sound_physics.occlusion_penalty_per_block", 6D);
    }

    public int reverbProbeRadius() {
        return i("sound_physics.reverb_probe_radius", 6);
    }

    public double reverbPenalty() {
        return d("sound_physics.reverb_penalty", 4D);
    }

    public int cacheTicks() {
        return i("sound_physics.cache_ticks", 2);
    }

    public double muffleLowpassHz() {
        return d("sound_physics.muffle_lowpass_hz", 1200D);
    }

    public double reverbGain() {
        return d("sound_physics.reverb_gain", 0.25D);
    }

    public double reverbDecaySeconds() {
        return d("sound_physics.reverb_decay_seconds", 1.2D);
    }

    public List<String> tier2Worlds() {
        List<String> list = yaml.getStringList("sound_physics.tier2_worlds");
        return list == null ? new ArrayList<>() : list;
    }

    public boolean bypassPhysicsForGroups() {
        return b("sound_physics.bypass_for_groups", true);
    }

    // ---- audio playback ----
    public boolean audioEnabled() {
        return b("audio.enabled", true);
    }

    public String audioFolder() {
        return s("audio.folder", "audio");
    }

    public double audioDefaultVolume() {
        return d("audio.default_volume", 1.0D);
    }

    public double audioDefaultDistance() {
        return d("audio.default_distance", 32D);
    }

    public int maxConcurrentPlaybacks() {
        return i("audio.max_concurrent_playbacks", 64);
    }

    public String resampleQuality() {
        return s("audio.resample_quality", "HIGH");
    }

    public boolean audioNormalize() {
        return b("audio.normalize", true);
    }

    // ---- performance ----
    public boolean virtualThreads() {
        return b("performance.virtual_threads", true);
    }

    public int dspPoolSize() {
        return i("performance.dsp_pool_size", 0);
    }

    public int opusPoolSize() {
        return i("performance.opus_pool_size", 32);
    }

    public boolean dropExpiredPackets() {
        return b("performance.drop_expired_packets", true);
    }

    public long overloadLogCooldownMs() {
        return i("performance.overload_log_cooldown_ms", 5000);
    }

    public int dspQueueCapacity() {
        return i("performance.dsp_queue_capacity", 1024);
    }

    // ---- compatibility ----
    public int defaultCompatibilityVersion() {
        return i("compatibility.default_compatibility_version", 20);
    }

    public boolean allowLegacyAdapters() {
        return b("compatibility.allow_legacy_adapters", true);
    }

    public boolean rejectUnknownVersion() {
        return b("compatibility.reject_unknown_version", false);
    }

    // ---- debug ----
    public boolean debugEnabled() {
        return b("debug.enabled", false);
    }

    public boolean logPackets() {
        return b("debug.log_packets", false);
    }
}
