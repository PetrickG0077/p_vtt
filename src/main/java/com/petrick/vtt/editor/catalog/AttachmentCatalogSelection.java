package com.petrick.vtt.editor.catalog;

/** Editor-only selection state for reusable attachment definitions. */
public final class AttachmentCatalogSelection {
    private String selectedAttachmentDefinitionId;

    public String getSelectedAttachmentDefinitionId() {
        return selectedAttachmentDefinitionId;
    }

    public boolean isSelected(String definitionId) {
        return definitionId != null && definitionId.equals(selectedAttachmentDefinitionId);
    }

    public void select(String definitionId) {
        selectedAttachmentDefinitionId = definitionId == null || definitionId.isBlank()
                ? null : definitionId;
    }

    public void clear() {
        selectedAttachmentDefinitionId = null;
    }
}
