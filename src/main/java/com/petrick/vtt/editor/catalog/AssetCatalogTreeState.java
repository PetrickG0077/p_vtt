package com.petrick.vtt.editor.catalog;

import java.util.HashSet;
import java.util.Set;

/**
 * Guarda quais pastas da árvore do Asset Catalog estão abertas.
 */
public final class AssetCatalogTreeState {

    private final Set<String> expandedFolderIds = new HashSet<>();

    public boolean isExpanded(String folderId) {
        return expandedFolderIds.contains(folderId);
    }

    public void expand(String folderId) {
        if (folderId == null || folderId.isBlank()) {
            return;
        }

        expandedFolderIds.add(folderId);
    }

    public void collapse(String folderId) {
        if (folderId == null || folderId.isBlank()) {
            return;
        }

        expandedFolderIds.remove(folderId);
    }

    public void toggle(String folderId) {
        if (folderId == null || folderId.isBlank()) {
            return;
        }

        if (isExpanded(folderId)) {
            collapse(folderId);
        } else {
            expand(folderId);
        }
    }

    public void clear() {
        expandedFolderIds.clear();
    }
}