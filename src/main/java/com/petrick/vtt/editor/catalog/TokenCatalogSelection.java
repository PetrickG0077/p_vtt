package com.petrick.vtt.editor.catalog;

/**
 * Guarda qual TokenDefinition está selecionada no Token Catalog.
 *
 * Por enquanto isso é apenas estado do editor.
 * Futuramente pode ser usado para:
 * - mostrar detalhes fixos
 * - criar token com botão
 * - criar com Enter
 * - editar TokenDefinition
 */
public final class TokenCatalogSelection {

    private String selectedTokenDefinitionId;

    public String getSelectedTokenDefinitionId() {
        return selectedTokenDefinitionId;
    }

    public boolean hasSelection() {
        return selectedTokenDefinitionId != null && !selectedTokenDefinitionId.isBlank();
    }

    public boolean isSelected(String tokenDefinitionId) {
        if (tokenDefinitionId == null) {
            return false;
        }

        return tokenDefinitionId.equals(selectedTokenDefinitionId);
    }

    public void select(String tokenDefinitionId) {
        if (tokenDefinitionId == null || tokenDefinitionId.isBlank()) {
            clear();
            return;
        }

        this.selectedTokenDefinitionId = tokenDefinitionId;
    }

    public void clear() {
        this.selectedTokenDefinitionId = null;
    }
}