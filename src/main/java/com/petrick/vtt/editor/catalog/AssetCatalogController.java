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

    private int scrollOffset;

    public AssetCatalogController(AssetCatalogSelection selection) {
        if (selection == null) {
            throw new IllegalArgumentException("AssetCatalogSelection cannot be null");
        }

        this.selection = selection;
    }

    public int getScrollOffset() {
        return scrollOffset;
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

        scrollOffset = overlay.clampScrollOffset(registry, scrollOffset);

        Optional<AssetRef> clickedAsset = overlay.findAssetAt(
                registry,
                screenHeight,
                mouseX,
                mouseY,
                scrollOffset
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

    public boolean mouseScrolled(
            AssetCatalogOverlay overlay,
            AssetRegistry registry,
            boolean catalogVisible,
            int screenHeight,
            double mouseX,
            double mouseY,
            double scrollY
    ) {
        if (!catalogVisible) {
            return false;
        }

        if (!overlay.containsPoint(registry, screenHeight, mouseX, mouseY)) {
            return false;
        }

        scrollOffset = overlay.scroll(
                registry,
                scrollOffset,
                scrollY
        );

        return true;
    }

    public AssetCatalogSelection getSelection() {
        return selection;
    }
}