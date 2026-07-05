package id.astolfo.voicechat.net;

import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter paket plugin-channel per player (byte-exact behavior SVC).
 * Token bucket: maxPacketsPerSecond * windowSeconds dalam window 5 detik.
 * maxPacketsPerSecond <= 0 → nonaktif (semua diizinkan).
 */
public final class PacketRateLimiter {

    private final ConcurrentHashMap<UUID, RateLimiter> rateLimiters = new ConcurrentHashMap<>();
    private final int maxPacketsPerSecond;
    private static final int TIME_WINDOW_SECONDS = 5;

    public PacketRateLimiter(int maxPacketsPerSecond) {
        this.maxPacketsPerSecond = maxPacketsPerSecond;
    }

    public boolean allow(UUID player) {
        if (maxPacketsPerSecond <= 0) {
            return true;
        }
        RateLimiter limiter = rateLimiters.computeIfAbsent(player,
                id -> new RateLimiter(maxPacketsPerSecond * TIME_WINDOW_SECONDS, 1000L * TIME_WINDOW_SECONDS));
        return limiter.tryAcquire();
    }

    public void onPlayerLoggedOut(Player player) {
        rateLimiters.remove(player.getUniqueId());
    }

    private static final class RateLimiter {
        private final int threshold;
        private final long timePerTokenNanos;
        private long lastLeakNanos;
        private long amount;

        RateLimiter(int threshold, long windowMillis) {
            this.threshold = threshold;
            this.timePerTokenNanos = (windowMillis * 1_000_000L) / threshold;
            this.lastLeakNanos = System.nanoTime();
            this.amount = 0L;
        }

        boolean tryAcquire() {
            long currentTime = System.nanoTime();
            long elapsed = currentTime - lastLeakNanos;
            long leakedTokens = elapsed / timePerTokenNanos;

            if (leakedTokens > 0) {
                amount -= leakedTokens;
                if (amount < 0) {
                    amount = 0;
                }
                lastLeakNanos += leakedTokens * timePerTokenNanos;
            }

            if (amount >= threshold) {
                return false;
            }

            amount++;
            return true;
        }
    }
}
