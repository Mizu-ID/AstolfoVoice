package id.astolfo.voicechat.voice.server;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MuteHolder - shared holder status mute per-player, ditulis oleh API/command,
 * dibaca oleh ProximityResolver di awal routing mic. Hindari coupling langsung
 * ke AstolfoApiImpl. Mute = sender tidak mengirim suara ke siapapun (server-side).
 *
 * Compat-safe: murni server-side routing gate, tidak menyentuh wire format SVC.
 */
public final class MuteHolder {

    private static final ConcurrentHashMap<UUID, Boolean> MUTED = new ConcurrentHashMap<>();

    public static void set(UUID player, boolean muted) {
        if (muted) {
            MUTED.put(player, Boolean.TRUE);
        } else {
            MUTED.remove(player);
        }
    }

    public static boolean isMuted(UUID player) {
        return MUTED.getOrDefault(player, Boolean.FALSE);
    }

    public static void remove(UUID player) {
        MUTED.remove(player);
    }

    public static void clear() {
        MUTED.clear();
    }

    private MuteHolder() {
    }
}
