package com.petrick.vtt.network.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.petrick.vtt.VTT;
import com.petrick.vtt.feature.token.persistence.CreatedTokenSaveData;
import com.petrick.vtt.network.payload.VttTokenDefinitionUpsertPayload;
import com.petrick.vtt.network.payload.VttTokenDefinitionCommandPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class VttServerTokenDefinitionHandler {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private VttServerTokenDefinitionHandler() {}

    public static void handle(VttTokenDefinitionUpsertPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!VttServerRequestRateLimiter.allow(
                player, VttServerRequestRateLimiter.Category.TOKEN_DEFINITION)) return;
        if (!VttServerPlayerEvents.isMaster(player)) {
            VttServerRequestRateLimiter.reject(
                    player, VttServerRequestRateLimiter.Category.TOKEN_DEFINITION,
                    "permission denied");
            return;
        }
        try {
            CreatedTokenSaveData data = GSON.fromJson(payload.tokenJson(), CreatedTokenSaveData.class);
            validate(data);
            save(data);
            VttServerTabletopState state = VttServerTabletopState.get();
            state.updateTokenDefinitionOwnership(data.tokenDefinitionId, data.player);

            broadcastReload(player, state);
            VTT.LOGGER.info("Saved server VTT token definition: {}", data.tokenDefinitionId);
        } catch (RuntimeException | IOException exception) {
            VTT.LOGGER.error("Rejected invalid VTT token definition update from {}",
                    player.getGameProfile().getName(), exception);
        }
    }

    public static void handleCommand(VttTokenDefinitionCommandPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!VttServerRequestRateLimiter.allow(
                player, VttServerRequestRateLimiter.Category.TOKEN_DEFINITION)) return;
        if (!VttServerPlayerEvents.isMaster(player)) {
            VttServerRequestRateLimiter.reject(
                    player, VttServerRequestRateLimiter.Category.TOKEN_DEFINITION,
                    "permission denied");
            return;
        }
        if (payload == null || payload.operation() == null || payload.definitionId() == null
                || payload.definitionId().length() > 256
                || !payload.definitionId().startsWith("user/tokens/")) {
            VttServerRequestRateLimiter.reject(
                    player, VttServerRequestRateLimiter.Category.TOKEN_DEFINITION,
                    "invalid definition command");
            return;
        }

        try {
            VttServerTabletopState state = VttServerTabletopState.get();
            if (VttTokenDefinitionCommandPayload.DELETE.equals(payload.operation())) {
                List<String> usages =
                        state.scenesUsingTokenDefinition(payload.definitionId());
                if (!usages.isEmpty()) {
                    VTT.LOGGER.warn(
                            "Rejected deletion of server VTT token definition {} "
                                    + "because it is used in {}",
                            payload.definitionId(), usages);
                    return;
                }
            }
            boolean changed = switch (payload.operation()) {
                case VttTokenDefinitionCommandPayload.DUPLICATE -> duplicate(payload.definitionId());
                case VttTokenDefinitionCommandPayload.DELETE -> delete(payload.definitionId());
                default -> false;
            };
            if (!changed) {
                VTT.LOGGER.warn("Rejected VTT token definition command {} for {}",
                        payload.operation(), payload.definitionId());
                return;
            }

            broadcastReload(player, state);
        } catch (RuntimeException | IOException exception) {
            VTT.LOGGER.error("Failed VTT token definition command {} for {}",
                    payload.operation(), payload.definitionId(), exception);
        }
    }

    private static boolean duplicate(String definitionId) throws IOException {
        Map<String, TokenFile> definitions = loadDefinitions();
        TokenFile source = definitions.get(definitionId);
        if (source == null) return false;
        validate(source.data());

        CreatedTokenSaveData copy = GSON.fromJson(GSON.toJson(source.data()), CreatedTokenSaveData.class);
        String baseName = source.data().displayName + " Copy";
        copy.displayName = uniqueDisplayName(definitions, baseName);
        copy.tokenDefinitionId = uniqueDefinitionId(definitions, "user/tokens/" + sanitize(copy.displayName));
        save(copy);
        VTT.LOGGER.info("Duplicated server VTT token definition: {} -> {}",
                definitionId, copy.tokenDefinitionId);
        return true;
    }

    private static boolean delete(String definitionId) throws IOException {
        TokenFile tokenFile = loadDefinitions().get(definitionId);
        if (tokenFile == null) return false;
        boolean deleted = Files.deleteIfExists(tokenFile.path());
        if (deleted) VTT.LOGGER.info("Deleted server VTT token definition: {}", definitionId);
        return deleted;
    }

    private static Map<String, TokenFile> loadDefinitions() throws IOException {
        Path folder = tokensFolder();
        Map<String, TokenFile> result = new LinkedHashMap<>();
        if (!Files.isDirectory(folder)) return result;
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase().endsWith(".json")).toList()) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    CreatedTokenSaveData data = GSON.fromJson(reader, CreatedTokenSaveData.class);
                    if (data != null && data.tokenDefinitionId != null) {
                        result.put(data.tokenDefinitionId, new TokenFile(file, data));
                    }
                } catch (RuntimeException exception) {
                    VTT.LOGGER.warn("Skipped invalid server VTT token file: {}", file, exception);
                }
            }
        }
        return result;
    }

    private static String uniqueDisplayName(Map<String, TokenFile> definitions, String baseName) {
        String candidate = baseName;
        int suffix = 2;
        while (hasDisplayName(definitions, candidate) || Files.exists(
                tokensFolder().resolve(sanitize(candidate) + ".json"))) {
            candidate = baseName + " " + suffix++;
        }
        return candidate;
    }

    private static boolean hasDisplayName(Map<String, TokenFile> definitions, String displayName) {
        for (TokenFile token : definitions.values()) {
            if (token.data().displayName != null
                    && token.data().displayName.equalsIgnoreCase(displayName)) return true;
        }
        return false;
    }

    private static String uniqueDefinitionId(Map<String, TokenFile> definitions, String baseId) {
        String normalizedBase = baseId.equals("user/tokens/") ? "user/tokens/new_token" : baseId;
        String candidate = normalizedBase;
        int suffix = 2;
        while (definitions.containsKey(candidate)) candidate = normalizedBase + "_" + suffix++;
        return candidate;
    }

    private static void broadcastReload(ServerPlayer requester, VttServerTabletopState state) {
        for (ServerPlayer connected : requester.getServer().getPlayerList().getPlayers()) {
            VttServerVisionSourceSync.markCurrentAssetsSent(connected, state);
            VttServerAssetSyncService.sendActiveSceneAssets(
                    connected, state.replicatedSceneFor(connected),
                    VttServerPlayerEvents.isMaster(connected), () -> {
                        VttServerSceneSnapshotSync.sendToPlayer(connected, state);
                        VttServerVisionSourceSync.sendToPlayer(connected, state);
                    });
        }
    }

    private static void validate(CreatedTokenSaveData data) {
        if (data == null || data.tokenDefinitionId == null
                || !data.tokenDefinitionId.startsWith("user/tokens/")
                || data.displayName == null || data.displayName.isBlank()
                || data.defaultWidth <= 0.0 || data.defaultHeight <= 0.0) {
            throw new JsonParseException("Invalid token definition metadata");
        }
        Set<String> stateIds = new HashSet<>();
        if (data.states == null || data.states.isEmpty()) throw new JsonParseException("Token has no states");
        Path assetsRoot = assetsRoot();
        for (CreatedTokenSaveData.StateSaveData state : data.states) {
            if (state == null || state.id == null || state.id.isBlank() || !stateIds.add(state.id)
                    || state.imageId == null || state.imageId.isBlank()) {
                throw new JsonParseException("Invalid token state");
            }
            Path image = resolveAsset(assetsRoot, state.imageId);
            if (!Files.isRegularFile(image)) {
                throw new JsonParseException("Token references asset not present on server: " + state.imageId);
            }
        }
        if (data.activeStateId == null || !stateIds.contains(data.activeStateId)) {
            throw new JsonParseException("Invalid default token state");
        }
    }

    private static void save(CreatedTokenSaveData data) throws IOException {
        Path folder = tokensFolder();
        Files.createDirectories(folder);
        deleteOldDefinitionFile(folder, data.tokenDefinitionId);
        Path target = folder.resolve(sanitize(data.displayName) + ".json").normalize();
        if (!target.startsWith(folder)) throw new IOException("Invalid token file path");
        try (Writer writer = Files.newBufferedWriter(target)) {
            GSON.toJson(data, writer);
        }
    }

    private static Path tokensFolder() {
        return FMLPaths.GAMEDIR.get().resolve("config/vtt_assets/created/tokens")
                .toAbsolutePath().normalize();
    }

    private static void deleteOldDefinitionFile(Path folder, String definitionId) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".json")).toList()) {
                boolean matches = false;
                try (Reader reader = Files.newBufferedReader(file)) {
                    CreatedTokenSaveData existing = GSON.fromJson(reader, CreatedTokenSaveData.class);
                    matches = existing != null && definitionId.equals(existing.tokenDefinitionId);
                } catch (RuntimeException ignored) {
                }
                if (matches) Files.deleteIfExists(file);
            }
        }
    }

    private static Path assetsRoot() {
        return FMLPaths.GAMEDIR.get().resolve("config/vtt_assets/assets").toAbsolutePath().normalize();
    }

    private static Path resolveAsset(Path root, String id) {
        String relative = id.replace('\\', '/');
        if (relative.startsWith("library:")) relative = relative.substring("library:".length());
        while (relative.startsWith("/")) relative = relative.substring(1);
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) throw new JsonParseException("Asset path escapes library");
        return resolved;
    }

    private static String sanitize(String value) {
        String result = value.toLowerCase().trim().replaceAll("[^a-z0-9._-]", "_").replaceAll("_+", "_");
        return result.isBlank() ? "new_token" : result;
    }

    private record TokenFile(Path path, CreatedTokenSaveData data) {}
}
