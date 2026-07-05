package id.astolfo.voicechat.api;

import java.util.List;
import java.util.UUID;

/**
 * AstolfoApi — API publik AstolfoVoice untuk plugin lain / Denizen / Skript.
 * Diakses via Bukkit.getServicesManager().load(AstolfoApi.class).
 *
 * Mencakup: range, status, playback, private channel (skenario user: player 1 & 2
 * bicara tanpa didengar sekitar / dengan range tak terbatas), broadcast, reset.
 */
public interface AstolfoApi {

    // ---- Voice range ----
    void setVoiceRange(UUID player, double range);

    double getVoiceRange(UUID player);

    void resetPlayer(UUID player); // reset range + status override

    // ---- Status ----
    VoiceStatus getPlayerStatus(UUID player);

    // ---- Playback ----
    PlaybackHandle playToPlayer(UUID player, String file, PlaybackOptions options);

    PlaybackHandle broadcastWorld(String world, String file, PlaybackOptions options);

    PlaybackHandle broadcastAll(String file, PlaybackOptions options);

    PlaybackHandle playAtLocation(String world, double x, double y, double z, String file, PlaybackOptions options);

    PlaybackHandle playToGroup(String group, String file, PlaybackOptions options);

    void stopPlayback(PlaybackHandle handle);

    // ---- Private channel ----
    PrivateChannel createPrivateChannel(List<UUID> members, PrivateChannelOptions options);

    void removePrivateChannel(PrivateChannel channel);

    // ---- Events ----
    void registerListener(AstolfoListener listener);

    void unregisterListener(AstolfoListener listener);
}
