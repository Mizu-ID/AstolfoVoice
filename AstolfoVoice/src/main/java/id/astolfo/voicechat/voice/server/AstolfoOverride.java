package id.astolfo.voicechat.voice.server;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AstolfoOverride — shared holder voice range per-player, ditulis oleh API/command,
 * dibaca oleh ProximityResolver. Hindari coupling langsung antar modul.
 * Nilai <=0 berarti tidak ada override (pakai config/dynamic).
 */
public final class AstolfoOverride {

    private static final ConcurrentHashMap<UUID, Double> OVERRIDES = new ConcurrentHashMap<>();

    public static void set(UUID player, double range) {
        if (range <= 0) {
            OVERRIDES.remove(player);
        } else {
            OVERRIDES.put(player, range);
        }
    }

    public static double get(UUID player) {
        return OVERRIDES.getOrDefault(player, -1.0);
    }

    public static void remove(UUID player) {
        OVERRIDES.remove(player);
    }

    private AstolfoOverride() {
    }
}
