package com.petrick.vtt.network.client;

import com.petrick.vtt.VTT;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;

/** Keeps the disposable, per-server asset cache within bounded disk usage. */
final class VttClientAssetCacheMaintenance {
    private static final long MAX_SERVER_CACHE_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final long MAX_TOTAL_CACHE_BYTES = 8L * 1024L * 1024L * 1024L;
    private static final long MAINTENANCE_INTERVAL_MS = 5L * 60L * 1_000L;
    private static final String LAST_USED_FILE = ".last_used";
    private static long lastMaintenanceAt;

    private VttClientAssetCacheMaintenance() {}

    static void maintain(
            Path cacheBase, String activeServerId, Set<String> retainedActiveKeys
    ) {
        if (cacheBase == null || !validUuid(activeServerId)) return;
        Path normalizedBase = cacheBase.toAbsolutePath().normalize();
        Path activeRoot = normalizedBase.resolve(activeServerId).normalize();
        touchLastUsed(activeRoot);
        if (!claimMaintenance()) return;

        long removedBytes = 0L;
        try {
            Files.createDirectories(normalizedBase);
            List<ServerCache> caches = new ArrayList<>();
            try (var children = Files.list(normalizedBase)) {
                for (Path child : children.toList()) {
                    checkCancelled();
                    if (!Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)
                            || !validUuid(child.getFileName().toString())) continue;
                    Path root = child.toAbsolutePath().normalize();
                    removedBytes += removeTemporaryFiles(root);
                    if (root.equals(activeRoot)) {
                        removedBytes += removeUnindexedActiveFiles(
                                root, retainedActiveKeys == null ? Set.of() : retainedActiveKeys);
                    }
                    long size = directorySize(root);
                    if (!root.equals(activeRoot) && size > MAX_SERVER_CACHE_BYTES) {
                        removedBytes += deleteTree(root);
                        continue;
                    }
                    caches.add(new ServerCache(root, size, lastUsed(root)));
                }
            }

            long totalBytes = caches.stream().mapToLong(ServerCache::size).sum();
            if (totalBytes > MAX_TOTAL_CACHE_BYTES) {
                List<ServerCache> removable = caches.stream()
                        .filter(cache -> !cache.root().equals(activeRoot))
                        .sorted(Comparator.comparingLong(ServerCache::lastUsedAt))
                        .toList();
                for (ServerCache cache : removable) {
                    checkCancelled();
                    if (totalBytes <= MAX_TOTAL_CACHE_BYTES) break;
                    removedBytes += deleteTree(cache.root());
                    totalBytes -= cache.size();
                }
            }

            long activeBytes = directorySize(activeRoot);
            if (activeBytes > MAX_SERVER_CACHE_BYTES) {
                VTT.LOGGER.warn("Active VTT asset cache exceeds its soft limit: {} MiB / {} MiB",
                        toMib(activeBytes), toMib(MAX_SERVER_CACHE_BYTES));
            }
            if (totalBytes > MAX_TOTAL_CACHE_BYTES) {
                VTT.LOGGER.warn("VTT asset caches exceed their soft global limit: {} MiB / {} MiB",
                        toMib(totalBytes), toMib(MAX_TOTAL_CACHE_BYTES));
            }
            if (removedBytes > 0L) {
                VTT.LOGGER.info("VTT asset cache maintenance released {} MiB",
                        toMibRoundedUp(removedBytes));
            }
        } catch (CancellationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            VTT.LOGGER.warn("Could not complete VTT asset cache maintenance", exception);
        }
    }

    private static synchronized boolean claimMaintenance() {
        long now = System.currentTimeMillis();
        if (now - lastMaintenanceAt < MAINTENANCE_INTERVAL_MS) return false;
        lastMaintenanceAt = now;
        return true;
    }

    private static void touchLastUsed(Path root) {
        try {
            Files.createDirectories(root);
            Files.writeString(root.resolve(LAST_USED_FILE),
                    Long.toString(System.currentTimeMillis()), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException | RuntimeException exception) {
            VTT.LOGGER.debug("Could not update VTT asset cache access time: {}", root, exception);
        }
    }

    private static long lastUsed(Path root) {
        Path marker = root.resolve(LAST_USED_FILE);
        try {
            if (Files.isRegularFile(marker)) {
                return Long.parseLong(Files.readString(marker, StandardCharsets.UTF_8).trim());
            }
            return Files.getLastModifiedTime(root, LinkOption.NOFOLLOW_LINKS).toMillis();
        } catch (IOException | NumberFormatException exception) {
            return 0L;
        }
    }

    private static long removeTemporaryFiles(Path root) throws IOException {
        long removed = deleteTree(root.resolve(".incoming"));
        Path manifestTemporary = root.resolve("manifest.json.tmp");
        if (Files.isRegularFile(manifestTemporary, LinkOption.NOFOLLOW_LINKS)) {
            removed += Files.size(manifestTemporary);
            Files.deleteIfExists(manifestTemporary);
        }
        return removed;
    }

    private static long removeUnindexedActiveFiles(Path root, Set<String> retained) throws IOException {
        long removed = 0L;
        for (String category : List.of("assets", "tokens")) {
            Path categoryRoot = root.resolve(category);
            if (!Files.isDirectory(categoryRoot, LinkOption.NOFOLLOW_LINKS)) continue;
            try (var paths = Files.walk(categoryRoot)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    checkCancelled();
                    if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                        String relative = categoryRoot.relativize(path).toString().replace('\\', '/');
                        if (!retained.contains(category + ":" + relative)) {
                            removed += Files.size(path);
                            Files.deleteIfExists(path);
                        }
                    } else if (!path.equals(categoryRoot)
                            && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                        try (var children = Files.list(path)) {
                            if (children.findAny().isEmpty()) Files.deleteIfExists(path);
                        }
                    }
                }
            }
        }
        return removed;
    }

    private static long directorySize(Path root) throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return 0L;
        long size = 0L;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                checkCancelled();
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) size += Files.size(path);
            }
        }
        return size;
    }

    private static long deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return 0L;
        long removed = Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                ? directorySize(root) : Files.size(root);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            Files.deleteIfExists(root);
            return removed;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                checkCancelled();
                Files.deleteIfExists(path);
            }
        }
        return removed;
    }

    private static boolean validUuid(String value) {
        try {
            return value != null && UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static long toMib(long bytes) {
        return bytes / (1024L * 1024L);
    }

    private static long toMibRoundedUp(long bytes) {
        long mib = 1024L * 1024L;
        return (bytes + mib - 1L) / mib;
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
    }

    private record ServerCache(Path root, long size, long lastUsedAt) {}
}
