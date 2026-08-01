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
        MapDefinition definition = createDefinition(
                displayName, assetId, imageWidth, imageHeight, textureMode);
        if (!save(definition)) {
            throw new IllegalStateException("Could not persist map definition");
        }
        return definition;
    }

    public static MapDefinition createDefinition(
            String displayName,
            String assetId,
            int imageWidth,
            int imageHeight,
            MapTextureMode textureMode
    ) {
        String name = normalizeName(displayName);
        String id = USER_MAP_ID_PREFIX + slug(name) + "_"
                + UUID.randomUUID().toString().substring(0, 8);
        return new MapDefinition(
                id, name, assetId, imageWidth, imageHeight, textureMode);
    }

    public static MapDefinition updateDefinition(
            MapDefinition existing,
            String displayName,
            String assetId,
            int imageWidth,
            int imageHeight,
            MapTextureMode textureMode
    ) {
        if (!isUserCreatedMap(existing)) {
            throw new IllegalArgumentException("Only user-created maps can be edited");
        }
        return new MapDefinition(
                existing.id(), normalizeName(displayName), assetId,
                imageWidth, imageHeight, textureMode);
    }

    public static MapDefinition updateAndSave(
            MapDefinition existing,
            String displayName,
            String assetId,
            int imageWidth,
            int imageHeight,
            MapTextureMode textureMode
    ) {
        if (!isUserCreatedMap(existing)) {
            throw new IllegalArgumentException("Only user-created maps can be edited");
        }
        Path previousFile = findMapFile(existing);
        Path targetFolder = previousFile == null
                ? getMapsFolder() : previousFile.getParent();
        MapDefinition updated = updateDefinition(
                existing, displayName, assetId, imageWidth, imageHeight, textureMode);
        if (!save(updated, targetFolder)) {
            throw new IllegalStateException("Could not persist edited map definition");
        }
        Path updatedFile = targetFolder.resolve(fileName(updated));
        if (previousFile != null && !previousFile.equals(updatedFile)) {
            try {
                Files.deleteIfExists(previousFile);
            } catch (IOException exception) {
                VTT.LOGGER.warn("Could not remove renamed VTT map file: {}", previousFile,
                        exception);
            }
        }
        return updated;
    }

    public static MapDefinition duplicate(MapDefinition source) {
        if (!isUserCreatedMap(source)) {
            throw new IllegalArgumentException("Only user-created maps can be duplicated");
        }
        String name = normalizeName(source.displayName() + " Copy");
        String id = USER_MAP_ID_PREFIX + slug(name) + "_"
                + UUID.randomUUID().toString().substring(0, 8);
        MapDefinition duplicate = new MapDefinition(
                id, name, source.assetId(), source.imageWidth(),
                source.imageHeight(), source.textureMode());
        Path sourceFile = findMapFile(source);
        Path targetFolder = sourceFile == null ? getMapsFolder() : sourceFile.getParent();
        if (!save(duplicate, targetFolder)) {
            throw new IllegalStateException("Could not persist duplicated map definition");
        }
        return duplicate;
    }

    public static boolean delete(MapDefinition definition) {
        if (!isUserCreatedMap(definition)) return false;
        try {
            Path file = findMapFile(definition);
            return file != null && Files.deleteIfExists(file);
        } catch (IOException exception) {
            VTT.LOGGER.error("Failed to delete created VTT map: {}", definition.id(), exception);
            return false;
        }
    }

    public static boolean isUserCreatedMap(MapDefinition definition) {
        return definition != null && definition.id().startsWith(USER_MAP_ID_PREFIX);
    }

    public static String folderOf(MapDefinition definition) {
        Path file = findMapFile(definition);
        return file == null ? "" : relativeFolder(getMapsFolder(), file.getParent());
    }

    public static boolean save(MapDefinition definition) {
        return save(definition, getMapsFolder());
    }

    private static boolean save(MapDefinition definition, Path folder) {
        if (definition == null) return false;
        if (folder == null) folder = getMapsFolder();
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
            return true;
        } catch (IOException exception) {
            VTT.LOGGER.error("Failed to save created VTT map: {}", definition.id(), exception);
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
            return false;
        }
    }

    public static void loadCreatedMaps(MapDefinitionRegistry registry) {
        loadCreatedMapsFromFolder(getMapsFolder(), registry);
    }

    public static void loadCreatedMapsFromFolder(
            Path folder, MapDefinitionRegistry registry
    ) {
        if (registry == null || folder == null) return;
        if (!Files.isDirectory(folder)) return;
        try (Stream<Path> files = Files.walk(folder)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .toLowerCase(Locale.ROOT).endsWith(".json"))
                    .forEach(path -> load(path, folder, registry));
        } catch (IOException exception) {
            VTT.LOGGER.error("Failed to load created VTT maps", exception);
        }
    }

    private static void load(
            Path file, Path rootFolder, MapDefinitionRegistry registry
    ) {
        try (Reader reader = Files.newBufferedReader(file)) {
            CreatedMapSaveData data = GSON.fromJson(reader, CreatedMapSaveData.class);
            if (!valid(data)) {
                VTT.LOGGER.warn("Ignored invalid created VTT map: {}", file);
                return;
            }
            MapDefinition definition = new MapDefinition(
                    data.mapDefinitionId(), data.displayName(), data.assetId(),
                    data.imageWidth(), data.imageHeight(),
                    MapTextureMode.normalize(data.textureMode()));
            registry.register(definition, relativeFolder(rootFolder, file.getParent()));
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

    private static Path findMapFile(MapDefinition definition) {
        if (definition == null) return null;
        Path root = getMapsFolder();
        Path expected = root.resolve(fileName(definition));
        if (Files.isRegularFile(expected)) return expected;
        if (!Files.isDirectory(root)) return null;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path candidate : files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .toLowerCase(Locale.ROOT).endsWith(".json")).toList()) {
                try (Reader reader = Files.newBufferedReader(candidate)) {
                    CreatedMapSaveData data = GSON.fromJson(reader, CreatedMapSaveData.class);
                    if (data != null
                            && definition.id().equals(data.mapDefinitionId())) {
                        return candidate;
                    }
                } catch (RuntimeException ignored) {
                }
            }
        } catch (IOException exception) {
            VTT.LOGGER.warn("Could not search VTT map folders for {}",
                    definition.id(), exception);
        }
        return null;
    }

    private static String relativeFolder(Path root, Path folder) {
        if (root == null || folder == null) return "";
        try {
            String relative = root.toAbsolutePath().normalize()
                    .relativize(folder.toAbsolutePath().normalize()).toString();
            return ".".equals(relative) ? "" : relative.replace('\\', '/');
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }
}
