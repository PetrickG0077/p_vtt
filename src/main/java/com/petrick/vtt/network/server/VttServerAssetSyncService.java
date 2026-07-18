package com.petrick.vtt.network.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.petrick.vtt.VTT;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.network.payload.VttAssetChunkPayload;
import com.petrick.vtt.network.payload.VttAssetSyncCompletePayload;
import com.petrick.vtt.network.payload.VttAssetSyncStartPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class VttServerAssetSyncService {
    private static final long MAX_FILE_BYTES = 256L * 1024L * 1024L;
    private static final long MAX_SCENE_BYTES = 512L * 1024L * 1024L;

    private VttServerAssetSyncService() {}

    public static void sendActiveSceneAssets(ServerPlayer player, VttScene scene) {
        sendActiveSceneAssets(player, scene, false);
    }

    public static void sendActiveSceneAssets(ServerPlayer player, VttScene scene, boolean includeAllTokens) {
        List<SyncFile> files = collectFiles(scene, includeAllTokens);
        PacketDistributor.sendToPlayer(player, new VttAssetSyncStartPayload(files.size()));
        for (SyncFile file : files) sendFile(player, file);
        PacketDistributor.sendToPlayer(player, new VttAssetSyncCompletePayload());
        VTT.LOGGER.info("Sent {} VTT scene asset files to {}", files.size(), player.getGameProfile().getName());
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
            if (size > MAX_FILE_BYTES || totalBytes[0] + size > MAX_SCENE_BYTES) {
                VTT.LOGGER.warn("Skipped oversized VTT server asset: {}", normalizedFile);
                return;
            }
            String relative = normalizedRoot.relativize(normalizedFile).toString().replace('\\', '/');
            String key = category + ":" + relative;
            if (!result.containsKey(key)) {
                result.put(key, new SyncFile(category, relative, normalizedFile));
                totalBytes[0] += size;
            }
        } catch (IOException exception) {
            VTT.LOGGER.warn("Could not inspect VTT server asset: {}", file, exception);
        }
    }

    private static void sendFile(ServerPlayer player, SyncFile file) {
        try {
            byte[] bytes = Files.readAllBytes(file.absolutePath());
            String hash = sha256(bytes);
            int chunks = Math.max(1, (bytes.length + VttAssetChunkPayload.MAX_CHUNK_BYTES - 1)
                    / VttAssetChunkPayload.MAX_CHUNK_BYTES);
            for (int index = 0; index < chunks; index++) {
                int from = index * VttAssetChunkPayload.MAX_CHUNK_BYTES;
                int to = Math.min(bytes.length, from + VttAssetChunkPayload.MAX_CHUNK_BYTES);
                PacketDistributor.sendToPlayer(player, new VttAssetChunkPayload(
                        file.category(), file.relativePath(), hash, index, chunks, Arrays.copyOfRange(bytes, from, to)
                ));
            }
        } catch (IOException exception) {
            VTT.LOGGER.error("Failed to send VTT server asset: {}", file.absolutePath(), exception);
        }
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

    private record SyncFile(String category, String relativePath, Path absolutePath) {}
}
