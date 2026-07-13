package com.petrick.vtt.network.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.petrick.vtt.VTT;
import com.petrick.vtt.feature.token.persistence.CreatedTokenSaveData;
import com.petrick.vtt.network.payload.VttTokenDefinitionUpsertPayload;
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
import java.util.Set;
import java.util.stream.Stream;

public final class VttServerTokenDefinitionHandler {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private VttServerTokenDefinitionHandler() {}

    public static void handle(VttTokenDefinitionUpsertPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !VttServerPlayerEvents.isMaster(player)) {
            VTT.LOGGER.warn("Rejected VTT token definition update from non-OP player");
            return;
        }
        try {
            CreatedTokenSaveData data = GSON.fromJson(payload.tokenJson(), CreatedTokenSaveData.class);
            validate(data);
            save(data);
            VttServerTabletopState state = VttServerTabletopState.get();
            state.updateTokenDefinitionOwnership(data.tokenDefinitionId, data.player);

            for (ServerPlayer connected : player.getServer().getPlayerList().getPlayers()) {
                VttServerAssetSyncService.sendActiveSceneAssets(
                        connected, state.activeScene(), VttServerPlayerEvents.isMaster(connected)
                );
                PacketDistributor.sendToPlayer(connected, state.createSnapshotPayload());
            }
            VTT.LOGGER.info("Saved server VTT token definition: {}", data.tokenDefinitionId);
        } catch (RuntimeException | IOException exception) {
            VTT.LOGGER.error("Rejected invalid VTT token definition update from {}",
                    player.getGameProfile().getName(), exception);
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
        Path folder = FMLPaths.GAMEDIR.get().resolve("config/vtt_assets/created/tokens")
                .toAbsolutePath().normalize();
        Files.createDirectories(folder);
        deleteOldDefinitionFile(folder, data.tokenDefinitionId);
        Path target = folder.resolve(sanitize(data.displayName) + ".json").normalize();
        if (!target.startsWith(folder)) throw new IOException("Invalid token file path");
        try (Writer writer = Files.newBufferedWriter(target)) {
            GSON.toJson(data, writer);
        }
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
}
