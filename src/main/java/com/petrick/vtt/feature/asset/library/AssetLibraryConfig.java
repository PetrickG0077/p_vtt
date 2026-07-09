package com.petrick.vtt.feature.asset.library;

import java.nio.file.Path;

/**
 * Configuração inicial da biblioteca de assets.
 *
 * Estrutura atual:
 *
 * config/vtt_assets/
 * ├─ assets/
 * │  ├─ documents/
 * │  ├─ items/
 * │  ├─ maps/
 * │  ├─ misc/
 * │  ├─ portraits/
 * │  └─ tokens/
 * │
 * └─ created/
 *    └─ tokens/
 *
 * O Asset Catalog deve ler apenas a pasta "assets".
 * A pasta "created" guarda dados internos do VTT, como JSONs de tokens criados.
 */
public final class AssetLibraryConfig {

    private static final String DEFAULT_LIBRARY_FOLDER_NAME = "vtt_assets";

    private static final String ASSETS_FOLDER_NAME = "assets";

    private static final String CREATED_FOLDER_NAME = "created";

    private static final String CREATED_TOKENS_FOLDER_NAME = "tokens";

    private final Path rootDirectory;

    private final Path assetsDirectory;

    private final Path createdDirectory;

    private final Path createdTokensDirectory;

    private final AssetLibraryPath libraryPath;

    public AssetLibraryConfig(Path minecraftGameDirectory) {
        if (minecraftGameDirectory == null) {
            throw new IllegalArgumentException("Minecraft game directory cannot be null");
        }

        this.rootDirectory = minecraftGameDirectory
                .resolve("config")
                .resolve(DEFAULT_LIBRARY_FOLDER_NAME);

        this.assetsDirectory = rootDirectory.resolve(ASSETS_FOLDER_NAME);

        this.createdDirectory = rootDirectory.resolve(CREATED_FOLDER_NAME);

        this.createdTokensDirectory = createdDirectory.resolve(CREATED_TOKENS_FOLDER_NAME);

        /*
         * Importante:
         * O AssetLibraryPath agora aponta para vtt_assets/assets,
         * não mais para vtt_assets inteiro.
         *
         * Assim o Asset Catalog não escaneia dados internos em vtt_assets/created.
         */
        this.libraryPath = new AssetLibraryPath(assetsDirectory);
    }

    public Path rootDirectory() {
        return rootDirectory;
    }

    public Path assetsDirectory() {
        return assetsDirectory;
    }

    public Path createdDirectory() {
        return createdDirectory;
    }

    public Path createdTokensDirectory() {
        return createdTokensDirectory;
    }

    public AssetLibraryPath libraryPath() {
        return libraryPath;
    }
}