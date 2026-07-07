package com.petrick.vtt.editor.catalog;

import com.petrick.vtt.editor.overlay.AssetCatalogOverlay;
import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.feature.asset.library.AssetLibraryScanResult;

import java.util.Optional;

/**
 * Controla as interações do Asset Catalog.
 */
public final class AssetCatalogController {

    private final AssetCatalogSelection selection;

    private int scrollOffset;

    private AssetCatalogFilter filter = AssetCatalogFilter.ALL;

    public AssetCatalogController(AssetCatalogSelection selection) {
        if (selection == null) {
            throw new IllegalArgumentException("AssetCatalogSelection cannot be null");
        }

        this.selection = selection;
    }

    public boolean mouseClicked(
            AssetCatalogOverlay overlay,
            AssetRegistry registry,
            AssetLibraryScanResult libraryScanResult,
            boolean catalogVisible,
            int screenHeight,
            double mouseX,
            double mouseY
    ) {
        if (!catalogVisible) {
            selection.clear();
            return false;
        }

        scrollOffset = overlay.clampScrollOffset(
                registry,
                libraryScanResult,
                filter,
                scrollOffset
        );

        Optional<AssetCatalogItem> clickedItem = overlay.findItemAt(
                registry,
                libraryScanResult,
                filter,
                screenHeight,
                mouseX,
                mouseY,
                scrollOffset
        );

        if (clickedItem.isPresent()) {
            selection.select(clickedItem.get().id());
            return true;
        }

        if (!overlay.containsPoint(screenHeight, mouseX, mouseY)) {
            selection.clear();
        }

        return false;
    }

    public boolean mouseScrolled(
            AssetCatalogOverlay overlay,
            AssetRegistry registry,
            AssetLibraryScanResult libraryScanResult,
            boolean catalogVisible,
            int screenHeight,
            double mouseX,
            double mouseY,
            double scrollY
    ) {
        if (!catalogVisible) {
            return false;
        }

        if (!overlay.containsPoint(screenHeight, mouseX, mouseY)) {
            return false;
        }

        scrollOffset = overlay.scroll(
                registry,
                libraryScanResult,
                filter,
                scrollOffset,
                scrollY
        );

        return true;
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    public AssetCatalogFilter getFilter() {
        return filter;
    }

    public void cycleFilter() {
        filter = filter.next();
        scrollOffset = 0;
        selection.clear();
    }

    public AssetCatalogSelection getSelection() {
        return selection;
    }
}