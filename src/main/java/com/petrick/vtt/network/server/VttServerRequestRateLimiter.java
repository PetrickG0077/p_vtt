package com.petrick.vtt.network.server;

import com.petrick.vtt.VTT;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-player token buckets protecting authoritative VTT request handlers. */
public final class VttServerRequestRateLimiter {
    private static final long LOG_INTERVAL_NANOS = 5_000_000_000L;
    private static final Map<BucketKey, Bucket> BUCKETS = new HashMap<>();
    private static final Map<BucketKey, Long> REJECTED_COUNTS = new HashMap<>();
    private static final Map<BucketKey, Long> LAST_LOG_AT = new HashMap<>();

    private VttServerRequestRateLimiter() {}

    public static synchronized boolean allow(ServerPlayer player, Category category) {
        if (player == null || category == null) return false;
        long now = System.nanoTime();
        boolean master = VttServerPlayerEvents.isMaster(player);
        Limit limit = category.limit(master);
        BucketKey key = new BucketKey(player.getUUID(), category);
        Bucket bucket = BUCKETS.computeIfAbsent(
                key, ignored -> new Bucket(limit.capacity(), now));
        bucket.refill(now, limit);
        if (bucket.tryConsume()) return true;

        recordRejected(player, key, category, now, "rate limit exceeded");
        return false;
    }

    public static synchronized void reject(
            ServerPlayer player, Category category, String reason
    ) {
        if (player == null || category == null) return;
        BucketKey key = new BucketKey(player.getUUID(), category);
        recordRejected(player, key, category, System.nanoTime(), reason);
    }

    private static void recordRejected(
            ServerPlayer player, BucketKey key, Category category, long now, String reason
    ) {
        long rejected = REJECTED_COUNTS.merge(key, 1L, Long::sum);
        long lastLog = LAST_LOG_AT.getOrDefault(key, Long.MIN_VALUE);
        if (lastLog == Long.MIN_VALUE || now - lastLog >= LOG_INTERVAL_NANOS) {
            LAST_LOG_AT.put(key, now);
            VTT.LOGGER.warn("Rejected VTT {} request from {}: {} ({} rejected)",
                    category.name().toLowerCase(), player.getGameProfile().getName(),
                    reason == null || reason.isBlank() ? "unspecified reason" : reason, rejected);
        }
    }

    public static synchronized long rejectedCount(UUID playerId, Category category) {
        if (playerId == null || category == null) return 0L;
        return REJECTED_COUNTS.getOrDefault(new BucketKey(playerId, category), 0L);
    }

    public static synchronized void forget(UUID playerId) {
        if (playerId == null) return;
        BUCKETS.keySet().removeIf(key -> playerId.equals(key.playerId()));
        REJECTED_COUNTS.keySet().removeIf(key -> playerId.equals(key.playerId()));
        LAST_LOG_AT.keySet().removeIf(key -> playerId.equals(key.playerId()));
    }

    public static synchronized void clear() {
        BUCKETS.clear();
        REJECTED_COUNTS.clear();
        LAST_LOG_AT.clear();
    }

    public enum Category {
        TOKEN_TRANSFORM(60.0, 120.0, true),
        TOKEN_TRANSFORM_CORRECTION(2.0, 2.0, false),
        ENVIRONMENT(12.0, 18.0, true),
        TOKEN_LIFECYCLE(2.0, 5.0, true),
        TOKEN_OWNERSHIP(2.0, 5.0, true),
        PLAYER_MODE(1.0, 3.0, true),
        PRESENTATION(30.0, 60.0, true),
        SCENE_COMMAND(0.5, 3.0, true),
        TOKEN_DEFINITION(0.5, 2.0, true),
        MAP_DEFINITION(0.5, 2.0, true),
        ASSET_SYNC_REQUEST(4.0, 8.0, false),
        REPLICATION_RESYNC(0.5, 1.0, false);

        private final double refillPerSecond;
        private final double capacity;
        private final boolean masterBoost;

        Category(double refillPerSecond, double capacity, boolean masterBoost) {
            this.refillPerSecond = refillPerSecond;
            this.capacity = capacity;
            this.masterBoost = masterBoost;
        }

        private Limit limit(boolean master) {
            double multiplier = master && masterBoost ? 2.0 : 1.0;
            return new Limit(refillPerSecond * multiplier, capacity * multiplier);
        }
    }

    private static final class Bucket {
        private double tokens;
        private long lastRefillAt;

        private Bucket(double tokens, long lastRefillAt) {
            this.tokens = tokens;
            this.lastRefillAt = lastRefillAt;
        }

        private void refill(long now, Limit limit) {
            long elapsedNanos = Math.max(0L, now - lastRefillAt);
            tokens = Math.min(limit.capacity(),
                    tokens + elapsedNanos / 1_000_000_000.0 * limit.refillPerSecond());
            lastRefillAt = now;
        }

        private boolean tryConsume() {
            if (tokens < 1.0) return false;
            tokens -= 1.0;
            return true;
        }
    }

    private record BucketKey(UUID playerId, Category category) {}
    private record Limit(double refillPerSecond, double capacity) {}
}
