package com.petrick.vtt.feature.asset.library;

/**
 * Tipo básico de arquivo encontrado na biblioteca de assets.
 *
 * Por enquanto isso é usado apenas para classificação.
 */
public enum AssetLibraryFileType {

    IMAGE,
    ANIMATED_IMAGE,
    DOCUMENT,
    UNKNOWN;

    public static AssetLibraryFileType fromExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return UNKNOWN;
        }

        String normalizedExtension = extension.toLowerCase();

        return switch (normalizedExtension) {
            case "png", "jpg", "jpeg" -> IMAGE;
            case "gif", "apng" -> ANIMATED_IMAGE;

            /*
             * WebP pode ser estático ou animado.
             * Por enquanto vamos tratar como imagem normal.
             * Futuramente podemos inspecionar o arquivo para detectar animação real.
             */
            case "webp" -> IMAGE;

            case "txt", "md", "pdf" -> DOCUMENT;
            default -> UNKNOWN;
        };
    }
}