package com.petrick.vtt.feature.asset.library;

import java.nio.file.Path;

/**
 * Representa os caminhos principais da biblioteca de assets do VTT.
 *
 * A biblioteca tem uma pasta raiz, mas o jogador pode criar qualquer
 * subpasta dentro dela.
 */
public final class AssetLibraryPath {

    private final Path rootDirectory;

    public AssetLibraryPath(Path rootDirectory) {
        if (rootDirectory == null) {
            throw new IllegalArgumentException("Asset library root directory cannot be null");
        }

        this.rootDirectory = rootDirectory;
    }

    public Path rootDirectory() {
        return rootDirectory;
    }

    public Path defaultFolderDirectory(AssetLibraryDefaultFolder folder) {
        if (folder == null) {
            throw new IllegalArgumentException("Asset library default folder cannot be null");
        }

        return rootDirectory.resolve(folder.folderName());
    }

    public Path resolveRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Relative path cannot be null or blank");
        }

        return rootDirectory.resolve(relativePath).normalize();
    }
}