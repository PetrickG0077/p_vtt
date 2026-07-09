package com.petrick.vtt.feature.asset.library;

import com.petrick.vtt.VTT;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serviço da biblioteca de assets.
 *
 * Ele cria a estrutura inicial de pastas e escaneia apenas a área pública de assets:
 *
 * config/vtt_assets/assets/
 *
 * Dados internos do VTT, como tokens criados em JSON, ficam em:
 *
 * config/vtt_assets/created/
 */
public final class AssetLibraryService {

    private final AssetLibraryConfig config;

    private final AssetLibraryScanner scanner;

    public AssetLibraryService(AssetLibraryConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("AssetLibraryConfig cannot be null");
        }

        this.config = config;

        /*
         * O scanner recebe config.libraryPath().
         * Como o AssetLibraryConfig agora aponta o libraryPath para:
         *
         * config/vtt_assets/assets/
         *
         * o Asset Catalog não vai mais escanear:
         *
         * config/vtt_assets/created/
         */
        this.scanner = new AssetLibraryScanner(config.libraryPath());
    }

    public AssetLibraryConfig config() {
        return config;
    }

    public AssetLibraryScanResult scanLibrary() {
        return scanner.scan();
    }

    public void ensureDirectoriesExist() {
        createDirectory(config.rootDirectory());

        createDirectory(config.assetsDirectory());

        for (AssetLibraryDefaultFolder folder : AssetLibraryDefaultFolder.values()) {
            createDirectory(config.libraryPath().defaultFolderDirectory(folder));
        }

        createDirectory(config.createdDirectory());

        createDirectory(config.createdTokensDirectory());
    }

    private void createDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
            VTT.LOGGER.info("Ensured VTT asset directory exists: {}", directory);
        } catch (IOException exception) {
            VTT.LOGGER.error("Failed to create VTT asset directory: {}", directory, exception);
        }
    }
}