package com.petrick.vtt.network.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttAssetChunkPayload;
import com.petrick.vtt.network.payload.VttAssetManifestPayload;
import com.petrick.vtt.network.payload.VttAssetRequestPayload;
import com.petrick.vtt.network.payload.VttAssetSyncCompletePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Incremental, per-server cache for synchronized VTT assets and token definitions. */
public final class VttClientAssetCache {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type INDEX_TYPE = new TypeToken<Map<String, CacheEntry>>() {}.getType();
    private static final long SYNC_TIMEOUT_MS = 30_000L;
    private static IncomingSync incoming;
    private static String activeServerId;
    private static boolean readyForServerState;
    private static boolean recoveryRequired;

    private VttClientAssetCache() {}

    public static synchronized void begin(VttAssetManifestPayload manifest) {
        if (!validManifest(manifest)) {
            VTT.LOGGER.warn("Rejected invalid VTT asset manifest");
            readyForServerState = false;
            requestRecovery();
            return;
        }
        readyForServerState = false;
        recoveryRequired = false;
        activeServerId = manifest.serverId();
        Path root = activeCacheRoot();
        Map<String, CacheEntry> index = loadIndex(root);
        LinkedHashSet<Integer> missing = new LinkedHashSet<>();
        for (int fileIndex = 0; fileIndex < manifest.entries().size(); fileIndex++) {
            VttAssetManifestPayload.Entry entry = manifest.entries().get(fileIndex);
            String key = key(entry);
            CacheEntry cached = index.get(key);
            Path target = safeTarget(root, entry.category(), entry.relativePath());
            try {
                if (cached == null || !entry.sha256().equalsIgnoreCase(cached.sha256())
                        || entry.size() != cached.size() || !Files.isRegularFile(target)
                        || Files.size(target) != entry.size()
                        || !entry.sha256().equalsIgnoreCase(sha256(target))) {
                    missing.add(fileIndex);
                }
            } catch (IOException exception) {
                missing.add(fileIndex);
            }
        }
        incoming = new IncomingSync(manifest, index, missing, System.currentTimeMillis());
        PacketDistributor.sendToServer(new VttAssetRequestPayload(
                manifest.syncId(), List.copyOf(missing)));
        VTT.LOGGER.info("VTT asset manifest {}: {} total, {} missing, {} cached",
                manifest.syncId(), manifest.entries().size(), missing.size(),
                manifest.entries().size() - missing.size());
    }

    public static synchronized void accept(VttAssetChunkPayload payload) {
        if (incoming == null || payload == null || payload.data() == null
                || !incoming.manifest.syncId().equals(payload.syncId())
                || !incoming.requested.contains(payload.fileIndex())
                || payload.fileIndex() < 0
                || payload.fileIndex() >= incoming.manifest.entries().size()) return;
        incoming.lastActivityAt = System.currentTimeMillis();
        VttAssetManifestPayload.Entry entry = incoming.manifest.entries().get(payload.fileIndex());
        int expectedChunks = Math.max(1, (int) ((entry.size()
                + VttAssetChunkPayload.MAX_CHUNK_BYTES - 1L)
                / VttAssetChunkPayload.MAX_CHUNK_BYTES));
        IncomingFile file = incoming.files.computeIfAbsent(payload.fileIndex(),
                ignored -> new IncomingFile(entry, expectedChunks));
        if (!file.accept(payload)) {
            incoming.files.remove(payload.fileIndex());
            incoming.failed = true;
            return;
        }
        if (!file.complete()) return;

        incoming.files.remove(payload.fileIndex());
        byte[] bytes = file.bytes();
        if (bytes.length != entry.size() || !sha256(bytes).equalsIgnoreCase(entry.sha256())) {
            incoming.failed = true;
            return;
        }
        try {
            writeAtomically(safeTarget(activeCacheRoot(), entry.category(), entry.relativePath()), bytes);
            incoming.completed.add(payload.fileIndex());
            incoming.index.put(key(entry), new CacheEntry(entry.sha256(), entry.size()));
        } catch (IOException | IllegalArgumentException exception) {
            incoming.failed = true;
            VTT.LOGGER.error("Failed to cache VTT server asset: {}",
                    entry.relativePath(), exception);
        }
    }

