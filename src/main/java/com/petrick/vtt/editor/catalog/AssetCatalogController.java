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

    private boolean searchActive;

    private String searchQuery = "";

    public AssetCatalogController(AssetCatalogSelection selection) {
        if (selection == null) {
            throw new IllegalArgumentException("AssetCatalogSelection cannot be null");
        }

        this.selection = selection;
        this.treeState = new AssetCatalogTreeState();

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
            searchActive = false;
            return false;
        }

        if (overlay.isSearchBoxAt(screenHeight, mouseX, mouseY)) {
            searchActive = true;
            return true;
        }

        scrollOffset = overlay.clampScrollOffset(
                registry,
                libraryScanResult,
                filter,
                treeState,
                searchQuery,
                scrollOffset
        );

        Optional<AssetCatalogVisibleRow> clickedRow = overlay.findRowAt(
                registry,
                libraryScanResult,
                filter,
                treeState,
                searchQuery,
                screenHeight,
                mouseX,
                mouseY,
                scrollOffset
        );

        if (clickedRow.isPresent()) {
            AssetCatalogVisibleRow row = clickedRow.get();

            searchActive = false;

            if (row.isFolder()) {
                treeState.toggle(row.folder().id());
                selection.clear();

                scrollOffset = overlay.clampScrollOffset(
                        registry,
                        libraryScanResult,
                        filter,
                        treeState,
                        searchQuery,
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
            searchActive = false;
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
                searchQuery,
                scrollOffset,
                scrollY
        );

        return true;
    }

    public boolean keyPressed(int keyCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_F
                && (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0) {
            searchActive = true;
            return true;
        }

        if (!searchActive) {
            return false;
        }

        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            searchActive = false;
            return true;
        }

        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            searchQuery = "";
            searchActive = false;
            scrollOffset = 0;
            selection.clear();
            return true;
        }

        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
            if (!searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                scrollOffset = 0;
                selection.clear();
            }

            return true;
        }

        return true;
    }

    public boolean charTyped(char codePoint) {
        if (!searchActive) {
            return false;
        }

        if (isAllowedSearchCharacter(codePoint) && searchQuery.length() < 64) {
            searchQuery += codePoint;
            scrollOffset = 0;
            selection.clear();
        }

        return true;
    }

    private boolean isAllowedSearchCharacter(char character) {
        return character >= 32 && character != 127;
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

    public boolean isSearchActive() {
        return searchActive;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void cycleFilter() {
        filter = filter.next();
        scrollOffset = 0;
        selection.clear();
    }
}