package com.petrick.vtt.network.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petrick.vtt.VTT;
import com.petrick.vtt.feature.map.MapTextureMode;
import com.petrick.vtt.feature.map.persistence.CreatedMapSaveData;
import com.petrick.vtt.network.payload.VttAssetManagerChangePayload;
import com.petrick.vtt.network.payload.VttMapDefinitionCommandPayload;
import com.petrick.vtt.network.payload.VttMapDefinitionUpsertPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

/** Authoritative persistence and lifecycle handling for reusable map definitions. */
public final class VttServerMapDefinitionHandler {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private VttServerMapDefinitionHandler() {}

    public static void handleUpsert(
            VttMapDefinitionUpsertPayload payload, IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer requester)
                || !allowed(requester) || payload == null || payload.mapJson() == null
                || payload.folder() == null) return;
        try {
            CreatedMapSaveData data = GSON.fromJson(
                    payload.mapJson(), CreatedMapSaveData.class);
            validate(data);
            Path previousFile = findMapFile(data.mapDefinitionId());
            CreatedMapSaveData previous = read(previousFile);
            Path targetFolder = previousFile == null
                    ? safeFolder(payload.folder()) : previousFile.getParent();
            Path savedFile = save(data, targetFolder);
            if (previousFile != null && !previousFile.equals(savedFile)) {
                Files.deleteIfExists(previousFile);
            }

            VttServerTabletopState state = VttServerTabletopState.get();
            if (!state.applyMapDefinitionUpdate(
                    data.mapDefinitionId(), data.displayName(), data.assetId(),
                    previous == null ? data.imageWidth() : previous.imageWidth(),
                    previous == null ? data.imageHeight() : previous.imageHeight(),
                    data.imageWidth(), data.imageHeight(),
                    MapTextureMode.normalize(data.textureMode()))) return;
            broadcastReload(requester, state, "UPSERT",
                    previous == null ? "Map created" : "Map updated");
            VTT.LOGGER.info("Saved server VTT map definition: {}",
                    data.mapDefinitionId());
        } catch (RuntimeException | IOException exception) {
            VttServerFeedback.showLimit(requester,
                    "Could not save the map definition on the server");
            VTT.LOGGER.error("Rejected invalid VTT map definition from {}",
                    requester.getGameProfile().getName(), exception);
        }
    }

    public static void handleCommand(
            VttMapDefinitionCommandPayload payload, IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer requester)
                || !allowed(requester) || payload == null
                || payload.operation() == null || payload.definitionId() == null
                || payload.definitionId().length() > 256
                || !payload.definitionId().startsWith("user/maps/")) return;
        try {
            String message;
            if (VttMapDefinitionCommandPayload.DUPLICATE.equals(payload.operation())) {
                if (!duplicate(payload.definitionId())) {
                    VttServerFeedback.showLimit(requester, "Could not duplicate the map");
                    return;
                }
                message = "Map duplicated";
            } else if (VttMapDefinitionCommandPayload.DELETE.equals(payload.operation())) {
                Path file = findMapFile(payload.definitionId());
                if (file == null || !Files.deleteIfExists(file)) {
                    VttServerFeedback.showLimit(requester, "Could not delete the map");
                    return;
                }
                message = "Map deleted";
            } else {
                return;
            }
            VttServerTabletopState state = VttServerTabletopState.get();
            state.markAssetCatalogChanged();
            broadcastReload(requester, state, payload.operation(), message);
        } catch (RuntimeException | IOException exception) {
            VttServerFeedback.showLimit(requester,
                    "Could not update the map definition on the server");
            VTT.LOGGER.error("Failed VTT map definition command {} for {}",
                    payload.operation(), payload.definitionId(), exception);
        }
    }

    private static boolean allowed(ServerPlayer requester) {
        if (!VttServerRequestRateLimiter.allow(
                requester, VttServerRequestRateLimiter.Category.MAP_DEFINITION)) return false;
        if (VttServerPlayerEvents.isMaster(requester)) return true;
        VttServerRequestRateLimiter.reject(
                requester, VttServerRequestRateLimiter.Category.MAP_DEFINITION,
                "permission denied");
        VttServerFeedback.showLimit(requester,
                "Only masters can update map definitions");
        return false;
    }

    private static void validate(CreatedMapSaveData data) {
        if (data == null || data.schemaVersion() < 1
                || data.schemaVersion() > CreatedMapSaveData.CURRENT_SCHEMA_VERSION
                || data.mapDefinitionId() == null
                || !data.mapDefinitionId().startsWith("user/maps/")
                || data.mapDefinitionId().length() > 256
                || data.displayName() == null || data.displayName().isBlank()
                || data.displayName().length() > 64
                || data.assetId() == null || data.assetId().length() > 512
                || data.imageWidth() <= 0 || data.imageWidth() > 16_000
                || data.imageHeight() <= 0 || data.imageHeight() > 16_000
                || data.textureMode() == null) {
            throw new IllegalArgumentException("Invalid map definition metadata");
        }
        Path asset = resolveAsset(data.assetId());
        String lower = asset.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".png") || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg") || lower.endsWith(".webp"))) {
            throw new IllegalArgumentException("Map must reference a static image");
        }
    }

    private static Path resolveAsset(String assetId) {
        if (assetId == null || !assetId.startsWith("library:")) {
            throw new IllegalArgumentException("Invalid map asset ID");
        }
        String relative = assetId.substring("library:".length()).replace('\\', '/');
        Path root = FMLPaths.GAMEDIR.get().resolve("config/vtt_assets/assets")
                .toAbsolutePath().normalize();
        Path file = root.resolve(relative).toAbsolutePath().normalize();
        if (relative.isBlank() || relative.startsWith("/") || relative.contains("../")
                || !file.startsWith(root) || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Map asset is not present on the server");
        }
        return file;
    }

    private static boolean duplicate(String definitionId) throws IOException {
        Path sourceFile = findMapFile(definitionId);
        CreatedMapSaveData source = read(sourceFile);
        if (source == null) return false;
        validate(source);
        String name = uniqueName(source.displayName() + " Copy");
        CreatedMapSaveData copy = new CreatedMapSaveData(
                CreatedMapSaveData.CURRENT_SCHEMA_VERSION,
                "user/maps/" + slug(name) + "_"
                        + UUID.randomUUID().toString().substring(0, 8),
                name, source.assetId(), source.imageWidth(), source.imageHeight(),
                source.textureMode());
        save(copy, sourceFile.getParent());
        return true;
    }

    private static String uniqueName(String base) throws IOException {
        String candidate = base;
        int suffix = 2;
        while (displayNameExists(candidate)) candidate = base + " " + suffix++;
        return candidate.length() <= 64 ? candidate : candidate.substring(0, 64);
    }

    private static boolean displayNameExists(String displayName) throws IOException {
        Path root = mapsRoot();
        if (!Files.isDirectory(root)) return false;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(VttServerMapDefinitionHandler::isJson).toList()) {
                CreatedMapSaveData data = read(file);
                if (data != null && displayName.equalsIgnoreCase(data.displayName())) return true;
            }
        }
        return false;
    }

    private static Path save(CreatedMapSaveData data, Path folder) throws IOException {
        Files.createDirectories(folder);
        Path target = folder.resolve(fileName(data));
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary)) {
            GSON.toJson(data, writer);
        }
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private static CreatedMapSaveData read(Path file) {
        if (file == null || !Files.isRegularFile(file)) return null;
        try (Reader reader = Files.newBufferedReader(file)) {
            return GSON.fromJson(reader, CreatedMapSaveData.class);
        } catch (RuntimeException | IOException exception) {
            return null;
        }
    }

    private static Path findMapFile(String definitionId) throws IOException {
        Path root = mapsRoot();
        if (!Files.isDirectory(root)) return null;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(VttServerMapDefinitionHandler::isJson).toList()) {
                CreatedMapSaveData data = read(file);
                if (data != null && definitionId.equals(data.mapDefinitionId())) return file;
            }
        }
        return null;
    }

    private static Path safeFolder(String relativeFolder) {
        Path root = mapsRoot();
        String relative = relativeFolder == null ? "" : relativeFolder.replace('\\', '/');
        Path folder = root.resolve(relative).toAbsolutePath().normalize();
        if (relative.startsWith("/") || relative.contains("../") || !folder.startsWith(root)) {
            throw new IllegalArgumentException("Invalid map folder");
        }
        return folder;
    }

    private static Path mapsRoot() {
        return FMLPaths.GAMEDIR.get().resolve("config/vtt_assets/created/maps")
                .toAbsolutePath().normalize();
    }

    private static boolean isJson(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    private static String fileName(CreatedMapSaveData data) {
        String idSuffix = data.mapDefinitionId().substring(
                data.mapDefinitionId().lastIndexOf('/') + 1);
        return slug(data.displayName()) + "_" + idSuffix + ".json";
    }

    private static String slug(String value) {
        String result = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return result.isBlank() ? "map" : result;
    }

    private static void broadcastReload(
            ServerPlayer requester, VttServerTabletopState state,
            String operation, String message
    ) {
        for (ServerPlayer connected : requester.getServer().getPlayerList().getPlayers()) {
            VttServerVisionSourceSync.markCurrentAssetsSent(connected, state);
            VttServerAssetSyncService.sendActiveSceneAssets(
                    connected, state.replicatedSceneFor(connected),
                    VttServerPlayerEvents.isMaster(connected), () -> {
                        VttServerSceneSnapshotSync.sendToPlayer(connected, state);
                        VttServerVisionSourceSync.sendToPlayer(connected, state);
                        if (VttServerPlayerEvents.isMaster(connected)) {
                            PacketDistributor.sendToPlayer(connected,
                                    new VttAssetManagerChangePayload(
                                            state.authorityRevision(), operation, "MAPS",
                                            requester.getGameProfile().getName(), message));
                        }
                    });
        }
    }
}
