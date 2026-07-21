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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public final class VttServerAssetSyncService {
    private static final long SYNC_TIMEOUT_MS = 30_000L;
    private static final Map<UUID, PendingSync> PENDING = new HashMap<>();
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
        PendingSync pending = new PendingSync(
                syncId, safeFiles, completion, System.currentTimeMillis());
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

    private static boolean sendFile(
            ServerPlayer player, String syncId, int fileIndex, SyncFile file
    ) {
        try {
            byte[] bytes = Files.readAllBytes(file.absolutePath());
            if (bytes.length != file.size() || !sha256(bytes).equals(file.sha256())) {
                VTT.LOGGER.warn("VTT asset changed after manifest creation: {}", file.absolutePath());
                return false;
            }
            int chunks = Math.max(1, (bytes.length + VttAssetChunkPayload.MAX_CHUNK_BYTES - 1)
                    / VttAssetChunkPayload.MAX_CHUNK_BYTES);
            for (int index = 0; index < chunks; index++) {
                int from = index * VttAssetChunkPayload.MAX_CHUNK_BYTES;
                int to = Math.min(bytes.length, from + VttAssetChunkPayload.MAX_CHUNK_BYTES);
                PacketDistributor.sendToPlayer(player, new VttAssetChunkPayload(
                        syncId, fileIndex, index, chunks, Arrays.copyOfRange(bytes, from, to)
                ));
            }
            return true;
        } catch (IOException exception) {
            VTT.LOGGER.error("Failed to send VTT server asset: {}", file.absolutePath(), exception);
            return false;
        }
    }

    public static synchronized void handleRequest(
            ServerPlayer player, VttAssetRequestPayload request
    ) {
        if (player == null || request == null) return;
        expirePending();
        PendingSync pending = PENDING.get(player.getUUID());
        if (pending == null || !pending.syncId().equals(request.syncId())) return;

        LinkedHashSet<Integer> requested = new LinkedHashSet<>();
        for (Integer index : request.missingIndices()) {
            if (index == null || index < 0 || index >= pending.files().size()
                    || !requested.add(index)) {
                VTT.LOGGER.warn("Rejected invalid VTT asset request from {}",
                        player.getGameProfile().getName());
                return;
            }
        }

        int transferred = 0;
        for (int index : requested) {
            if (sendFile(player, pending.syncId(), index, pending.files().get(index))) {
                transferred++;
            }
        }
        PacketDistributor.sendToPlayer(player, new VttAssetSyncCompletePayload(
                pending.syncId(), transferred));
        PENDING.remove(player.getUUID());
        VTT.LOGGER.info("Incremental VTT asset sync for {}: {}/{} transferred, {} cached",
                player.getGameProfile().getName(), transferred, pending.files().size(),
                pending.files().size() - requested.size());
        if (transferred == requested.size() && pending.completion() != null) {
            try {
                pending.completion().run();
            } catch (RuntimeException exception) {
                VTT.LOGGER.error("Failed to finish VTT asset sync {}", pending.syncId(), exception);
            }
        }
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
        Runnable previous = pending.completion();
        Runnable chained = previous == null ? action : () -> {
            try {
                previous.run();
            } finally {
                action.run();
            }
        };
        PENDING.put(player.getUUID(), new PendingSync(
                pending.syncId(), pending.files(), chained, pending.createdAt()));
    }

    public static synchronized void tick() {
        expirePending();
    }

    public static synchronized void forget(UUID playerId) {
        if (playerId != null) PENDING.remove(playerId);
    }

    public static synchronized void clear() {
        PENDING.clear();
        serverId = null;
    }

    private static void expirePending() {
        long now = System.currentTimeMillis();
        PENDING.entrySet().removeIf(entry -> now - entry.getValue().createdAt() > SYNC_TIMEOUT_MS);
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

    private record SyncFile(
            String category, String relativePath, Path absolutePath, long size, String sha256
    ) {}

    private record PendingSync(
            String syncId, List<SyncFile> files, Runnable completion, long createdAt
    ) {}
}
