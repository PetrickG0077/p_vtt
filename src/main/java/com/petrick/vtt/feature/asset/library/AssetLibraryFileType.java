package com.petrick.vtt.feature.asset.library;

/**
 * Tipo básico de arquivo encontrado na biblioteca de assets.
 *
 * Por enquanto isso é usado apenas para classificação.
 */
public enum AssetLibraryFileType {

    IMAGE,
    DOCUMENT,
    UNKNOWN;

    public static AssetLibraryFileType fromExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return UNKNOWN;
        }

        String normalizedExtension = extension.toLowerCase();

        return switch (normalizedExtension) {
            case "png", "jpg", "jpeg", "webp" -> IMAGE;
            case "txt", "md", "pdf" -> DOCUMENT;
            default -> UNKNOWN;
        };
    }
}