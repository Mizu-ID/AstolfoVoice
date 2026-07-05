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
 *   %astolfo_connected%   %astolfo_range%   %astolfo_status%
 *   %astolfo_group%       %astolfo_muted%   %astolfo_world%
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
    public @NotNull String getIdentifier() {
        return "astolfo";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Mizu";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        VoiceStatus status = api.getPlayerStatus(player.getUniqueId());
        switch (params.toLowerCase()) {
            case "connected":
                return String.valueOf(status.isConnected());
            case "range":
                return String.format(java.util.Locale.ROOT, "%.1f", status.getRange());
            case "status":
                if (!status.isConnected()) return "disconnected";
                if (status.isDisabled()) return "disabled";
                if (status.isMuted()) return "muted";
                return status.isWhispering() ? "whisper" : "normal";
            case "group":
                return status.getGroup() != null ? status.getGroup().toString() : "-";
            case "muted":
                return String.valueOf(status.isMuted());
            case "world":
                return status.getWorld() != null ? status.getWorld() : "-";
            case "shout":
                // shout diizinkan bila player punya permission astolfo.shout (cek via online)
                if (player.isOnline() && player.getPlayer() != null) {
                    return String.valueOf(player.getPlayer().hasPermission("astolfo.shout"));
                }
                return "true";
            default:
                return null;
        }
    }
}
