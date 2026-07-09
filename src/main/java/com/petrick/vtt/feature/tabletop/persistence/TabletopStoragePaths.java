package com.petrick.vtt.feature.tabletop.persistence;

import com.petrick.vtt.VTT;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Centraliza os caminhos usados para salvar Tabletop e Scenes.
 *
 * Estrutura inicial:
 *
 * config/vtt_assets/
 * └─ created/
 *    └─ tabletops/
 *       └─ default/
 *          ├─ tabletop.json
 *          └─ scenes/
 *             └─ default_scene.json
 */
public final class TabletopStoragePaths {

    private static final String CONFIG_FOLDER = "config";

    private static final String ROOT_FOLDER = "vtt_assets";

    private static final String CREATED_FOLDER = "created";

    private static final String TABLETOPS_FOLDER = "tabletops";

    private static final String SCENES_FOLDER = "scenes";

    private static final String TABLETOP_FILE_NAME = "tabletop.json";

    private static final String SCENE_FILE_EXTENSION = ".json";

    private final Path gameDirectory;

    public TabletopStoragePaths(Path gameDirectory) {
        if (gameDirectory == null) {
            throw new IllegalArgumentException("Game directory cannot be null");
        }

        this.gameDirectory = gameDirectory;
    }

    public static TabletopStoragePaths fromMinecraftDirectory() {
        return new TabletopStoragePaths(
                Minecraft.getInstance().gameDirectory.toPath()
        );
    }

    public Path rootFolder() {
        return gameDirectory
                .resolve(CONFIG_FOLDER)
                .resolve(ROOT_FOLDER);
    }

    public Path createdFolder() {
        return rootFolder()
                .resolve(CREATED_FOLDER);
    }

    public Path tabletopsFolder() {
        return createdFolder()
                .resolve(TABLETOPS_FOLDER);
    }

    public Path tabletopFolder(String tabletopId) {
        return tabletopsFolder()
                .resolve(sanitizePathSegment(tabletopId, "default"));
    }

    public Path tabletopFile(String tabletopId) {
        return tabletopFolder(tabletopId)
                .resolve(TABLETOP_FILE_NAME);
    }

    public Path scenesFolder(String tabletopId) {
        return tabletopFolder(tabletopId)
                .resolve(SCENES_FOLDER);
    }

    public Path sceneFile(String tabletopId, String sceneId) {
        return scenesFolder(tabletopId)
                .resolve(sanitizePathSegment(sceneId, "default_scene") + SCENE_FILE_EXTENSION);
    }

    public void ensureBaseFoldersExist() {
        ensureDirectoryExists(tabletopsFolder());
    }

    public void ensureTabletopFoldersExist(String tabletopId) {
        ensureDirectoryExists(tabletopFolder(tabletopId));
        ensureDirectoryExists(scenesFolder(tabletopId));
    }

    private void ensureDirectoryExists(Path folder) {
        try {
            Files.createDirectories(folder);
        } catch (IOException exception) {
            VTT.LOGGER.error(
                    "Failed to create VTT tabletop storage folder: {}",
                    folder,
                    exception
            );
        }
    }

    private String sanitizePathSegment(String value, String fallback) {
        String normalized = value;

        if (normalized == null || normalized.isBlank()) {
            normalized = fallback;
        }

        normalized = normalized
                .trim()
                .toLowerCase()
                .replace('\\', '/')
                .replaceAll("[^a-z0-9/_-]", "_");

        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (normalized.isBlank()) {
            return fallback;
        }

        return normalized;
    }
}