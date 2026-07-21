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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Incremental, per-server cache for synchronized VTT assets and token definitions. */
public final class VttClientAssetCache {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type INDEX_TYPE = new TypeToken<Map<String, CacheEntry>>() {}.getType();
    private static final long SYNC_TIMEOUT_MS = 120_000L;
    private static final long TERMINAL_PROGRESS_MS = 3_000L;
    private static final ExecutorService HASH_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "VTT client cache hashing");
        thread.setDaemon(true);
        return thread;
    });
    private static IncomingSync incoming;
    private static CacheVerification verification;
    private static String activeServerId;
    private static boolean readyForServerState;
    private static boolean recoveryRequired;
    private static SyncProgress progress = SyncProgress.hidden();
    private static long progressHideAt;

    private VttClientAssetCache() {}

    public static synchronized void begin(VttAssetManifestPayload manifest) {
        if (!validManifest(manifest)) {
            VTT.LOGGER.warn("Rejected invalid VTT asset manifest");
            discardIncoming();
            cancelVerification();
            readyForServerState = false;
            showFailure("Manifesto inválido");
            requestRecovery();
            return;
        }
        long manifestBytes = manifest.entries().stream()
                .mapToLong(VttAssetManifestPayload.Entry::size).sum();
        progress = new SyncProgress(SyncStatus.VERIFYING, 0, manifest.entries().size(),
                0L, manifestBytes, "");
        progressHideAt = 0L;
        readyForServerState = false;
        recoveryRequired = false;
        activeServerId = manifest.serverId();
        Path root = activeCacheRoot();
        discardIncoming();
        cancelVerification();
        deleteTreeQuietly(root.resolve(".incoming"));
        CacheVerification pendingVerification = new CacheVerification(
                manifest, root, System.currentTimeMillis());
        verification = pendingVerification;
        Minecraft minecraft = Minecraft.getInstance();
        pendingVerification.future = HASH_EXECUTOR.submit(() -> {
            try {
                VerificationResult result = verifyCache(manifest, root);
                minecraft.execute(() -> finishVerification(
                        pendingVerification, result, null));
            } catch (Throwable failure) {
                minecraft.execute(() -> finishVerification(
                        pendingVerification, null, failure));
            }
        });
    }

    private static synchronized void finishVerification(
            CacheVerification completed, VerificationResult result, Throwable failure
    ) {
        if (verification != completed) return;
        verification = null;
        if (failure != null) {
            if (!(failure instanceof CancellationException)) {
                VTT.LOGGER.error("Failed to verify VTT asset cache", failure);
                readyForServerState = false;
                showFailure("Falha ao verificar cache");
                requestRecovery();
            }
            return;
        }
        VttAssetManifestPayload manifest = completed.manifest;
        incoming = new IncomingSync(manifest, result.index, result.missing,
                completed.root.resolve(".incoming").resolve(manifest.syncId()),
                System.currentTimeMillis());
        updateDownloadProgress("");
        PacketDistributor.sendToServer(new VttAssetRequestPayload(
                manifest.syncId(), List.copyOf(result.missing)));
        VTT.LOGGER.info("VTT asset manifest {}: {} total, {} missing, {} cached",
                manifest.syncId(), manifest.entries().size(), result.missing.size(),
                manifest.entries().size() - result.missing.size());
    }

    private static VerificationResult verifyCache(
            VttAssetManifestPayload manifest, Path root
    ) {
        Map<String, CacheEntry> index = loadIndex(root);
        LinkedHashSet<Integer> missing = new LinkedHashSet<>();
        for (int fileIndex = 0; fileIndex < manifest.entries().size(); fileIndex++) {
            checkCancelled();
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
        return new VerificationResult(index, Set.copyOf(missing));
    }

    public static synchronized void accept(VttAssetChunkPayload payload) {
        if (incoming == null || incoming.failed || payload == null || payload.data() == null
                || !incoming.manifest.syncId().equals(payload.syncId())
                || !incoming.requested.contains(payload.fileIndex())
                || payload.fileIndex() < 0
                || payload.fileIndex() >= incoming.manifest.entries().size()) return;
        incoming.lastActivityAt = System.currentTimeMillis();
        VttAssetManifestPayload.Entry entry = incoming.manifest.entries().get(payload.fileIndex());
        int expectedChunks = Math.max(1, (int) ((entry.size()
                + VttAssetChunkPayload.MAX_CHUNK_BYTES - 1L)
                / VttAssetChunkPayload.MAX_CHUNK_BYTES));
        IncomingFile file = incoming.files.get(payload.fileIndex());
        try {
            if (file == null) {
                Path target = safeTarget(activeCacheRoot(), entry.category(), entry.relativePath());
                Path temporary = safeTarget(
                        incoming.temporaryRoot, entry.category(), entry.relativePath());
                file = new IncomingFile(entry, expectedChunks, temporary, target);
                incoming.files.put(payload.fileIndex(), file);
            }
            if (!file.accept(payload)) {
                file.discard();
                incoming.files.remove(payload.fileIndex());
                incoming.failed = true;
                showFailure("Chunk inválido");
                return;
            }
            incoming.receivedBytes += payload.data().length;
            updateDownloadProgress(entry.relativePath());
        } catch (IOException | RuntimeException exception) {
            if (file != null) file.discard();
            incoming.files.remove(payload.fileIndex());
            incoming.failed = true;
            showFailure("Falha ao gravar asset");
            VTT.LOGGER.error("Failed to stream VTT server asset: {}",
                    entry.relativePath(), exception);
            return;
        }
        if (!file.complete()) return;

        incoming.files.remove(payload.fileIndex());
        try {
            if (!file.commit()) {
                incoming.failed = true;
                showFailure("Hash inválido");
                return;
            }
            incoming.completed.add(payload.fileIndex());
            incoming.index.put(key(entry), new CacheEntry(entry.sha256(), entry.size()));
            updateDownloadProgress(entry.relativePath());
        } catch (IOException | IllegalArgumentException exception) {
            incoming.failed = true;
            showFailure("Falha ao finalizar asset");
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
            completed.discard();
            VTT.LOGGER.error("Incomplete VTT incremental asset sync: {}", payload.syncId());
            showFailure("Sincronização incompleta");
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
            completed.cleanupTemporaryRoot();
            VTT.LOGGER.info("Finished VTT incremental asset sync: {} downloaded, {} retained",
                    completed.completed.size(),
                    completed.manifest.entries().size() - completed.completed.size());
            scheduleCacheMaintenance(completed.manifest.serverId(), finalIndex.keySet());
            VTT.getApplication().getActiveSession().reloadSyncedServerAssets();
            readyForServerState = true;
            recoveryRequired = false;
            showComplete(completed);
        } catch (IOException | RuntimeException exception) {
            completed.discard();
            VTT.LOGGER.error("Failed to finalize VTT incremental asset sync", exception);
            showFailure("Falha ao atualizar cache");
            requestRecovery();
        }
    }

    public static synchronized void tick() {
        if (verification != null
                && System.currentTimeMillis() - verification.startedAt > SYNC_TIMEOUT_MS) {
            cancelVerification();
            readyForServerState = false;
            showFailure("Tempo de verificação esgotado");
            requestRecovery();
        }
        if (incoming != null
                && System.currentTimeMillis() - incoming.lastActivityAt > SYNC_TIMEOUT_MS) {
            VTT.LOGGER.warn("Discarded timed-out VTT asset sync: {}",
                    incoming.manifest.syncId());
            discardIncoming();
            readyForServerState = false;
            showFailure("Tempo de sincronização esgotado");
            requestRecovery();
        }
        if (recoveryRequired && incoming == null && verification == null) requestRecovery();
    }

    public static synchronized void reset() {
        discardIncoming();
        cancelVerification();
        activeServerId = null;
        readyForServerState = false;
        recoveryRequired = false;
        progress = SyncProgress.hidden();
        progressHideAt = 0L;
    }

    public static synchronized boolean isReadyForServerState() {
        return readyForServerState && incoming == null && verification == null;
    }

    public static synchronized void recoverServerState() {
        readyForServerState = false;
        showFailure("Reconectando sincronização");
        requestRecovery();
    }

    public static synchronized SyncProgress progressSnapshot() {
        if (progressHideAt > 0L && System.currentTimeMillis() >= progressHideAt) {
            progress = SyncProgress.hidden();
            progressHideAt = 0L;
        }
        return progress;
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

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    checkCancelled();
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

    private static void scheduleCacheMaintenance(String serverId, Set<String> retainedKeys) {
        Path base = cacheBase();
        Set<String> safeRetainedKeys = new LinkedHashSet<>();
        if (retainedKeys != null) {
            retainedKeys.stream().filter(key -> key != null && !key.isBlank())
                    .forEach(safeRetainedKeys::add);
        }
        HASH_EXECUTOR.submit(() -> VttClientAssetCacheMaintenance.maintain(
                base, serverId, Set.copyOf(safeRetainedKeys)));
    }

    private static void updateDownloadProgress(String currentFile) {
        if (incoming == null) return;
        progress = new SyncProgress(SyncStatus.DOWNLOADING,
                incoming.completed.size(), incoming.requested.size(),
                incoming.receivedBytes, incoming.totalRequestedBytes,
                currentFile == null ? "" : currentFile);
        progressHideAt = 0L;
    }

    private static void showComplete(IncomingSync completed) {
        progress = new SyncProgress(SyncStatus.COMPLETE,
                completed.requested.size(), completed.requested.size(),
                completed.totalRequestedBytes, completed.totalRequestedBytes, "");
        progressHideAt = System.currentTimeMillis() + TERMINAL_PROGRESS_MS;
    }

    private static void showFailure(String message) {
        int completedFiles = incoming == null ? 0 : incoming.completed.size();
        int totalFiles = incoming == null ? 0 : incoming.requested.size();
        long receivedBytes = incoming == null ? 0L : incoming.receivedBytes;
        long totalBytes = incoming == null ? 0L : incoming.totalRequestedBytes;
        progress = new SyncProgress(SyncStatus.FAILED, completedFiles, totalFiles,
                receivedBytes, totalBytes, message == null ? "" : message);
        progressHideAt = incoming == null
                ? System.currentTimeMillis() + TERMINAL_PROGRESS_MS : 0L;
    }

    private static void discardIncoming() {
        if (incoming == null) return;
        IncomingSync discarded = incoming;
        incoming = null;
        discarded.discard();
    }

    private static void cancelVerification() {
        CacheVerification cancelled = verification;
        verification = null;
        if (cancelled != null && cancelled.future != null) cancelled.future.cancel(true);
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
    }

    private static void deleteTreeQuietly(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            VTT.LOGGER.warn("Failed to clean temporary VTT asset files: {}", root, exception);
        }
    }

    private record CacheEntry(String sha256, long size) {}

    private record VerificationResult(
            Map<String, CacheEntry> index, Set<Integer> missing
    ) {}

    private static final class CacheVerification {
        private final VttAssetManifestPayload manifest;
        private final Path root;
        private final long startedAt;
        private Future<?> future;

        private CacheVerification(
                VttAssetManifestPayload manifest, Path root, long startedAt
        ) {
            this.manifest = manifest;
            this.root = root;
            this.startedAt = startedAt;
        }
    }

    private static final class IncomingSync {
        private final VttAssetManifestPayload manifest;
        private final Map<String, CacheEntry> index;
        private final Set<Integer> requested;
        private final Set<Integer> completed = new LinkedHashSet<>();
        private final Map<Integer, IncomingFile> files = new HashMap<>();
        private final Path temporaryRoot;
        private final long totalRequestedBytes;
        private long lastActivityAt;
        private long receivedBytes;
        private boolean failed;

        private IncomingSync(VttAssetManifestPayload manifest, Map<String, CacheEntry> index,
                             Set<Integer> requested, Path temporaryRoot, long now) {
            this.manifest = manifest;
            this.index = new LinkedHashMap<>(index);
            this.requested = Set.copyOf(requested);
            this.temporaryRoot = temporaryRoot;
            this.totalRequestedBytes = requested.stream()
                    .mapToLong(fileIndex -> manifest.entries().get(fileIndex).size()).sum();
            this.lastActivityAt = now;
        }

        private void discard() {
            files.values().forEach(IncomingFile::discard);
            files.clear();
            deleteTreeQuietly(temporaryRoot);
        }

        private void cleanupTemporaryRoot() {
            deleteTreeQuietly(temporaryRoot);
        }
    }

    public enum SyncStatus {
        IDLE,
        VERIFYING,
        DOWNLOADING,
        COMPLETE,
        FAILED
    }

    public record SyncProgress(
            SyncStatus status,
            int completedFiles,
            int totalFiles,
            long receivedBytes,
            long totalBytes,
            String currentFile
    ) {
        private static SyncProgress hidden() {
            return new SyncProgress(SyncStatus.IDLE, 0, 0, 0L, 0L, "");
        }

        public boolean visible() {
            return status != SyncStatus.IDLE;
        }

        public double fraction() {
            if (status == SyncStatus.COMPLETE) return 1.0;
            if (totalBytes > 0L) {
                return Math.max(0.0, Math.min(1.0, receivedBytes / (double) totalBytes));
            }
            return totalFiles <= 0 ? 0.0
                    : Math.max(0.0, Math.min(1.0, completedFiles / (double) totalFiles));
        }
    }

    private static final class IncomingFile {
        private final VttAssetManifestPayload.Entry entry;
        private final int chunkCount;
        private final Path temporary;
        private final Path target;
        private final MessageDigest digest;
        private OutputStream output;
        private int nextChunk;
        private long bytesWritten;

        private IncomingFile(VttAssetManifestPayload.Entry entry, int chunkCount,
                             Path temporary, Path target) throws IOException {
            this.entry = entry;
            this.chunkCount = chunkCount;
            this.temporary = temporary;
            this.target = target;
            try {
                this.digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
            Files.createDirectories(temporary.getParent());
            this.output = Files.newOutputStream(temporary, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        }

        private boolean accept(VttAssetChunkPayload payload) throws IOException {
            int expectedBytes = (int) Math.min(
                    VttAssetChunkPayload.MAX_CHUNK_BYTES, entry.size() - bytesWritten);
            if (payload.chunkCount() != chunkCount || payload.chunkIndex() != nextChunk
                    || payload.chunkIndex() < 0 || payload.chunkIndex() >= chunkCount
                    || payload.data().length != expectedBytes) return false;
            output.write(payload.data());
            digest.update(payload.data());
            bytesWritten += payload.data().length;
            nextChunk++;
            return true;
        }

        private boolean complete() {
            return nextChunk == chunkCount && bytesWritten == entry.size();
        }

        private boolean commit() throws IOException {
            closeOutput();
            String actualHash = HexFormat.of().formatHex(digest.digest());
            if (bytesWritten != entry.size()
                    || !actualHash.equalsIgnoreCase(entry.sha256())) {
                Files.deleteIfExists(temporary);
                return false;
            }
            Files.createDirectories(target.getParent());
            moveReplacing(temporary, target);
            return true;
        }

        private void discard() {
            try {
                closeOutput();
                Files.deleteIfExists(temporary);
            } catch (IOException exception) {
                VTT.LOGGER.warn("Failed to discard partial VTT asset: {}", temporary, exception);
            }
        }

        private void closeOutput() throws IOException {
            if (output == null) return;
            output.close();
            output = null;
        }
    }
}
