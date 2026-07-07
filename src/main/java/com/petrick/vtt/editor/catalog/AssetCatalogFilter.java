package com.petrick.vtt.editor.catalog;

import com.petrick.vtt.feature.asset.library.AssetLibraryFileType;

/**
 * Filtro visual do Asset Catalog.
 */
public enum AssetCatalogFilter {

    ALL("All"),
    IMAGES("Images"),
    ANIMATED("Animated"),
    DOCUMENTS("Documents"),
    UNKNOWN("Unknown");

    private final String displayName;

    AssetCatalogFilter(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public AssetCatalogFilter next() {
        AssetCatalogFilter[] values = values();
        int nextIndex = (ordinal() + 1) % values.length;
        return values[nextIndex];
    }

    public boolean accepts(AssetCatalogItem item) {
        if (this == ALL) {
            return true;
        }

        if (item instanceof AssetCatalogItem.RegisteredAsset) {
            return this == IMAGES;
        }

        if (item instanceof AssetCatalogItem.LibraryFile libraryFile) {
            AssetLibraryFileType fileType = libraryFile.entry().fileType();

            return switch (this) {
                case ALL -> true;
                case IMAGES -> fileType == AssetLibraryFileType.IMAGE;
                case ANIMATED -> fileType == AssetLibraryFileType.ANIMATED_IMAGE;
                case DOCUMENTS -> fileType == AssetLibraryFileType.DOCUMENT;
                case UNKNOWN -> fileType == AssetLibraryFileType.UNKNOWN;
            };
        }

        return false;
    }
}