package com.petrick.vtt.network.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.petrick.vtt.VTT;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttSceneObject;
import com.petrick.vtt.network.payload.VttAssetManifestPayload;
import com.petrick.vtt.network.payload.VttAssetRequestPayload;
import com.petrick.vtt.network.payload.VttAssetChunkPayload;
import com.petrick.vtt.network.payload.VttAssetSyncCompletePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Stream;

public final class VttServerAssetSyncService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long SYNC_TIMEOUT_MS = 120_000L;
    private static final int MAX_CHUNKS_PER_TICK = 4;
    private static final int MAX_HASH_CACHE_ENTRIES = 8_192;
    private static final long HASH_CACHE_PRUNE_INTERVAL_MS = 60_000L;
    private static final int HASH_CACHE_FORMAT_VERSION = 1;
    private static final Map<UUID, PendingSync> PENDING = new HashMap<>();
    private static final Map<UUID, ManifestPreparation> PREPARING = new HashMap<>();
    private static final Deque<UUID> ACTIVE_TRANSFERS = new ArrayDeque<>();
    private static final Map<Path, CachedHash> HASH_CACHE =
            new LinkedHashMap<>(256, 0.75F, true);
    private static final Map<Path, CompletableFuture<CachedHash>> HASHES_IN_FLIGHT =
            new ConcurrentHashMap<>();
    private static final ExecutorService HASH_EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "VTT server asset hashing");
        thread.setDaemon(true);
        return thread;
    });
    private static String serverId;
    private static long lastHashCachePruneAt;
    private static Path hashCacheRoot;
    private static boolean hashCacheLoaded;
    private static boolean hashCacheDirty;

    private VttServerAssetSyncService() {}

    public static void sendActiveSceneAssets(ServerPlayer player, VttScene scene) {
        sendActiveSceneAssets(player, scene, false, null);
    }

    public static void sendActiveSceneAssets(ServerPlayer player, VttScene scene, boolean includeAllTokens) {
        sendActiveSceneAssets(player, scene, includeAllTokens, null);
    }

    public static void sendActiveSceneAssets(
            ServerPlayer player, VttScene scene, boolean includeAllTokens,
            Runnable completion
    ) {
        prepareSync(player, AssetScope.fromScene(scene), includeAllTokens, true, completion);
    }

    public static void sendVisibleTokenAssets(
            ServerPlayer player, List<VttSceneObject> visibleObjects, Runnable completion
    ) {
        if (player == null || visibleObjects == null || visibleObjects.isEmpty()) {
            if (completion != null) completion.run();
            return;
        }
        prepareSync(player, AssetScope.fromObjects(visibleObjects), false, false, completion);
    }

    private static synchronized void prepareSync(
            ServerPlayer player, AssetScope scope, boolean includeAllTokens,
            boolean replaceScope, Runnable completion
    ) {
        if (player == null) return;
        expirePending();
        UUID playerId = player.getUUID();
        cancelPlayerWork(playerId);
        ManifestPreparation preparation = new ManifestPreparation(
                player, UUID.randomUUID().toString(), replaceScope, completion,
                System.currentTimeMillis());
        PREPARING.put(playerId, preparation);
        MinecraftServer server = player.getServer();
        Path storageRoot = FMLPaths.GAMEDIR.get()
                .resolve("config/vtt_assets").toAbsolutePath().normalize();
        preparation.future = HASH_EXECUTOR.submit(() -> {
            if (server == null) return;
            try {
                List<SyncFile> files = collectFiles(scope, includeAllTokens, storageRoot);
                server.execute(() -> finishPreparation(
                        playerId, preparation, files, null));
            } catch (Throwable failure) {
                server.execute(() -> finishPreparation(
                        playerId, preparation, null, failure));
            }
        });
    }

    private static synchronized void finishPreparation(
            UUID playerId, ManifestPreparation preparation,
            List<SyncFile> files, Throwable failure
    ) {
        if (PREPARING.get(playerId) != preparation) return;
        PREPARING.remove(playerId);
        if (failure != null) {
            if (!(unwrap(failure) instanceof CancellationException)) {
                VTT.LOGGER.error("Failed to prepare VTT asset manifest for {}",
                        preparation.player.getGameProfile().getName(), unwrap(failure));
                beginFallbackSync(preparation);
            }
            return;
        }
        var server = preparation.player.getServer();
        if (server == null || server.getPlayerList().getPlayer(playerId) != preparation.player) return;
        beginSync(preparation.player, files, preparation.replaceScope,
                preparation.completion, preparation.syncId);
    }

    private static synchronized void beginSync(
            ServerPlayer player, List<SyncFile> files, boolean replaceScope,
            Runnable completion, String syncId
    ) {
        if (player == null) return;
        expirePending();
        List<SyncFile> safeFiles = files == null ? List.of() : List.copyOf(files);
        PendingSync previous = PENDING.remove(player.getUUID());
        if (previous != null) previous.close();
        ACTIVE_TRANSFERS.remove(player.getUUID());
        PendingSync pending = new PendingSync(
                player, syncId, safeFiles, completion, System.currentTimeMillis());
        PENDING.put(player.getUUID(), pending);
        List<VttAssetManifestPayload.Entry> entries = safeFiles.stream()
                .map(file -> new VttAssetManifestPayload.Entry(
                        file.category(), file.relativePath(), file.sha256(), file.size()))
                .toList();
        PacketDistributor.sendToPlayer(player, new VttAssetManifestPayload(
                syncId, serverId(), replaceScope, entries));
        VTT.LOGGER.debug("Offered {} VTT asset(s) to {} in manifest {}",
                entries.size(), player.getGameProfile().getName(), syncId);
    }

    private static List<SyncFile> collectFiles(
            AssetScope scope, boolean includeAllTokens, Path root
    ) {
        checkCancelled();
        ensureHashCacheLoaded(root);
        Path assetsRoot = root.resolve("assets").normalize();
        Path tokensRoot = root.resolve("created/tokens").normalize();
        Path mapsRoot = root.resolve("created/maps").normalize();
        Path attachmentsRoot = root.resolve("created/attachments").normalize();
        Set<String> definitionIds = scope == null ? Set.of() : scope.definitionIds();

        Map<String, SyncFile> result = new LinkedHashMap<>();
        long[] totalBytes = {0L};
        addAsset(scope == null ? null : scope.backgroundAssetId(), assetsRoot, result, totalBytes);
        if (scope != null) {
            scope.mapAssetIds().forEach(
                    assetId -> addAsset(assetId, assetsRoot, result, totalBytes));
        }

        if (Files.isDirectory(tokensRoot)) {
            try (Stream<Path> stream = Files.walk(tokensRoot)) {
                stream.filter(Files::isRegularFile).filter(path -> path.toString().toLowerCase().endsWith(".json"))
                        .peek(path -> checkCancelled())
                        .forEach(path -> collectToken(path, definitionIds, includeAllTokens,
                                tokensRoot, assetsRoot, result, totalBytes));
            } catch (IOException exception) {
                VTT.LOGGER.error("Failed to scan server VTT token definitions", exception);
            }
        }
        if (includeAllTokens && Files.isDirectory(mapsRoot)) {
            try (Stream<Path> stream = Files.walk(mapsRoot)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.toString().toLowerCase().endsWith(".json"))
                        .peek(path -> checkCancelled())
                        .forEach(path -> collectMap(
                                path, mapsRoot, assetsRoot, result, totalBytes));
            } catch (IOException exception) {
                VTT.LOGGER.error("Failed to scan server VTT map definitions", exception);
            }
        }
        if (includeAllTokens && Files.isDirectory(attachmentsRoot)) {
            try (Stream<Path> stream = Files.walk(attachmentsRoot)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.toString().toLowerCase().endsWith(".json"))
                        .peek(path -> checkCancelled())
                        .forEach(path -> collectAttachment(
                                path, attachmentsRoot, assetsRoot, result, totalBytes));
            } catch (IOException exception) {
                VTT.LOGGER.error("Failed to scan server VTT attachment definitions", exception);
            }
        }
        if (includeAllTokens) addAllLibraryFiles(assetsRoot, result, totalBytes);
        pruneRemovedHashEntries(root);
        persistHashCache();
        return new ArrayList<>(result.values());
    }

    private static void addAllLibraryFiles(Path assetsRoot, Map<String, SyncFile> result,
                                           long[] totalBytes) {
        if (!Files.isDirectory(assetsRoot)) return;
        try (Stream<Path> stream = Files.walk(assetsRoot)) {
            stream.filter(Files::isRegularFile).sorted()
                    .peek(path -> checkCancelled())
                    .forEach(path -> addFile("assets", assetsRoot, path, result, totalBytes));
        } catch (IOException exception) {
            VTT.LOGGER.error("Failed to scan full VTT asset library for master sync", exception);
        }
    }

    private static void collectToken(Path jsonFile, Set<String> ids, boolean includeAllTokens,
                                     Path tokensRoot, Path assetsRoot,
                                     Map<String, SyncFile> result, long[] totalBytes) {
        try (Reader reader = Files.newBufferedReader(jsonFile)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            String definitionId = string(json, "tokenDefinitionId");
            if (!includeAllTokens && !ids.contains(definitionId)) return;

            addFile("tokens", tokensRoot, jsonFile, result, totalBytes);
            JsonElement states = json.get("states");
            if (states != null && states.isJsonArray() && states.getAsJsonArray().size() > 0) {
                states.getAsJsonArray().forEach(element -> {
                    if (element.isJsonObject()) addAsset(string(element.getAsJsonObject(), "imageId"), assetsRoot, result, totalBytes);
                });
            } else {
                addAsset(string(json, "selectedImageId"), assetsRoot, result, totalBytes);
            }
        } catch (Exception exception) {
            if (exception instanceof CancellationException
                    || Thread.currentThread().isInterrupted()) throw new CancellationException();
            VTT.LOGGER.warn("Skipped invalid server VTT token definition: {}", jsonFile, exception);
        }
    }

    private static void collectMap(
            Path jsonFile, Path mapsRoot, Path assetsRoot,
            Map<String, SyncFile> result, long[] totalBytes
    ) {
        try (Reader reader = Files.newBufferedReader(jsonFile)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (string(json, "mapDefinitionId") == null) return;
            addFile("maps", mapsRoot, jsonFile, result, totalBytes);
            addAsset(string(json, "assetId"), assetsRoot, result, totalBytes);
        } catch (Exception exception) {
            if (exception instanceof CancellationException
                    || Thread.currentThread().isInterrupted()) throw new CancellationException();
            VTT.LOGGER.warn("Skipped invalid server VTT map definition: {}",
                    jsonFile, exception);
        }
    }

    private static void collectAttachment(
            Path jsonFile, Path attachmentsRoot, Path assetsRoot,
            Map<String, SyncFile> result, long[] totalBytes
    ) {
        try (Reader reader = Files.newBufferedReader(jsonFile)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (string(json, "attachmentDefinitionId") == null) return;
            addFile("attachments", attachmentsRoot, jsonFile, result, totalBytes);
            addAsset(string(json, "assetId"), assetsRoot, result, totalBytes);
        } catch (Exception exception) {
            if (exception instanceof CancellationException
                    || Thread.currentThread().isInterrupted()) throw new CancellationException();
            VTT.LOGGER.warn("Skipped invalid server VTT attachment definition: {}",
                    jsonFile, exception);
        }
    }

    private static void addAsset(String id, Path assetsRoot, Map<String, SyncFile> result, long[] totalBytes) {
        if (id == null || id.isBlank()) return;
        String relative = id.replace('\\', '/');
        if (relative.startsWith("library:")) relative = relative.substring("library:".length());
        while (relative.startsWith("/")) relative = relative.substring(1);
        addFile("assets", assetsRoot, assetsRoot.resolve(relative), result, totalBytes);
    }

    private static void addFile(String category, Path root, Path file, Map<String, SyncFile> result, long[] totalBytes) {
        checkCancelled();
        try {
            Path normalizedRoot = root.toAbsolutePath().normalize();
            Path normalizedFile = file.toAbsolutePath().normalize();
            if (!normalizedFile.startsWith(normalizedRoot) || !Files.isRegularFile(normalizedFile)) return;
            FileFingerprint fingerprint = fingerprint(normalizedFile);
            long size = fingerprint.size();
            if (size > VttAssetManifestPayload.MAX_FILE_BYTES
                    || totalBytes[0] + size > VttAssetManifestPayload.MAX_MANIFEST_BYTES
                    || result.size() >= VttAssetManifestPayload.MAX_FILES) {
                VTT.LOGGER.warn("Skipped oversized VTT server asset: {}", normalizedFile);
                return;
            }
            String relative = normalizedRoot.relativize(normalizedFile).toString().replace('\\', '/');
            if (relative.length() > VttAssetManifestPayload.MAX_PATH_LENGTH) {
                VTT.LOGGER.warn("Skipped VTT server asset with an oversized path: {}", normalizedFile);
                return;
            }
            String key = category + ":" + relative;
            if (!result.containsKey(key)) {
                CachedHash cachedHash = resolveHash(normalizedFile, fingerprint);
                result.put(key, new SyncFile(
                        category, relative, normalizedFile, size, cachedHash.sha256()));
                totalBytes[0] += size;
            }
        } catch (IOException exception) {
            VTT.LOGGER.warn("Could not inspect VTT server asset: {}", file, exception);
        }
    }

    public static synchronized void handleRequest(
            ServerPlayer player, VttAssetRequestPayload request
    ) {
        if (player == null || request == null) return;
        expirePending();
        PendingSync pending = PENDING.get(player.getUUID());
        if (pending == null || !pending.syncId.equals(request.syncId()) || pending.streaming) return;

        LinkedHashSet<Integer> requested = new LinkedHashSet<>();
        for (Integer index : request.missingIndices()) {
            if (index == null || index < 0 || index >= pending.files.size()
                    || !requested.add(index)) {
                VTT.LOGGER.warn("Rejected invalid VTT asset request from {}",
                        player.getGameProfile().getName());
                return;
            }
        }

        pending.start(List.copyOf(requested));
        if (requested.isEmpty()) {
            finishTransfer(player.getUUID(), pending);
            return;
        }
        ACTIVE_TRANSFERS.addLast(player.getUUID());
    }

    public static synchronized boolean hasPending(ServerPlayer player) {
        expirePending();
        return player != null && (PENDING.containsKey(player.getUUID())
                || PREPARING.containsKey(player.getUUID()));
    }

    public static synchronized void runAfterPending(ServerPlayer player, Runnable action) {
        if (player == null || action == null) return;
        expirePending();
        ManifestPreparation preparation = PREPARING.get(player.getUUID());
        if (preparation != null) {
            preparation.appendCompletion(action);
            return;
        }
        PendingSync pending = PENDING.get(player.getUUID());
        if (pending == null) {
            action.run();
            return;
        }
        pending.appendCompletion(action);
    }

    public static synchronized void tick() {
        expirePending();
        int chunks = 0;
        while (chunks < MAX_CHUNKS_PER_TICK && !ACTIVE_TRANSFERS.isEmpty()) {
            UUID playerId = ACTIVE_TRANSFERS.removeFirst();
            PendingSync pending = PENDING.get(playerId);
            if (pending == null || !pending.streaming) continue;
            boolean finished;
            try {
                finished = pending.sendNextChunk();
            } catch (IOException | RuntimeException exception) {
                pending.failed = true;
                finished = true;
                VTT.LOGGER.error("Failed to stream VTT asset sync {}", pending.syncId, exception);
            }
            chunks++;
            if (finished) finishTransfer(playerId, pending);
            else ACTIVE_TRANSFERS.addLast(playerId);
        }
    }

    public static synchronized void forget(UUID playerId) {
        if (playerId == null) return;
        PendingSync pending = PENDING.remove(playerId);
        if (pending != null) pending.close();
        ManifestPreparation preparation = PREPARING.remove(playerId);
        if (preparation != null) preparation.cancel();
        ACTIVE_TRANSFERS.remove(playerId);
    }

    public static synchronized void clear() {
        PENDING.values().forEach(PendingSync::close);
        PREPARING.values().forEach(ManifestPreparation::cancel);
        PENDING.clear();
        PREPARING.clear();
        ACTIVE_TRANSFERS.clear();
        persistHashCache();
        synchronized (HASH_CACHE) {
            HASH_CACHE.clear();
            lastHashCachePruneAt = 0L;
            hashCacheRoot = null;
            hashCacheLoaded = false;
            hashCacheDirty = false;
        }
        HASHES_IN_FLIGHT.clear();
        serverId = null;
    }

    private static void expirePending() {
        long now = System.currentTimeMillis();
        List<UUID> expiredPreparations = PREPARING.entrySet().stream()
                .filter(entry -> now - entry.getValue().createdAt > SYNC_TIMEOUT_MS)
                .map(Map.Entry::getKey).toList();
        for (UUID playerId : expiredPreparations) {
            ManifestPreparation preparation = PREPARING.remove(playerId);
            if (preparation != null) {
                preparation.cancel();
                VTT.LOGGER.warn("Expired VTT asset manifest preparation {}", preparation.syncId);
                beginFallbackSync(preparation);
            }
        }
        List<UUID> expired = PENDING.entrySet().stream()
                .filter(entry -> now - entry.getValue().lastActivityAt > SYNC_TIMEOUT_MS)
                .map(Map.Entry::getKey).toList();
        for (UUID playerId : expired) {
            PendingSync pending = PENDING.remove(playerId);
            if (pending != null) {
                pending.close();
                VTT.LOGGER.warn("Expired VTT asset sync {}", pending.syncId);
            }
            ACTIVE_TRANSFERS.remove(playerId);
        }
    }

    private static void finishTransfer(UUID playerId, PendingSync pending) {
        if (PENDING.get(playerId) != pending) return;
        PENDING.remove(playerId);
        ACTIVE_TRANSFERS.remove(playerId);
        pending.close();
        PacketDistributor.sendToPlayer(pending.player, new VttAssetSyncCompletePayload(
                pending.syncId, pending.transferredFiles));
        boolean successful = !pending.failed
                && pending.transferredFiles == pending.requestedIndices.size();
        VTT.LOGGER.info("Streamed VTT asset sync for {}: {}/{} transferred, {} cached",
                pending.player.getGameProfile().getName(), pending.transferredFiles,
                pending.requestedIndices.size(), pending.files.size() - pending.requestedIndices.size());
        if (successful && pending.completion != null) {
            try {
                pending.completion.run();
            } catch (RuntimeException exception) {
                VTT.LOGGER.error("Failed to finish VTT asset sync {}", pending.syncId, exception);
            }
        }
    }

    private static void beginFallbackSync(ManifestPreparation preparation) {
        if (preparation == null) return;
        var server = preparation.player.getServer();
        if (server == null || server.getPlayerList().getPlayer(
                preparation.player.getUUID()) != preparation.player) return;
        VTT.LOGGER.warn("Using existing client VTT cache after manifest preparation failure for {}",
                preparation.player.getGameProfile().getName());
        beginSync(preparation.player, List.of(), false,
                preparation.completion, preparation.syncId);
    }

    private static String serverId() {
        if (serverId != null) return serverId;
        Path file = FMLPaths.GAMEDIR.get()
                .resolve("config/vtt_assets/created/server_id.txt");
        try {
            if (Files.isRegularFile(file)) {
                String loaded = Files.readString(file, StandardCharsets.UTF_8).trim();
                serverId = UUID.fromString(loaded).toString();
                return serverId;
            }
        } catch (IOException | IllegalArgumentException exception) {
            VTT.LOGGER.warn("Could not load VTT server cache identity", exception);
        }
        serverId = UUID.randomUUID().toString();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, serverId, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException exception) {
            VTT.LOGGER.warn("Could not persist VTT server cache identity", exception);
        }
        return serverId;
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || !value.isJsonPrimitive() ? null : value.getAsString();
    }

    private static CachedHash resolveHash(
            Path file, FileFingerprint expectedFingerprint
    ) throws IOException {
        CachedHash cached = cachedHash(file, expectedFingerprint);
        if (cached != null) return cached;

        CompletableFuture<CachedHash> created = new CompletableFuture<>();
        CompletableFuture<CachedHash> existing = HASHES_IN_FLIGHT.putIfAbsent(file, created);
        if (existing != null) {
            CachedHash shared;
            try {
                shared = awaitHash(existing);
            } catch (CancellationException exception) {
                if (Thread.currentThread().isInterrupted()) throw exception;
                HASHES_IN_FLIGHT.remove(file, existing);
                return resolveHash(file, expectedFingerprint);
            }
            if (!shared.fingerprint().equals(expectedFingerprint)) {
                throw new IOException("VTT asset changed during shared hashing: " + file);
            }
            return shared;
        }

        try {
            String sha256 = calculateSha256(file);
            FileFingerprint completedFingerprint = fingerprint(file);
            if (!completedFingerprint.equals(expectedFingerprint)) {
                throw new IOException("VTT asset changed while hashing: " + file);
            }
            CachedHash completed = new CachedHash(completedFingerprint, sha256);
            cacheHash(file, completed);
            created.complete(completed);
            return completed;
        } catch (Throwable failure) {
            created.completeExceptionally(failure);
            return rethrowHashFailure(failure);
        } finally {
            HASHES_IN_FLIGHT.remove(file, created);
        }
    }

    private static CachedHash cachedHash(Path file, FileFingerprint fingerprint) {
        synchronized (HASH_CACHE) {
            CachedHash cached = HASH_CACHE.get(file);
            if (cached == null) return null;
            if (cached.fingerprint().equals(fingerprint)) return cached;
            HASH_CACHE.remove(file);
            hashCacheDirty = true;
            return null;
        }
    }

    private static void cacheHash(Path file, CachedHash hash) {
        synchronized (HASH_CACHE) {
            if (!hashCacheLoaded || hashCacheRoot == null || !file.startsWith(hashCacheRoot)) return;
            HASH_CACHE.put(file, hash);
            hashCacheDirty = true;
            while (HASH_CACHE.size() > MAX_HASH_CACHE_ENTRIES) {
                var iterator = HASH_CACHE.entrySet().iterator();
                if (!iterator.hasNext()) break;
                iterator.next();
                iterator.remove();
            }
        }
    }

    private static CachedHash awaitHash(
            CompletableFuture<CachedHash> future
    ) throws IOException {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException();
        } catch (ExecutionException exception) {
            return rethrowHashFailure(exception.getCause());
        }
    }

    private static CachedHash rethrowHashFailure(Throwable failure) throws IOException {
        if (failure instanceof IOException exception) throw exception;
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
        throw new IOException("Failed to hash VTT asset", failure);
    }

    private static FileFingerprint fingerprint(Path file) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                file, BasicFileAttributes.class);
        if (!attributes.isRegularFile()) throw new IOException("Not a regular file: " + file);
        return new FileFingerprint(attributes.size(), attributes.lastModifiedTime().toString(),
                attributes.fileKey() == null ? "" : attributes.fileKey().toString());
    }

    private static String calculateSha256(Path file) throws IOException {
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

    private static void pruneRemovedHashEntries(Path storageRoot) {
        long now = System.currentTimeMillis();
        List<Path> candidates;
        synchronized (HASH_CACHE) {
            if (now - lastHashCachePruneAt < HASH_CACHE_PRUNE_INTERVAL_MS) return;
            lastHashCachePruneAt = now;
            candidates = HASH_CACHE.keySet().stream()
                    .filter(path -> path.startsWith(storageRoot)).toList();
        }
        for (Path path : candidates) {
            checkCancelled();
            if (Files.isRegularFile(path)) continue;
            synchronized (HASH_CACHE) {
                if (HASH_CACHE.remove(path) != null) hashCacheDirty = true;
            }
        }
    }

    private static void ensureHashCacheLoaded(Path storageRoot) {
        Path normalizedRoot = storageRoot.toAbsolutePath().normalize();
        synchronized (HASH_CACHE) {
            if (hashCacheLoaded && normalizedRoot.equals(hashCacheRoot)) return;
            HASH_CACHE.clear();
            hashCacheRoot = normalizedRoot;
            hashCacheLoaded = true;
            hashCacheDirty = false;

            Path file = hashCacheFile(normalizedRoot);
            if (!Files.isRegularFile(file)) return;
            try {
                PersistentHashCache persisted = GSON.fromJson(
                        Files.readString(file, StandardCharsets.UTF_8), PersistentHashCache.class);
                if (persisted == null || persisted.version() != HASH_CACHE_FORMAT_VERSION
                        || persisted.entries() == null) {
                    hashCacheDirty = true;
                    return;
                }
                for (PersistentHashEntry entry : persisted.entries()) {
                    if (HASH_CACHE.size() >= MAX_HASH_CACHE_ENTRIES) {
                        hashCacheDirty = true;
                        break;
                    }
                    loadPersistentHashEntry(normalizedRoot, entry);
                }
                VTT.LOGGER.debug("Loaded {} cached VTT server asset hash(es)", HASH_CACHE.size());
            } catch (IOException | RuntimeException exception) {
                HASH_CACHE.clear();
                hashCacheDirty = true;
                VTT.LOGGER.warn("Could not load VTT server asset hash cache: {}", file, exception);
            }
        }
    }

    private static void loadPersistentHashEntry(Path storageRoot, PersistentHashEntry entry) {
        if (entry == null || entry.relativePath() == null || entry.relativePath().isBlank()
                || entry.size() < 0L || entry.lastModified() == null
                || entry.lastModified().isBlank() || !validSha256(entry.sha256())) {
            hashCacheDirty = true;
            return;
        }
        Path file = storageRoot.resolve(entry.relativePath()).toAbsolutePath().normalize();
        if (!file.startsWith(storageRoot) || !Files.isRegularFile(file)) {
            hashCacheDirty = true;
            return;
        }
        try {
            FileFingerprint actual = fingerprint(file);
            FileFingerprint persisted = new FileFingerprint(entry.size(),
                    entry.lastModified(), entry.fileKey() == null ? "" : entry.fileKey());
            if (!actual.equals(persisted)) {
                hashCacheDirty = true;
                return;
            }
            HASH_CACHE.put(file, new CachedHash(actual, entry.sha256().toLowerCase()));
        } catch (IOException exception) {
            hashCacheDirty = true;
        }
    }

    private static void persistHashCache() {
        synchronized (HASH_CACHE) {
            if (!hashCacheLoaded || !hashCacheDirty || hashCacheRoot == null) return;
            Path file = hashCacheFile(hashCacheRoot);
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            List<PersistentHashEntry> entries = new ArrayList<>(HASH_CACHE.size());
            for (Map.Entry<Path, CachedHash> entry : HASH_CACHE.entrySet()) {
                Path path = entry.getKey();
                if (!path.startsWith(hashCacheRoot)) continue;
                CachedHash hash = entry.getValue();
                entries.add(new PersistentHashEntry(
                        hashCacheRoot.relativize(path).toString().replace('\\', '/'),
                        hash.fingerprint().size(), hash.fingerprint().lastModified(),
                        hash.fingerprint().fileKey(), hash.sha256()));
            }
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(temporary,
                        GSON.toJson(new PersistentHashCache(HASH_CACHE_FORMAT_VERSION, entries)),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                moveReplacing(temporary, file);
                hashCacheDirty = false;
            } catch (IOException | RuntimeException exception) {
                VTT.LOGGER.warn("Could not persist VTT server asset hash cache: {}", file, exception);
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static Path hashCacheFile(Path storageRoot) {
        return storageRoot.resolve("created/cache/server_asset_hashes.json");
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException | UnsupportedOperationException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean validSha256(String value) {
        if (value == null || value.length() != 64) return false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!((character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f')
                    || (character >= 'A' && character <= 'F'))) return false;
        }
        return true;
    }

    private record SyncFile(
            String category, String relativePath, Path absolutePath, long size, String sha256
    ) {}

    private record FileFingerprint(long size, String lastModified, String fileKey) {}

    private record CachedHash(FileFingerprint fingerprint, String sha256) {}

    private record PersistentHashCache(int version, List<PersistentHashEntry> entries) {}

    private record PersistentHashEntry(
            String relativePath, long size, String lastModified, String fileKey, String sha256
    ) {}

    private record AssetScope(
            String backgroundAssetId,
            Set<String> mapAssetIds,
            Set<String> definitionIds
    ) {
        private AssetScope {
            mapAssetIds = mapAssetIds == null ? Set.of() : Set.copyOf(mapAssetIds);
            definitionIds = definitionIds == null ? Set.of() : Set.copyOf(definitionIds);
        }

        private static AssetScope fromScene(VttScene scene) {
            return new AssetScope(
                    scene == null ? null : scene.getBackgroundAssetId(),
                    scene == null ? Set.of() : scene.getMaps().stream()
                            .filter(java.util.Objects::nonNull)
                            .map(com.petrick.vtt.feature.tabletop.VttSceneMap::getAssetId)
                            .filter(java.util.Objects::nonNull)
                            .collect(java.util.stream.Collectors.toSet()),
                    scene == null ? Set.of() : definitionIds(scene.getObjects()));
        }

        private static AssetScope fromObjects(List<VttSceneObject> objects) {
            return new AssetScope(null, Set.of(), definitionIds(objects));
        }

        private static Set<String> definitionIds(List<VttSceneObject> objects) {
            if (objects == null || objects.isEmpty()) return Set.of();
            Set<String> result = new HashSet<>();
            for (VttSceneObject object : objects) {
                if (object != null && object.getSourceTokenDefinitionId() != null) {
                    result.add(object.getSourceTokenDefinitionId());
                }
            }
            return Set.copyOf(result);
        }
    }

    private static final class ManifestPreparation {
        private final ServerPlayer player;
        private final String syncId;
        private final boolean replaceScope;
        private final long createdAt;
        private Runnable completion;
        private Future<?> future;

        private ManifestPreparation(ServerPlayer player, String syncId, boolean replaceScope,
                                    Runnable completion, long createdAt) {
            this.player = player;
            this.syncId = syncId;
            this.replaceScope = replaceScope;
            this.completion = completion;
            this.createdAt = createdAt;
        }

        private void appendCompletion(Runnable action) {
            completion = chain(completion, action);
        }

        private void cancel() {
            if (future != null) future.cancel(true);
        }
    }

    private static final class PendingSync {
        private final ServerPlayer player;
        private final String syncId;
        private final List<SyncFile> files;
        private Runnable completion;
        private long lastActivityAt;
        private List<Integer> requestedIndices = List.of();
        private boolean streaming;
        private boolean failed;
        private int requestedPosition;
        private int transferredFiles;
        private InputStream input;
        private MessageDigest digest;
        private long fileBytesRead;
        private int chunkIndex;

        private PendingSync(ServerPlayer player, String syncId, List<SyncFile> files,
                            Runnable completion, long now) {
            this.player = player;
            this.syncId = syncId;
            this.files = files;
            this.completion = completion;
            this.lastActivityAt = now;
        }

        private void start(List<Integer> requestedIndices) {
            this.requestedIndices = requestedIndices;
            this.streaming = !requestedIndices.isEmpty();
            this.lastActivityAt = System.currentTimeMillis();
        }

        private boolean sendNextChunk() throws IOException {
            if (!streaming || requestedPosition >= requestedIndices.size()) return true;
            int fileIndex = requestedIndices.get(requestedPosition);
            SyncFile file = files.get(fileIndex);
            if (input == null) open(file);

            int expectedBytes = (int) Math.min(
                    VttAssetChunkPayload.MAX_CHUNK_BYTES, file.size - fileBytesRead);
            byte[] bytes = input.readNBytes(expectedBytes);
            if (bytes.length != expectedBytes) {
                throw new IOException("VTT asset ended before its declared size: " + file.absolutePath);
            }
            digest.update(bytes);
            int chunkCount = Math.max(1, (int) ((file.size
                    + VttAssetChunkPayload.MAX_CHUNK_BYTES - 1L)
                    / VttAssetChunkPayload.MAX_CHUNK_BYTES));
            PacketDistributor.sendToPlayer(player, new VttAssetChunkPayload(
                    syncId, fileIndex, chunkIndex, chunkCount, bytes));
            chunkIndex++;
            fileBytesRead += bytes.length;
            lastActivityAt = System.currentTimeMillis();

            if (fileBytesRead == file.size) {
                input.close();
                input = null;
                String streamedHash = HexFormat.of().formatHex(digest.digest());
                digest = null;
                if (!streamedHash.equalsIgnoreCase(file.sha256)) {
                    failed = true;
                    return true;
                }
                transferredFiles++;
                requestedPosition++;
                fileBytesRead = 0L;
                chunkIndex = 0;
            }
            return requestedPosition >= requestedIndices.size();
        }

        private void open(SyncFile file) throws IOException {
            if (!Files.isRegularFile(file.absolutePath)
                    || Files.size(file.absolutePath) != file.size) {
                throw new IOException("VTT asset changed after manifest: " + file.absolutePath);
            }
            input = Files.newInputStream(file.absolutePath);
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                close();
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
        }

        private void appendCompletion(Runnable action) {
            completion = chain(completion, action);
        }

        private void close() {
            if (input == null) return;
            try {
                input.close();
            } catch (IOException exception) {
                VTT.LOGGER.warn("Failed to close VTT asset stream {}", syncId, exception);
            } finally {
                input = null;
                digest = null;
            }
        }
    }

    private static void cancelPlayerWork(UUID playerId) {
        PendingSync pending = PENDING.remove(playerId);
        if (pending != null) pending.close();
        ManifestPreparation preparation = PREPARING.remove(playerId);
        if (preparation != null) preparation.cancel();
        ACTIVE_TRANSFERS.remove(playerId);
    }

    private static Runnable chain(Runnable previous, Runnable action) {
        if (previous == null) return action;
        return () -> {
            try {
                previous.run();
            } finally {
                action.run();
            }
        };
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null
                && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) {
            current = current.getCause();
        }
        return current;
    }
}
