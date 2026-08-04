package com.petrick.vtt.editor.catalog;

import com.petrick.vtt.feature.asset.AssetRef;
import com.petrick.vtt.feature.asset.library.AssetLibraryEntry;

/**
 * Item exibido no Asset Catalog.
 *
 * Pode representar:
 * - um AssetRef já registrado no AssetRegistry;
 * - um arquivo detectado na biblioteca real de assets.
 */
public sealed interface AssetCatalogItem
        permits AssetCatalogItem.RegisteredAsset, AssetCatalogItem.LibraryFile {

    String id();

    String displayName();

    String typeName();

    record RegisteredAsset(AssetRef assetRef) implements AssetCatalogItem {

        public RegisteredAsset {
            if (assetRef == null) {
                throw new IllegalArgumentException("AssetRef cannot be null");
            }
        }

        @Override
        public String id() {
            return "registered:" + assetRef.id();
        }

        @Override
        public String displayName() {
            return assetRef.id();
        }

        @Override
        public String typeName() {
            return "Built-in Asset";
        }
    }

    record LibraryFile(AssetLibraryEntry entry) implements AssetCatalogItem {

        public LibraryFile {
            if (entry == null) {
                throw new IllegalArgumentException("AssetLibraryEntry cannot be null");
            }
        }

        @Override
        public String id() {
            return "library:" + entry.id();
        }

        @Override
        public String displayName() {
            return entry.relativePath();
        }

        @Override
        public String typeName() {
            return switch (entry.fileType()) {
                case IMAGE -> "Library Image";
                case ANIMATED_IMAGE -> "Animated Image";
                case AUDIO -> "Library Audio";
                case VIDEO -> "Library Video";
                case DOCUMENT -> "Library Document";
                case UNKNOWN -> "Library File";
            };
        }
    }
}
