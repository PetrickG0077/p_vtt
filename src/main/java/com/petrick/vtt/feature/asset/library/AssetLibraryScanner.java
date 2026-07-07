package com.petrick.vtt.feature.asset.library;

import com.petrick.vtt.VTT;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Scanner recursivo da biblioteca de assets.
 *
 * Ele percorre config/vtt_assets/ e detecta arquivos em qualquer subpasta
 * criada pelo usuário.
 *
 * Por enquanto ele apenas lista arquivos. Ainda não registra textura no Minecraft.
 */
public final class AssetLibraryScanner {

    private final AssetLibraryPath libraryPath;

    public AssetLibraryScanner(AssetLibraryPath libraryPath) {
        if (libraryPath == null) {
            throw new IllegalArgumentException("AssetLibraryPath cannot be null");
        }

        this.libraryPath = libraryPath;
    }

    public AssetLibraryScanResult scan() {
        Path rootDirectory = libraryPath.rootDirectory();

        if (!Files.exists(rootDirectory)) {
            return new AssetLibraryScanResult(List.of());
        }

        try (Stream<Path> pathStream = Files.walk(rootDirectory)) {
            List<AssetLibraryEntry> entries = pathStream
                    .filter(Files::isRegularFile)
                    .map(this::createEntry)
                    .sorted(Comparator.comparing(AssetLibraryEntry::relativePath))
                    .toList();

            VTT.LOGGER.info(
                    "Scanned VTT asset library. Found {} files.",
                    entries.size()
            );

            return new AssetLibraryScanResult(entries);
        } catch (IOException exception) {
            VTT.LOGGER.error("Failed to scan VTT asset library: {}", rootDirectory, exception);
            return new AssetLibraryScanResult(List.of());
        }
    }

    private AssetLibraryEntry createEntry(Path absolutePath) {
        Path rootDirectory = libraryPath.rootDirectory();

        String relativePath = normalizePath(
                rootDirectory.relativize(absolutePath)
        );

        String fileName = absolutePath.getFileName().toString();

        String extension = extractExtension(fileName);

        AssetLibraryFileType fileType = AssetLibraryFileType.fromExtension(extension);

        return new AssetLibraryEntry(
                relativePath,
                absolutePath,
                relativePath,
                fileName,
                extension,
                fileType
        );
    }

    private String normalizePath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');

        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }

        return fileName.substring(dotIndex + 1);
    }
}