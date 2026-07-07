package com.petrick.vtt.feature.asset.library;

import java.nio.file.Path;

/**
 * Representa um arquivo encontrado dentro da biblioteca de assets.
 *
 * Exemplo:
 * Arquivo:
 * config/vtt_assets/tokens/enemies/zombie.png
 *
 * relativePath:
 * tokens/enemies/zombie.png
 *
 * id:
 * tokens/enemies/zombie.png
 */
public record AssetLibraryEntry(
        String id,
        Path absolutePath,
        String relativePath,
        String fileName,
        String extension,
        AssetLibraryFileType fileType
) {

    public AssetLibraryEntry {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Asset library entry id cannot be null or blank");
        }

        if (absolutePath == null) {
            throw new IllegalArgumentException("Asset library entry absolute path cannot be null");
        }

        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Asset library entry relative path cannot be null or blank");
        }

        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Asset library entry file name cannot be null or blank");
        }

        if (extension == null) {
            extension = "";
        }

        if (fileType == null) {
            fileType = AssetLibraryFileType.UNKNOWN;
        }
    }
}