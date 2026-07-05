package id.astolfo.voicechat.api;

import java.util.UUID;

/**
 * Listener event API (ringan). Hook ke event koneksi/perubahan state player.
 */
public interface AstolfoListener {

    default void onPlayerConnected(UUID player) {
    }

    default void onPlayerDisconnected(UUID player) {
    }

    default void onPlayerVoiceRangeChanged(UUID player, double oldRange, double newRange) {
    }

    default void onPlayerStatusChanged(UUID player, VoiceStatus status) {
    }
}
