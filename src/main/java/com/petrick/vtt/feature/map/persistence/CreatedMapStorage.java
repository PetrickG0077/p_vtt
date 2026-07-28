package com.petrick.vtt.feature.map.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petrick.vtt.VTT;
import com.petrick.vtt.feature.map.MapDefinition;
import com.petrick.vtt.feature.map.MapDefinitionRegistry;
import com.petrick.vtt.feature.map.MapTextureMode;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

/** Persistence for reusable maps under config/vtt_assets/created/maps. */
public final class CreatedMapStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String MAPS_FOLDER = "config/vtt_assets/created/maps";
    private static final String USER_MAP_ID_PREFIX = "user/maps/";

    private CreatedMapStorage() {
    }

    public static Path getMapsFolder() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(MAPS_FOLDER);
    }

    public static MapDefinition createAndSave(
            String displayName,
            String assetId,
            int imageWidth,
            int imageHeight
    ) {
        return createAndSave(
                displayName, assetId, imageWidth, imageHeight, MapTextureMode.STRETCH);
    }

    public static MapDefinition createAndSave(
            String displayName,
            String assetId,
            int imageWidth,
            int imageHeight,
            MapTextureMode textureMode
    ) {
        String name = normalizeName(displayName);
        String id = USER_MAP_ID_PREFIX + slug(name) + "_"
                + UUID.randomUUID().toString().substring(0, 8);
        MapDefinition definition = new MapDefinition(
                id, name, assetId, imageWidth, imageHeight, textureMode);
        save(definition);
        return definition;
    }

    public static void save(MapDefinition definition) {
        if (definition == null) return;
        Path folder = getMapsFolder();
        Path target = folder.resolve(fileName(definition));
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(folder);
            CreatedMapSaveData data = new CreatedMapSaveData(
                    CreatedMapSaveData.CURRENT_SCHEMA_VERSION,
                    definition.id(), definition.displayName(), definition.assetId(),
                    definition.imageWidth(), definition.imageHeight(),
                    definition.textureMode());
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(data, writer);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            VTT.LOGGER.info("Saved created VTT map: {}", definition.id());
        } catch (IOException exception) {
            VTT.LOGGER.error("Failed to save created VTT map: {}", definition.id(), exception);
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
        }
    }

    public static void loadCreatedMaps(MapDefinitionRegistry registry) {
        if (registry == null) return;
        Path folder = getMapsFolder();
        if (!Files.isDirectory(folder)) return;
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .toLowerCase(Locale.ROOT).endsWith(".json"))
                    .forEach(path -> load(path, registry));
        } catch (IOException exception) {
            VTT.LOGGER.error("Failed to load created VTT maps", exception);
        }
    }

    private static void load(Path file, MapDefinitionRegistry registry) {
        try (Reader reader = Files.newBufferedReader(file)) {
            CreatedMapSaveData data = GSON.fromJson(reader, CreatedMapSaveData.class);
            if (!valid(data)) {
                VTT.LOGGER.warn("Ignored invalid created VTT map: {}", file);
                return;
            }
            registry.register(new MapDefinition(
                    data.mapDefinitionId(), data.displayName(), data.assetId(),
                    data.imageWidth(), data.imageHeight(),
                    MapTextureMode.normalize(data.textureMode())));
        } catch (RuntimeException | IOException exception) {
            VTT.LOGGER.error("Failed to load created VTT map: {}", file, exception);
        }
    }

    private static boolean valid(CreatedMapSaveData data) {
        return data != null
                && data.schemaVersion() >= 1
                && data.schemaVersion() <= CreatedMapSaveData.CURRENT_SCHEMA_VERSION
                && data.mapDefinitionId() != null
                && data.mapDefinitionId().startsWith(USER_MAP_ID_PREFIX)
                && data.displayName() != null && !data.displayName().isBlank()
                && data.assetId() != null && !data.assetId().isBlank()
                && data.imageWidth() > 0 && data.imageWidth() <= 16_000
                && data.imageHeight() > 0 && data.imageHeight() <= 16_000;
    }

    private static String normalizeName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException("Map name cannot be blank");
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }

    private static String slug(String value) {
        String slug = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return slug.isBlank() ? "map" : slug;
    }

    private static String fileName(MapDefinition definition) {
        String suffix = definition.id().substring(
                definition.id().lastIndexOf('/') + 1);
        return slug(definition.displayName()) + "_" + suffix + ".json";
    }
}
