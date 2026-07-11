package com.petrick.vtt.editor.overlay;

/**
 * Guarda quais painéis/overlays do editor estão visíveis.
 */
public final class PanelVisibility {

    private boolean helpVisible;

    private boolean debugVisible;

    private boolean selectionInspectorVisible;

    private boolean assetCatalogVisible;

    private boolean tokenCatalogVisible;

    private boolean sceneOutlinerVisible;

    public PanelVisibility() {
        this.helpVisible = false;
        this.debugVisible = false;
        this.selectionInspectorVisible = false;
        this.assetCatalogVisible = false;
        this.tokenCatalogVisible = false;
        this.sceneOutlinerVisible = false;
    }

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

    public void hideAssetCatalog() {
        assetCatalogVisible = false;
    }

    public void showAssetCatalog() {
        assetCatalogVisible = true;
    }

    public void hideTokenCatalog() {
        tokenCatalogVisible = false;
    }

    public void showTokenCatalog() {
        tokenCatalogVisible = true;
    }
}