package com.petrick.vtt.network.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.petrick.vtt.VTT;
import com.petrick.vtt.feature.token.persistence.CreatedTokenSaveData;
import com.petrick.vtt.network.payload.VttTokenDefinitionUpsertPayload;
import com.petrick.vtt.network.payload.VttTokenDefinitionCommandPayload;
import com.petrick.vtt.network.payload.VttTokenDefinitionResultPayload;
import com.petrick.vtt.network.payload.VttAssetManagerChangePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
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
        if (!(context.player() instanceof ServerPlayer player) || payload == null) return;
        VttServerTabletopState state = VttServerTabletopState.get();
        if (!authorize(player, payload.requestId(), state)) return;
        if (!validRequestId(payload.requestId()) || payload.tokenJson() == null) {
            respond(player, payload.requestId(), false,
                    VttTokenDefinitionResultPayload.INVALID_REQUEST,
                    "Invalid token definition request", state.authorityRevision(), "");
            return;
        }
        if (payload.authorityRevision() != state.authorityRevision()) {
            respond(player, payload.requestId(), false,
                    VttTokenDefinitionResultPayload.STALE_REVISION,
                    "Token catalog changed on the server; resynchronizing",
                    state.authorityRevision(), "");
            return;
        }
        try {
            CreatedTokenSaveData data = GSON.fromJson(payload.tokenJson(), CreatedTokenSaveData.class);
            validate(data);
            boolean created = !loadDefinitions().containsKey(data.tokenDefinitionId);
            save(data);
            state.updateTokenDefinitionOwnership(data.tokenDefinitionId, data.player);
            state.markAssetCatalogChanged();
            String message = created ? "Token created" : "Token updated";
            respond(player, payload.requestId(), true,
                    VttTokenDefinitionResultPayload.OK, message,
                    state.authorityRevision(), data.tokenDefinitionId);
            broadcastReload(player, state, "UPSERT", message);
            VTT.LOGGER.info("Saved server VTT token definition: {}", data.tokenDefinitionId);
        } catch (RuntimeException | IOException exception) {
            respond(player, payload.requestId(), false,
                    VttTokenDefinitionResultPayload.REJECTED,
                    failureMessage(exception), state.authorityRevision(), "");
            VTT.LOGGER.error("Rejected invalid VTT token definition update from {}",
                    player.getGameProfile().getName(), exception);
        }
    }

    public static void handleCommand(VttTokenDefinitionCommandPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || payload == null) return;
        VttServerTabletopState state = VttServerTabletopState.get();
        if (!authorize(player, payload.requestId(), state)) return;
        if (!validRequestId(payload.requestId())) {
            respond(player, payload.requestId(), false,
                    VttTokenDefinitionResultPayload.INVALID_REQUEST,
                    "Invalid token request", state.authorityRevision(), "");
            return;
        }
        if (payload.authorityRevision() != state.authorityRevision()) {
            respond(player, payload.requestId(), false,
                    VttTokenDefinitionResultPayload.STALE_REVISION,
                    "Token catalog changed on the server; resynchronizing",
                    state.authorityRevision(), "");
            return;
        }
        if (payload.operation() == null || payload.definitionId() == null
                || payload.definitionId().length() > 256
                || !payload.definitionId().startsWith("user/tokens/")) {
            respond(player, payload.requestId(), false,
                    VttTokenDefinitionResultPayload.INVALID_REQUEST,
                    "Invalid token definition command", state.authorityRevision(), "");
            return;
        }

        try {
            if (VttTokenDefinitionCommandPayload.DELETE.equals(payload.operation())) {
                List<String> usages =
                        state.scenesUsingTokenDefinition(payload.definitionId());
                if (!usages.isEmpty()) {
                    respond(player, payload.requestId(), false,
                            VttTokenDefinitionResultPayload.REJECTED,
                            usages.size() == 1
                                    ? "Token is used in scene: " + usages.getFirst()
                                    : "Token is still used in " + usages.size() + " scenes",
                            state.authorityRevision(), payload.definitionId());
                    VTT.LOGGER.warn(
                            "Rejected deletion of server VTT token definition {} "
                                    + "because it is used in {}",
                            payload.definitionId(), usages);
                    return;
                }
            }
            String resultingId;
            String message;
            if (VttTokenDefinitionCommandPayload.DUPLICATE.equals(payload.operation())) {
                resultingId = duplicate(payload.definitionId());
                message = "Token duplicated";
            } else if (VttTokenDefinitionCommandPayload.DELETE.equals(payload.operation())) {
                resultingId = delete(payload.definitionId()) ? "" : null;
                message = "Token deleted";
            } else {
                respond(player, payload.requestId(), false,
                        VttTokenDefinitionResultPayload.INVALID_REQUEST,
                        "Unknown token definition command", state.authorityRevision(), "");
                return;
            }
            if (resultingId == null) {
                respond(player, payload.requestId(), false,
                        VttTokenDefinitionResultPayload.REJECTED,
                        "Could not " + (VttTokenDefinitionCommandPayload.DUPLICATE.equals(
                                payload.operation()) ? "duplicate" : "delete") + " the token",
                        state.authorityRevision(), "");
                return;
            }
            state.markAssetCatalogChanged();
            respond(player, payload.requestId(), true,
                    VttTokenDefinitionResultPayload.OK, message,
                    state.authorityRevision(), resultingId);
            broadcastReload(player, state, payload.operation(), message);
        } catch (RuntimeException | IOException exception) {
            respond(player, payload.requestId(), false,
                    VttTokenDefinitionResultPayload.REJECTED,
                    "Could not update the token definition on the server",
                    state.authorityRevision(), "");
            VTT.LOGGER.error("Failed VTT token definition command {} for {}",
                    payload.operation(), payload.definitionId(), exception);
        }
    }

    private static String duplicate(String definitionId) throws IOException {
        Map<String, TokenFile> definitions = loadDefinitions();
        TokenFile source = definitions.get(definitionId);
        if (source == null) return null;
        validate(source.data());

        CreatedTokenSaveData copy = GSON.fromJson(GSON.toJson(source.data()), CreatedTokenSaveData.class);
        String baseName = source.data().displayName + " Copy";
        copy.displayName = uniqueDisplayName(definitions, baseName);
        copy.tokenDefinitionId = uniqueDefinitionId(definitions, "user/tokens/" + sanitize(copy.displayName));
        save(copy, source.path().getParent());
        VTT.LOGGER.info("Duplicated server VTT token definition: {} -> {}",
                definitionId, copy.tokenDefinitionId);
        return copy.tokenDefinitionId;
    }

    private static boolean authorize(
            ServerPlayer player, String requestId, VttServerTabletopState state) {
        if (!VttServerPlayerEvents.isMaster(player)) {
            respond(player, requestId, false,
                    VttTokenDefinitionResultPayload.PERMISSION_DENIED,
                    "Only masters can update token definitions",
                    state.authorityRevision(), "");
            return false;
        }
        if (!VttServerRequestRateLimiter.allow(
                player, VttServerRequestRateLimiter.Category.TOKEN_DEFINITION)) {
            respond(player, requestId, false,
                    VttTokenDefinitionResultPayload.REJECTED,
                    "Too many token operations; try again shortly",
                    state.authorityRevision(), "");
            return false;
        }
        return true;
    }

    private static boolean validRequestId(String requestId) {
        return requestId != null && !requestId.isBlank() && requestId.length() <= 64;
    }

    private static void respond(
            ServerPlayer player, String requestId, boolean success,
            String code, String message, long authorityRevision, String definitionId) {
        PacketDistributor.sendToPlayer(player, new VttTokenDefinitionResultPayload(
                requestId == null ? "" : requestId, success, code, message,
                authorityRevision, definitionId == null ? "" : definitionId));
    }

    private static String failureMessage(Exception exception) {
        String message = exception == null ? null : exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Could not save the token definition on the server";
        }
        return message.length() <= 256 ? message : message.substring(0, 256);
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
        try (Stream<Path> files = Files.walk(folder)) {
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

    private static void broadcastReload(
            ServerPlayer requester,
            VttServerTabletopState state,
            String operation,
            String message
    ) {
        for (ServerPlayer connected : requester.getServer().getPlayerList().getPlayers()) {
            VttServerVisionSourceSync.markCurrentAssetsSent(connected, state);
            VttServerAssetSyncService.sendActiveSceneAssets(
                    connected, state.replicatedSceneFor(connected),
                    VttServerPlayerEvents.isMaster(connected), () -> {
                        VttServerSceneSnapshotSync.sendToPlayer(connected, state);
                        VttServerVisionSourceSync.sendToPlayer(connected, state);
                        if (connected != requester
                                && VttServerPlayerEvents.isMaster(connected)) {
                            PacketDistributor.sendToPlayer(connected,
                                    new VttAssetManagerChangePayload(
                                            state.authorityRevision(), operation, "TOKENS",
                                            requester.getGameProfile().getName(), message));
                        }
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
        TokenFile existing = loadDefinitions().get(data.tokenDefinitionId);
        Path folder = existing == null ? tokensFolder() : existing.path().getParent();
        save(data, folder);
    }

    private static void save(CreatedTokenSaveData data, Path folder) throws IOException {
        if (folder == null) folder = tokensFolder();
        Files.createDirectories(folder);
        deleteOldDefinitionFile(tokensFolder(), data.tokenDefinitionId);
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
        try (Stream<Path> files = Files.walk(folder)) {
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
