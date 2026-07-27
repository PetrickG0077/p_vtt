package com.petrick.vtt.editor.catalog;

/** Editor-only selection state for the reusable map catalog. */
public final class MapCatalogSelection {
    private String selectedMapDefinitionId;

    public String getSelectedMapDefinitionId() {
        return selectedMapDefinitionId;
    }

    public boolean isSelected(String mapDefinitionId) {
        return mapDefinitionId != null && mapDefinitionId.equals(selectedMapDefinitionId);
    }

    public void select(String mapDefinitionId) {
        selectedMapDefinitionId = mapDefinitionId == null || mapDefinitionId.isBlank()
                ? null : mapDefinitionId;
    }

    public void clear() {
        selectedMapDefinitionId = null;
    }
}
