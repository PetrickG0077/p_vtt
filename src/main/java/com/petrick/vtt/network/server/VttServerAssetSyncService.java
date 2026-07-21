package com.petrick.vtt.network.server;

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
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
import java.util.stream.Stream;

public final class VttServerAssetSyncService {
    private static final long SYNC_TIMEOUT_MS = 120_000L;
    private static final int MAX_CHUNKS_PER_TICK = 4;
    private static final Map<UUID, PendingSync> PENDING = new HashMap<>();
    private static final Deque<UUID> ACTIVE_TRANSFERS = new ArrayDeque<>();
    private static String serverId;

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
        List<SyncFile> files = collectFiles(scene, includeAllTokens);
        beginSync(player, files, true, completion);
    }

    public static void sendVisibleTokenAssets(
            ServerPlayer player, List<VttSceneObject> visibleObjects, Runnable completion
    ) {
        if (player == null || visibleObjects == null || visibleObjects.isEmpty()) {
            if (completion != null) completion.run();
            return;
        }
        VttScene subset = new VttScene("asset_sync", "Asset Sync");
        visibleObjects.forEach(subset::addObject);
        beginSync(player, collectFiles(subset, false), false, completion);
    }

    private static synchronized void beginSync(
            ServerPlayer player, List<SyncFile> files, boolean replaceScope,
            Runnable completion
    ) {
        if (player == null) return;
        expirePending();
        String syncId = UUID.randomUUID().toString();
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

    private static List<SyncFile> collectFiles(VttScene scene, boolean includeAllTokens) {
        Path root = FMLPaths.GAMEDIR.get().resolve("config/vtt_assets");
        Path assetsRoot = root.resolve("assets").normalize();
        Path tokensRoot = root.resolve("created/tokens").normalize();
        Set<String> definitionIds = new HashSet<>();
        if (scene != null) {
            scene.getObjects().forEach(object -> {
                if (object != null && object.getSourceTokenDefinitionId() != null) {
                    definitionIds.add(object.getSourceTokenDefinitionId());
                }
            });
        }

        Map<String, SyncFile> result = new LinkedHashMap<>();
        long[] totalBytes = {0L};
        addAsset(scene == null ? null : scene.getBackgroundAssetId(), assetsRoot, result, totalBytes);

        if (Files.isDirectory(tokensRoot)) {
            try (Stream<Path> stream = Files.list(tokensRoot)) {
                stream.filter(Files::isRegularFile).filter(path -> path.toString().toLowerCase().endsWith(".json"))
                        .forEach(path -> collectToken(path, definitionIds, includeAllTokens,
                                tokensRoot, assetsRoot, result, totalBytes));
            } catch (IOException exception) {
                VTT.LOGGER.error("Failed to scan server VTT token definitions", exception);
            }
        }
        if (includeAllTokens) addAllLibraryFiles(assetsRoot, result, totalBytes);
        return new ArrayList<>(result.values());
    }

    private static void addAllLibraryFiles(Path assetsRoot, Map<String, SyncFile> result,
                                           long[] totalBytes) {
        if (!Files.isDirectory(assetsRoot)) return;
        try (Stream<Path> stream = Files.walk(assetsRoot)) {
            stream.filter(Files::isRegularFile).sorted()
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
            VTT.LOGGER.warn("Skipped invalid server VTT token definition: {}", jsonFile, exception);
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
        try {
            Path normalizedRoot = root.toAbsolutePath().normalize();
            Path normalizedFile = file.toAbsolutePath().normalize();
            if (!normalizedFile.startsWith(normalizedRoot) || !Files.isRegularFile(normalizedFile)) return;
            long size = Files.size(normalizedFile);
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
                result.put(key, new SyncFile(
                        category, relative, normalizedFile, size, sha256(normalizedFile)));
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
        return player != null && PENDING.containsKey(player.getUUID());
    }

    public static synchronized void runAfterPending(ServerPlayer player, Runnable action) {
        if (player == null || action == null) return;
        expirePending();
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
        ACTIVE_TRANSFERS.remove(playerId);
    }

    public static synchronized void clear() {
        PENDING.values().forEach(PendingSync::close);
        PENDING.clear();
        ACTIVE_TRANSFERS.clear();
        serverId = null;
    }

    private static void expirePending() {
        long now = System.currentTimeMillis();
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

    private record SyncFile(
            String category, String relativePath, Path absolutePath, long size, String sha256
    ) {}

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
            Runnable previous = completion;
            completion = previous == null ? action : () -> {
                try {
                    previous.run();
                } finally {
                    action.run();
                }
            };
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
}
