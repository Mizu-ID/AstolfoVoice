package id.astolfo.voicechat.integration;

import id.astolfo.voicechat.AstolfoVoice;
import id.astolfo.voicechat.api.VoiceStatus;
import id.astolfo.voicechat.api.impl.AstolfoApiImpl;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI expansion untuk AstolfoVoice.
 * Placeholders:
 *   %astolfo_connected%   %astolfo_range%       %astolfo_status%
 *   %astolfo_group%       %astolfo_muted%       %astolfo_world%
 *   %astolfo_shout%       %astolfo_whisper%     %astolfo_disabled%
 *   %astolfo_disconnected%%astolfo_name%        %astolfo_version%
 *   %astolfo_compat%      %astolfo_active%      %astolfo_playlists%
 *   %astolfo_groups%      %astolfo_tracked%     %astolfo_range_int%
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
        String p = params.toLowerCase();
        // server-wide placeholders (tidak butuh player)
        switch (p) {
            case "version": return String.valueOf(AstolfoVoice.COMPATIBILITY_VERSION);
            case "compat":  return String.valueOf(AstolfoVoice.COMPATIBILITY_VERSION);
            case "active":  return String.valueOf(plugin.getAudioEngine() != null ? plugin.getAudioEngine().activeCount() : 0);
            case "playlists": return String.valueOf(plugin.getPlaylists() != null ? plugin.getPlaylists().all().size() : 0);
            case "groups":  return String.valueOf(plugin.getVoiceEvents().getGroupManager().allGroups().size());
            case "tracked": return String.valueOf(plugin.getVoiceEvents().getPlayerStateManager().allStates().size());
        }
        if (player == null) return "";
        VoiceStatus st = api.getPlayerStatus(player.getUniqueId());
        switch (p) {
            case "connected":    return String.valueOf(st.isConnected());
            case "disconnected": return String.valueOf(!st.isConnected());
            case "range":        return String.format(java.util.Locale.ROOT, "%.1f", st.getRange());
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
            case "world":        return st.getWorld() != null ? st.getWorld() : "-";
            case "name":         return st.getName() != null ? st.getName() : "-";
            case "shout":
                if (player.isOnline() && player.getPlayer() != null) {
                    return String.valueOf(player.getPlayer().hasPermission("astolfo.shout"));
                }
                return "true";
            default: return null;
        }
    }
}
