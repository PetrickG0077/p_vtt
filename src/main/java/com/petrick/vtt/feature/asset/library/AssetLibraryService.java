package com.petrick.vtt.feature.asset.library;

import com.petrick.vtt.VTT;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serviço da biblioteca de assets.
 *
 * Por enquanto ele cria a estrutura inicial de pastas.
 * Futuramente também vai escanear arquivos e registrar assets.
 */
public final class AssetLibraryService {

    private final AssetLibraryConfig config;

    private final AssetLibraryScanner scanner;

    public AssetLibraryService(AssetLibraryConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("AssetLibraryConfig cannot be null");
        }

        this.config = config;
        this.scanner = new AssetLibraryScanner(config.libraryPath());
    }

    public AssetLibraryConfig config() {
        return config;
    }

    public AssetLibraryScanResult scanLibrary() {
        return scanner.scan();
    }

    public void ensureDirectoriesExist() {
        AssetLibraryPath path = config.libraryPath();

        createDirectory(path.rootDirectory());

        for (AssetLibraryDefaultFolder folder : AssetLibraryDefaultFolder.values()) {
            createDirectory(path.defaultFolderDirectory(folder));
        }
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