    public static synchronized void finish(VttAssetSyncCompletePayload payload) {
        if (incoming == null || payload == null
                || !incoming.manifest.syncId().equals(payload.syncId())) return;
        IncomingSync completed = incoming;
        incoming = null;
        if (completed.failed || payload.transferredFiles() != completed.requested.size()
                || completed.completed.size() != completed.requested.size()
                || !completed.files.isEmpty()) {
            VTT.LOGGER.error("Incomplete VTT incremental asset sync: {}", payload.syncId());
            requestRecovery();
            return;
        }
        try {
            Map<String, CacheEntry> finalIndex = completed.manifest.replaceScope()
                    ? manifestIndex(completed.manifest.entries())
                    : new LinkedHashMap<>(completed.index);
            if (completed.manifest.replaceScope()) {
                removeStaleFiles(activeCacheRoot(), finalIndex.keySet());
            }
            saveIndex(activeCacheRoot(), finalIndex);
            VTT.LOGGER.info("Finished VTT incremental asset sync: {} downloaded, {} retained",
                    completed.completed.size(),
                    completed.manifest.entries().size() - completed.completed.size());
            VTT.getApplication().getActiveSession().reloadSyncedServerAssets();
            readyForServerState = true;
            recoveryRequired = false;
        } catch (IOException | RuntimeException exception) {
            VTT.LOGGER.error("Failed to finalize VTT incremental asset sync", exception);
            requestRecovery();
        }
    }

    public static synchronized void tick() {
        if (incoming != null
                && System.currentTimeMillis() - incoming.lastActivityAt > SYNC_TIMEOUT_MS) {
            VTT.LOGGER.warn("Discarded timed-out VTT asset sync: {}",
                    incoming.manifest.syncId());
            incoming = null;
            readyForServerState = false;
            requestRecovery();
        }
        if (recoveryRequired && incoming == null) requestRecovery();
    }

    public static synchronized void reset() {
        incoming = null;
        activeServerId = null;
        readyForServerState = false;
        recoveryRequired = false;
    }

    public static synchronized boolean isReadyForServerState() {
        return readyForServerState && incoming == null;
    }

    public static synchronized void recoverServerState() {
        readyForServerState = false;
        requestRecovery();
    }

    public static synchronized Path activeCacheRoot() {
        Path base = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config/vtt_assets/cache/servers").toAbsolutePath().normalize();
        return activeServerId == null ? base.resolve("inactive") : base.resolve(activeServerId);
    }

