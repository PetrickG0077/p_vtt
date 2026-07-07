package com.petrick.vtt.editor.catalog;

/**
 * Nó visual da árvore do Asset Catalog.
 *
 * Pode ser:
 * - pasta
 * - arquivo/item
 */
public sealed interface AssetCatalogTreeNode
        permits AssetCatalogTreeNode.Folder, AssetCatalogTreeNode.Item {

    String id();

    String displayName();

    record Folder(
            String id,
            String displayName,
            java.util.List<AssetCatalogTreeNode> children
    ) implements AssetCatalogTreeNode {

        public Folder {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Folder id cannot be null or blank");
            }

            if (displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException("Folder display name cannot be null or blank");
            }

            if (children == null) {
                children = java.util.List.of();
            }

            children = java.util.Collections.unmodifiableList(children);
        }
    }

    record Item(
            AssetCatalogItem catalogItem,
            String displayName
    ) implements AssetCatalogTreeNode {

        public Item {
            if (catalogItem == null) {
                throw new IllegalArgumentException("Catalog item cannot be null");
            }

            if (displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException("Item display name cannot be null or blank");
            }
        }

        @Override
        public String id() {
            return catalogItem.id();
        }
    }
}