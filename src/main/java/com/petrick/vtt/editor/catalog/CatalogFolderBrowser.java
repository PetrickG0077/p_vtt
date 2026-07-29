package com.petrick.vtt.editor.catalog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Lightweight folder navigation shared by the scene, map and token catalogs. */
public final class CatalogFolderBrowser<T> {
    private String currentFolder = "";

    public String currentFolder() {
        return currentFolder;
    }

    public String breadcrumb(String rootLabel) {
        return currentFolder.isBlank() ? rootLabel : rootLabel + " / " + currentFolder;
    }

    public List<Row<T>> rows(
            Collection<T> items,
            Function<T, String> folder,
            Function<T, String> displayName
    ) {
        Map<String, Row<T>> folders = new LinkedHashMap<>();
        List<Row<T>> values = new ArrayList<>();
        if (!currentFolder.isBlank()) {
            values.add(new Row<>(true, parent(currentFolder), "..", null));
        }
        if (items != null) {
            for (T item : items) {
                if (item == null) continue;
                String itemFolder = normalize(folder.apply(item));
                String child = immediateChild(currentFolder, itemFolder);
                if (child != null) {
                    String path = currentFolder.isBlank()
                            ? child : currentFolder + "/" + child;
                    folders.putIfAbsent(path, new Row<>(true, path, child, null));
                } else if (itemFolder.equals(currentFolder)) {
                    values.add(new Row<>(false, "", displayName.apply(item), item));
                }
            }
        }
        List<Row<T>> sortedFolders = new ArrayList<>(folders.values());
        sortedFolders.sort(Comparator.comparing(
                Row<T>::displayName, String.CASE_INSENSITIVE_ORDER));
        values.addAll(currentFolder.isBlank() ? 0 : 1, sortedFolders);
        int itemStart = (currentFolder.isBlank() ? 0 : 1) + sortedFolders.size();
        values.subList(itemStart, values.size()).sort(Comparator.comparing(
                Row<T>::displayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(values);
    }

    public boolean open(Row<T> row) {
        if (row == null || !row.folder()) return false;
        currentFolder = normalize(row.path());
        return true;
    }

    public void reset() {
        currentFolder = "";
    }

    private String immediateChild(String current, String candidate) {
        if (candidate.equals(current)) return null;
        String prefix = current.isBlank() ? "" : current + "/";
        if (!candidate.startsWith(prefix)) return null;
        String remaining = candidate.substring(prefix.length());
        if (remaining.isBlank()) return null;
        int slash = remaining.indexOf('/');
        return slash < 0 ? remaining : remaining.substring(0, slash);
    }

    private String parent(String value) {
        int slash = value.lastIndexOf('/');
        return slash < 0 ? "" : value.substring(0, slash);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank() || ".".equals(value)) return "";
        return value.replace('\\', '/').replaceAll("/+", "/")
                .replaceAll("^/+|/+$", "");
    }

    public record Row<T>(boolean folder, String path, String displayName, T item) {
    }
}
