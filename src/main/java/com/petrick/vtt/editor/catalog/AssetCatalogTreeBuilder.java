package com.petrick.vtt.editor.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Constrói uma árvore visual para o Asset Catalog a partir de AssetCatalogItem.
 *
 * Exemplo:
 * tokens/Kaleb/kaleb_idle.png
 *
 * vira:
 * tokens
 *   Kaleb
 *     kaleb_idle.png
 */
public final class AssetCatalogTreeBuilder {

    public AssetCatalogTreeNode.Folder build(List<AssetCatalogItem> items) {
        MutableFolder root = new MutableFolder("folder:root", "root");

        for (AssetCatalogItem item : items) {
            addItem(root, item);
        }

        return root.toImmutable();
    }

    public List<AssetCatalogVisibleRow> flattenVisibleRows(
            AssetCatalogTreeNode.Folder root,
            AssetCatalogTreeState treeState
    ) {
        List<AssetCatalogVisibleRow> rows = new ArrayList<>();

        for (AssetCatalogTreeNode child : root.children()) {
            appendVisibleRows(child, 0, treeState, rows);
        }

        return rows;
    }

    private void appendVisibleRows(
            AssetCatalogTreeNode node,
            int depth,
            AssetCatalogTreeState treeState,
            List<AssetCatalogVisibleRow> rows
    ) {
        rows.add(new AssetCatalogVisibleRow(node, depth));

        if (!(node instanceof AssetCatalogTreeNode.Folder folder)) {
            return;
        }

        if (!treeState.isExpanded(folder.id())) {
            return;
        }

        for (AssetCatalogTreeNode child : folder.children()) {
            appendVisibleRows(child, depth + 1, treeState, rows);
        }
    }

    private void addItem(MutableFolder root, AssetCatalogItem item) {
        String path = getPathForItem(item);

        String[] parts = path.split("/");

        if (parts.length == 0) {
            return;
        }

        MutableFolder currentFolder = root;

        for (int i = 0; i < parts.length - 1; i++) {
            String folderName = parts[i];

            if (folderName == null || folderName.isBlank()) {
                continue;
            }

            currentFolder = currentFolder.getOrCreateFolder(folderName);
        }

        String fileName = parts[parts.length - 1];

        if (fileName == null || fileName.isBlank()) {
            fileName = item.displayName();
        }

        currentFolder.addItem(item, fileName);
    }

    private String getPathForItem(AssetCatalogItem item) {
        if (item instanceof AssetCatalogItem.RegisteredAsset) {
            return "built-in/" + item.displayName();
        }

        return normalizePath(item.displayName());
    }

    private String normalizePath(String path) {
        return path
                .replace('\\', '/')
                .replaceAll("/+", "/");
    }

    private static final class MutableFolder {

        private final String id;

        private final String displayName;

        private final Map<String, MutableFolder> foldersByName = new LinkedHashMap<>();

        private final List<AssetCatalogTreeNode.Item> items = new ArrayList<>();

        private MutableFolder(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        private MutableFolder getOrCreateFolder(String folderName) {
            return foldersByName.computeIfAbsent(
                    folderName,
                    name -> new MutableFolder(
                            id + "/" + name,
                            name
                    )
            );
        }

        private void addItem(AssetCatalogItem item, String displayName) {
            items.add(new AssetCatalogTreeNode.Item(item, displayName));
        }

        private AssetCatalogTreeNode.Folder toImmutable() {
            List<AssetCatalogTreeNode> children = new ArrayList<>();

            List<MutableFolder> folders = new ArrayList<>(foldersByName.values());

            folders.sort(Comparator.comparing(folder -> folder.displayName.toLowerCase()));

            for (MutableFolder folder : folders) {
                children.add(folder.toImmutable());
            }

            items.sort(Comparator.comparing(item -> item.displayName().toLowerCase()));

            children.addAll(items);

            return new AssetCatalogTreeNode.Folder(
                    id,
                    displayName,
                    children
            );
        }
    }
}