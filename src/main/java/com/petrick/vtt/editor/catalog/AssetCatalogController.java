package com.petrick.vtt.editor.catalog;

import com.petrick.vtt.editor.overlay.AssetCatalogOverlay;
import com.petrick.vtt.feature.asset.AssetRef;
import com.petrick.vtt.feature.asset.AssetRegistry;

import java.util.Optional;

/**
 * Controla as interações do Asset Catalog.
 *
 * Responsabilidades:
 * - selecionar AssetRef
 * - limpar seleção ao clicar fora
 *
 * O Asset Catalog é apenas visualização/inspeção.
 * Ele não cria objetos no canvas e não inicia drag.
 */
public final class AssetCatalogController {

    private final AssetCatalogSelection selection;

    public AssetCatalogController(AssetCatalogSelection selection) {
        if (selection == null) {
            throw new IllegalArgumentException("AssetCatalogSelection cannot be null");
        }

        this.selection = selection;
    }

    public boolean mouseClicked(
            AssetCatalogOverlay overlay,
            AssetRegistry registry,
            boolean catalogVisible,
            int screenHeight,
            double mouseX,
            double mouseY
    ) {
        if (!catalogVisible) {
            selection.clear();
            return false;
        }

        Optional<AssetRef> clickedAsset = overlay.findAssetAt(
                registry,
                screenHeight,
                mouseX,
                mouseY
        );

        if (clickedAsset.isPresent()) {
            selection.select(clickedAsset.get().id());
            return true;
        }

        if (!overlay.containsPoint(registry, screenHeight, mouseX, mouseY)) {
            selection.clear();
        }

        return false;
    }

    public AssetCatalogSelection getSelection() {
        return selection;
    }
}