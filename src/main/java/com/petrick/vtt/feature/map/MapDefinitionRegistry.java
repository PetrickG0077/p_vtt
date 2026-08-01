package com.petrick.vtt.feature.map;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory registry of reusable map definitions. */
public final class MapDefinitionRegistry {
    private final Map<String, MapDefinition> definitionsById = new LinkedHashMap<>();
    private final Map<String, String> foldersById = new LinkedHashMap<>();

    public void register(MapDefinition definition) {
        register(definition, "");
    }

    public void register(MapDefinition definition, String folderPath) {
        if (definition == null) throw new IllegalArgumentException("MapDefinition cannot be null");
        definitionsById.put(definition.id(), definition);
        foldersById.put(definition.id(), normalizeFolder(folderPath));
    }

    public Optional<MapDefinition> findById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return Optional.ofNullable(definitionsById.get(id));
    }

    public Collection<MapDefinition> getAll() {
        return definitionsById.values();
    }

    public void removeById(String id) {
        if (id != null) {
            definitionsById.remove(id);
            foldersById.remove(id);
        }
    }

    public int size() {
        return definitionsById.size();
    }

    public void clear() {
        definitionsById.clear();
        foldersById.clear();
    }

    public String folderOf(String id) {
        return id == null ? "" : foldersById.getOrDefault(id, "");
    }

    public void setFolder(String id, String folderPath) {
        if (id != null && definitionsById.containsKey(id)) {
            foldersById.put(id, normalizeFolder(folderPath));
        }
    }

    public void clearFolders() {
        foldersById.clear();
        definitionsById.keySet().forEach(id -> foldersById.put(id, ""));
    }

    private String normalizeFolder(String value) {
        if (value == null || value.isBlank() || ".".equals(value)) return "";
        return value.replace('\\', '/').replaceAll("^/+|/+$", "");
    }
}
