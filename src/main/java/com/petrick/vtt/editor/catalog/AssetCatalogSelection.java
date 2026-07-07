package com.petrick.vtt.editor.catalog;

/**
 * Guarda qual AssetRef está selecionado no Asset Catalog.
 *
 * Por enquanto isso é apenas estado do editor.
 * Futuramente pode ser usado para:
 * - inspecionar assets
 * - escolher imagem para estados de token
 * - escolher imagem para mapas/mesas
 */
public final class AssetCatalogSelection {

    private String selectedAssetId;

    public String getSelectedAssetId() {
        return selectedAssetId;
    }

    public boolean hasSelection() {
        return selectedAssetId != null && !selectedAssetId.isBlank();
    }

    public boolean isSelected(String assetId) {
        if (assetId == null) {
            return false;
        }

        return assetId.equals(selectedAssetId);
    }

    public void select(String assetId) {
        if (assetId == null || assetId.isBlank()) {
            clear();
            return;
        }

        this.selectedAssetId = assetId;
    }

    public void clear() {
        this.selectedAssetId = null;
    }
}