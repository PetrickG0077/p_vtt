package com.petrick.vtt.feature.asset.library;

import java.nio.file.Path;

/**
 * Configuração inicial da biblioteca de assets.
 *
 * Futuramente pode virar config salva em arquivo.
 */
public final class AssetLibraryConfig {

    private static final String DEFAULT_LIBRARY_FOLDER_NAME = "vtt_assets";

    private final AssetLibraryPath libraryPath;

    public AssetLibraryConfig(Path minecraftGameDirectory) {
        if (minecraftGameDirectory == null) {
            throw new IllegalArgumentException("Minecraft game directory cannot be null");
        }

        Path rootDirectory = minecraftGameDirectory
                .resolve("config")
                .resolve(DEFAULT_LIBRARY_FOLDER_NAME);

        this.libraryPath = new AssetLibraryPath(rootDirectory);
    }

    public AssetLibraryPath libraryPath() {
        return libraryPath;
    }
}