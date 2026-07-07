package com.petrick.vtt.editor.panel;

/**
 * Controla quais painéis auxiliares do editor estão visíveis.
 *
 * Por enquanto isso é só estado em memória.
 * Futuramente pode ser salvo em config do usuário.
 */
public final class EditorPanelVisibility {

    private boolean helpVisible = true;

    private boolean debugVisible = false;

    private boolean selectionInspectorVisible = false;

    private boolean assetCatalogVisible = false;

    private boolean tokenCatalogVisible = false;

    private boolean sceneOutlinerVisible = false;

    public boolean isHelpVisible() {
        return helpVisible;
    }

    public boolean isDebugVisible() {
        return debugVisible;
    }

    public boolean isSelectionInspectorVisible() {
        return selectionInspectorVisible;
    }

    public boolean isAssetCatalogVisible() {
        return assetCatalogVisible;
    }

    public boolean isTokenCatalogVisible() {
        return tokenCatalogVisible;
    }

    public boolean isSceneOutlinerVisible() {
        return sceneOutlinerVisible;
    }

    public void toggleHelp() {
        helpVisible = !helpVisible;
    }

    public void toggleDebug() {
        debugVisible = !debugVisible;
    }

    public void toggleSelectionInspector() {
        selectionInspectorVisible = !selectionInspectorVisible;
    }

    public void toggleAssetCatalog() {
        assetCatalogVisible = !assetCatalogVisible;
    }

    public void toggleTokenCatalog() {
        tokenCatalogVisible = !tokenCatalogVisible;
    }

    public void toggleSceneOutliner() {
        sceneOutlinerVisible = !sceneOutlinerVisible;
    }

    public void hideAllEditorPanels() {
        debugVisible = false;
        selectionInspectorVisible = false;
        assetCatalogVisible = false;
        tokenCatalogVisible = false;
        sceneOutlinerVisible = false;
    }

    public void showAllEditorPanels() {
        debugVisible = true;
        selectionInspectorVisible = true;
        assetCatalogVisible = true;
        tokenCatalogVisible = true;
        sceneOutlinerVisible = true;
    }
}