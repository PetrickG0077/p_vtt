package com.petrick.vtt.editor.catalog;

/**
 * Guarda qual item está selecionado no Asset Catalog.
 *
 * O item pode ser:
 * - asset registrado;
 * - arquivo detectado na biblioteca.
 */
public final class AssetCatalogSelection {

    private String selectedItemId;

    public String getSelectedItemId() {
        return selectedItemId;
    }

    public boolean hasSelection() {
        return selectedItemId != null && !selectedItemId.isBlank();
    }

    public boolean isSelected(String itemId) {
        if (itemId == null) {
            return false;
        }

        return itemId.equals(selectedItemId);
    }

    public void select(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            clear();
            return;
        }

        this.selectedItemId = itemId;
    }

    public void clear() {
        this.selectedItemId = null;
    }
}