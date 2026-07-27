package com.petrick.vtt.feature.map;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory registry of reusable map definitions. */
public final class MapDefinitionRegistry {
    private final Map<String, MapDefinition> definitionsById = new LinkedHashMap<>();

    public void register(MapDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("MapDefinition cannot be null");
        definitionsById.put(definition.id(), definition);
    }

    public Optional<MapDefinition> findById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return Optional.ofNullable(definitionsById.get(id));
    }

    public Collection<MapDefinition> getAll() {
        return definitionsById.values();
    }

    public void removeById(String id) {
        if (id != null) definitionsById.remove(id);
    }

    public int size() {
        return definitionsById.size();
    }

    public void clear() {
        definitionsById.clear();
    }
}
