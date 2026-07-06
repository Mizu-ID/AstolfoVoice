package id.astolfo.voicechat.integration;

import id.astolfo.voicechat.AstolfoVoice;
import id.astolfo.voicechat.api.VoiceStatus;
import id.astolfo.voicechat.api.impl.AstolfoApiImpl;
import id.astolfo.voicechat.config.AstolfoConfig;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * PlaceholderAPI expansion untuk AstolfoVoice.
 *
 * Server-wide (tidak butuh player):
 *   %astolfo_version%       versi plugin           %astolfo_compat%        versi protokol SVC
 *   %astolfo_port%          port UDP voice         %astolfo_active%        playback aktif
 *   %astolfo_playlists%     jumlah playlist        %astolfo_groups%        jumlah grup
 *   %astolfo_tracked%       player ter-track       %astolfo_physics%       sound physics on/off
 *   %astolfo_physics_tier%  TIER_1 / TIER_2        %astolfo_nc%            noise cancellation on/off
 *   %astolfo_nc_engine%     SPECTRAL/GATE/RNNOISE  %astolfo_dynamic_range% range dinamis on/off
 *   %astolfo_voice_range%   range normal (config)  %astolfo_whisper_range% range bisik (config)
 *   %astolfo_shout_range%   range teriak (config)
 *
 * Per-player:
 *   %astolfo_connected%     %astolfo_disconnected%  %astolfo_status%
 *   %astolfo_range%         %astolfo_range_int%     %astolfo_whisper%
 *   %astolfo_muted%         %astolfo_disabled%      %astolfo_group%
 *   %astolfo_group_size%    %astolfo_world%         %astolfo_name%
 *   %astolfo_shout%
 */
public final class AstolfoExpansion extends PlaceholderExpansion {

    private final AstolfoVoice plugin;
    private final AstolfoApiImpl api;

    public AstolfoExpansion(AstolfoVoice plugin, AstolfoApiImpl api) {
        this.plugin = plugin;
        this.api = api;
    }

    @Override
    public @NotNull String getIdentifier() { return "astolfo"; }

    @Override
    public @NotNull String getAuthor() { return "Mizu"; }

    @Override
    public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }

    @Override
    public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String p = params.toLowerCase(Locale.ROOT);
        AstolfoConfig cfg = plugin.getAstolfoConfig();

        // server-wide placeholders (tidak butuh player)
        switch (p) {
            case "version": return plugin.getDescription().getVersion();
            case "compat":  return String.valueOf(AstolfoVoice.COMPATIBILITY_VERSION);
            case "port":    return cfg != null ? String.valueOf(cfg.port()) : "-";
            case "active":  return String.valueOf(plugin.getAudioEngine() != null ? plugin.getAudioEngine().activeCount() : 0);
            case "playlists": return String.valueOf(plugin.getPlaylists() != null ? plugin.getPlaylists().all().size() : 0);
            case "groups":
                return plugin.getVoiceEvents() != null
                        ? String.valueOf(plugin.getVoiceEvents().getGroupManager().allGroups().size()) : "0";
            case "tracked":
                return plugin.getVoiceEvents() != null
                        ? String.valueOf(plugin.getVoiceEvents().getPlayerStateManager().allStates().size()) : "0";
            case "physics":       return cfg != null ? String.valueOf(cfg.soundPhysicsEnabled()) : "false";
            case "physics_tier":  return cfg != null ? cfg.soundPhysicsTier().toUpperCase(Locale.ROOT) : "-";
            case "nc":            return cfg != null ? String.valueOf(cfg.noiseCancellationEnabled()) : "false";
            case "nc_engine":     return cfg != null ? cfg.noiseEngine().toUpperCase(Locale.ROOT) : "-";
            case "dynamic_range": return cfg != null ? String.valueOf(cfg.dynamicRangeEnabled()) : "false";
            case "voice_range":   return cfg != null ? fmt(cfg.voiceRange()) : "-";
            case "whisper_range": return cfg != null ? fmt(cfg.whisperRange()) : "-";
            case "shout_range":   return cfg != null ? fmt(cfg.shoutRange()) : "-";
        }

        if (player == null) return "";
        VoiceStatus st = api.getPlayerStatus(player.getUniqueId());
        if (st == null) return "";
        switch (p) {
            case "connected":    return String.valueOf(st.isConnected());
            case "disconnected": return String.valueOf(!st.isConnected());
            case "range":        return String.format(Locale.ROOT, "%.1f", st.getRange());
            case "range_int":    return String.valueOf((int) Math.round(st.getRange()));
            case "status":
                if (!st.isConnected()) return "disconnected";
                if (st.isDisabled()) return "disabled";
                if (st.isMuted()) return "muted";
                return st.isWhispering() ? "whisper" : "normal";
            case "whisper":      return String.valueOf(st.isWhispering());
            case "disabled":     return String.valueOf(st.isDisabled());
            case "muted":        return String.valueOf(st.isMuted());
            case "group":        return st.getGroup() != null ? st.getGroup().toString() : "-";
            case "group_size":   return String.valueOf(groupSize(player.getUniqueId()));
            case "world":        return st.getWorld() != null ? st.getWorld() : "-";
            case "name":         return st.getName() != null ? st.getName() : "-";
            case "shout":
                if (player.isOnline() && player.getPlayer() != null) {
                    String perm = cfg != null ? cfg.shoutPermission() : "astolfo.shout";
                    return String.valueOf(player.getPlayer().hasPermission(perm));
                }
                return "true";
            default: return null;
        }
    }

    /** Jumlah anggota grup player (0 bila tidak di grup / voiceEvents belum siap). */
    private int groupSize(UUID uuid) {
        if (plugin.getVoiceEvents() == null) return 0;
        var gm = plugin.getVoiceEvents().getGroupManager();
        UUID groupId = gm.getGroupOf(uuid);
        if (groupId == null) return 0;
        List<UUID> members = gm.getMembers(groupId);
        return members == null ? 0 : members.size();
    }

    private static String fmt(double v) {
        return v == Math.floor(v) && !Double.isInfinite(v)
                ? String.valueOf((long) v)
                : String.format(Locale.ROOT, "%.1f", v);
    }
}
