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

    private final AssetCatalogTreeState treeState;

    private AssetCatalogFilter filter = AssetCatalogFilter.ALL;

    private int scrollOffset;

    public AssetCatalogController(AssetCatalogSelection selection) {
        if (selection == null) {
            throw new IllegalArgumentException("AssetCatalogSelection cannot be null");
        }

        this.selection = selection;
        this.treeState = new AssetCatalogTreeState();

        /*
         * Por padrão abrimos as pastas principais conhecidas.
         * Pastas criadas pelo usuário começam fechadas, para não poluir a lista.
         */
        treeState.expand("folder:root/built-in");
        treeState.expand("folder:root/tokens");
        treeState.expand("folder:root/maps");
        treeState.expand("folder:root/portraits");
        treeState.expand("folder:root/documents");
        treeState.expand("folder:root/items");
        treeState.expand("folder:root/misc");
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
                treeState,
                scrollOffset
        );

        Optional<AssetCatalogVisibleRow> clickedRow = overlay.findRowAt(
                registry,
                libraryScanResult,
                filter,
                treeState,
                screenHeight,
                mouseX,
                mouseY,
                scrollOffset
        );

        if (clickedRow.isPresent()) {
            AssetCatalogVisibleRow row = clickedRow.get();

            if (row.isFolder()) {
                treeState.toggle(row.folder().id());
                selection.clear();
                scrollOffset = overlay.clampScrollOffset(
                        registry,
                        libraryScanResult,
                        filter,
                        treeState,
                        scrollOffset
                );
                return true;
            }

            if (row.isItem()) {
                selection.select(row.item().catalogItem().id());
                return true;
            }
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
                treeState,
                scrollOffset,
                scrollY
        );

        return true;
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    public AssetCatalogSelection getSelection() {
        return selection;
    }

    public AssetCatalogFilter getFilter() {
        return filter;
    }

    public AssetCatalogTreeState getTreeState() {
        return treeState;
    }

    public void cycleFilter() {
        filter = filter.next();
        scrollOffset = 0;
        selection.clear();
    }
}