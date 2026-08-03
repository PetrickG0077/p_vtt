package com.petrick.vtt.feature.attachment;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory registry of reusable attachment definitions. */
public final class AttachmentDefinitionRegistry {
    private final Map<String, AttachmentDefinition> definitionsById = new LinkedHashMap<>();
    private final Map<String, String> foldersById = new LinkedHashMap<>();

    public void register(AttachmentDefinition definition) { register(definition, ""); }

    public void register(AttachmentDefinition definition, String folderPath) {
        if (definition == null) throw new IllegalArgumentException("Attachment cannot be null");
        definitionsById.put(definition.id(), definition);
        foldersById.put(definition.id(), normalizeFolder(folderPath));
    }

    public Optional<AttachmentDefinition> findById(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(definitionsById.get(id));
    }

    public Collection<AttachmentDefinition> getAll() { return definitionsById.values(); }
    public int size() { return definitionsById.size(); }

    public void removeById(String id) {
        definitionsById.remove(id);
        foldersById.remove(id);
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

    private String normalizeFolder(String value) {
        if (value == null || value.isBlank() || ".".equals(value)) return "";
        return value.replace('\\', '/').replaceAll("^/+|/+$", "");
    }
}