    private static boolean validManifest(VttAssetManifestPayload manifest) {
        if (manifest == null || manifest.entries().size() > VttAssetManifestPayload.MAX_FILES
                || !validUuid(manifest.syncId()) || !validUuid(manifest.serverId())) return false;
        long totalBytes = 0L;
        Set<String> keys = new LinkedHashSet<>();
        try {
            Path root = cacheBase().resolve(manifest.serverId()).normalize();
            if (!root.startsWith(cacheBase())) return false;
            for (VttAssetManifestPayload.Entry entry : manifest.entries()) {
                if (entry == null || !validCategory(entry.category())
                        || entry.sha256() == null || !entry.sha256().matches("[0-9a-fA-F]{64}")
                        || entry.size() < 0L || entry.size() > VttAssetManifestPayload.MAX_FILE_BYTES
                        || !keys.add(key(entry))) return false;
                totalBytes += entry.size();
                if (totalBytes > VttAssetManifestPayload.MAX_MANIFEST_BYTES) return false;
                safeTarget(root, entry.category(), entry.relativePath());
            }
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static Path cacheBase() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config/vtt_assets/cache/servers").toAbsolutePath().normalize();
    }

    private static Path safeTarget(Path root, String category, String relativePath) {
        if (!validCategory(category) || relativePath == null || relativePath.isBlank()
                || relativePath.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid asset path");
        }
        Path categoryRoot = root.resolve(category).normalize();
        Path target = categoryRoot.resolve(relativePath.replace('\\', '/')).normalize();
        if (!target.startsWith(categoryRoot)) throw new IllegalArgumentException("Asset path escapes cache");
        return target;
    }

    private static boolean validCategory(String category) {
        return "assets".equals(category) || "tokens".equals(category);
    }

    private static boolean validUuid(String value) {
        try {
            return value != null && UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static Map<String, CacheEntry> loadIndex(Path root) {
        Path file = root.resolve("manifest.json");
        if (!Files.isRegularFile(file)) return new LinkedHashMap<>();
        try {
            Map<String, CacheEntry> loaded = GSON.fromJson(Files.readString(file), INDEX_TYPE);
            return loaded == null ? new LinkedHashMap<>() : new LinkedHashMap<>(loaded);
        } catch (IOException | RuntimeException exception) {
            VTT.LOGGER.warn("Could not read VTT asset cache index: {}", file, exception);
            return new LinkedHashMap<>();
        }
    }

    private static void saveIndex(Path root, Map<String, CacheEntry> index) throws IOException {
        Files.createDirectories(root);
        Path file = root.resolve("manifest.json");
        Path temporary = root.resolve("manifest.json.tmp");
        Files.writeString(temporary, GSON.toJson(index), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        moveReplacing(temporary, file);
    }

    private static Map<String, CacheEntry> manifestIndex(
            List<VttAssetManifestPayload.Entry> entries
    ) {
        Map<String, CacheEntry> result = new LinkedHashMap<>();
        for (VttAssetManifestPayload.Entry entry : entries) {
            result.put(key(entry), new CacheEntry(entry.sha256(), entry.size()));
        }
        return result;
    }

    private static void removeStaleFiles(Path root, Set<String> retained) throws IOException {
        for (String category : List.of("assets", "tokens")) {
            Path categoryRoot = root.resolve(category);
            if (!Files.isDirectory(categoryRoot)) continue;
            try (var paths = Files.walk(categoryRoot)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    if (Files.isRegularFile(path)) {
                        String relative = categoryRoot.relativize(path).toString().replace('\\', '/');
                        if (!retained.contains(category + ":" + relative)) Files.deleteIfExists(path);
                    } else if (!path.equals(categoryRoot)) {
                        try (var children = Files.list(path)) {
                            if (children.findAny().isEmpty()) Files.deleteIfExists(path);
                        }
                    }
                }
            }
        }
    }

    private static void writeAtomically(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temporary, bytes, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        moveReplacing(temporary, target);
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String key(VttAssetManifestPayload.Entry entry) {
        return entry.category() + ":" + entry.relativePath().replace('\\', '/');
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requestRecovery() {
        recoveryRequired = true;
        var session = VTT.getApplication().getActiveSession();
        VttClientSceneSnapshotReceiver.recoverAfterAssetFailure(
                session.getNetworkAuthorityRevision());
    }

    private record CacheEntry(String sha256, long size) {}

    private static final class IncomingSync {
        private final VttAssetManifestPayload manifest;
        private final Map<String, CacheEntry> index;
        private final Set<Integer> requested;
        private final Set<Integer> completed = new LinkedHashSet<>();
        private final Map<Integer, IncomingFile> files = new HashMap<>();
        private long lastActivityAt;
        private boolean failed;

        private IncomingSync(VttAssetManifestPayload manifest, Map<String, CacheEntry> index,
                             Set<Integer> requested, long now) {
            this.manifest = manifest;
            this.index = new LinkedHashMap<>(index);
            this.requested = Set.copyOf(requested);
            this.lastActivityAt = now;
        }
    }

    private static final class IncomingFile {
        private final VttAssetManifestPayload.Entry entry;
        private final int chunkCount;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private int nextChunk;

        private IncomingFile(VttAssetManifestPayload.Entry entry, int chunkCount) {
            this.entry = entry;
            this.chunkCount = chunkCount;
        }

        private boolean accept(VttAssetChunkPayload payload) {
            return payload.chunkCount() == chunkCount && payload.chunkIndex() == nextChunk
                    && payload.chunkIndex() >= 0 && payload.chunkIndex() < chunkCount
                    && payload.data().length <= VttAssetChunkPayload.MAX_CHUNK_BYTES
                    && (long) output.size() + payload.data().length <= entry.size()
                    && write(payload.data());
        }

        private boolean write(byte[] bytes) {
            output.writeBytes(bytes);
            nextChunk++;
            return true;
        }

        private boolean complete() {
            return nextChunk == chunkCount && output.size() == entry.size();
        }

        private byte[] bytes() {
            return output.toByteArray();
        }
    }
}
