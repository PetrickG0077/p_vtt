package com.petrick.vtt.editor.catalog;

/**
 * Linha visível no Asset Catalog depois de aplicar:
 * - árvore de pastas
 * - pastas abertas/fechadas
 * - scroll
 */
public record AssetCatalogVisibleRow(
        AssetCatalogTreeNode node,
        int depth
) {

    public AssetCatalogVisibleRow {
        if (node == null) {
            throw new IllegalArgumentException("Visible row node cannot be null");
        }

        if (depth < 0) {
            throw new IllegalArgumentException("Visible row depth cannot be negative");
        }
    }

    public boolean isFolder() {
        return node instanceof AssetCatalogTreeNode.Folder;
    }

    public boolean isItem() {
        return node instanceof AssetCatalogTreeNode.Item;
    }

    public AssetCatalogTreeNode.Folder folder() {
        return (AssetCatalogTreeNode.Folder) node;
    }

    public AssetCatalogTreeNode.Item item() {
        return (AssetCatalogTreeNode.Item) node;
    }
}