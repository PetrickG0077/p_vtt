package com.petrick.vtt.feature.token;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registro em memória de definições de tokens.
 *
 * Futuramente isso será alimentado por:
 * - tokens salvos em disco
 * - tokens importados
 * - tokens recebidos do servidor
 * - tokens criados pelo mestre
 */
public final class TokenDefinitionRegistry {

    private final Map<String, TokenDefinition> definitionsById = new HashMap<>();
    private final Map<String, String> foldersById = new HashMap<>();

    public void register(TokenDefinition definition) {
        register(definition, "");
    }

    public void register(TokenDefinition definition, String folderPath) {
        if (definition == null) {
            throw new IllegalArgumentException("TokenDefinition cannot be null");
        }

        definitionsById.put(definition.id(), definition);
        foldersById.put(definition.id(), normalizeFolder(folderPath));
    }

    public Optional<TokenDefinition> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(definitionsById.get(id));
    }

    public TokenDefinition getRequired(String id) {
        return findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Token definition not found: " + id));
    }

    public Collection<TokenDefinition> getAll() {
        return definitionsById.values();
    }

    public int size() {
        return definitionsById.size();
    }

    public void removeById(String id) {
        if (id == null || id.isBlank()) {
            return;
        }

        definitionsById.remove(id);
        foldersById.remove(id);
    }

    public boolean contains(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        return definitionsById.containsKey(id);
    }

    public void clear() {
        definitionsById.clear();
        foldersById.clear();
    }

    public String folderOf(String id) {
        return id == null ? "" : foldersById.getOrDefault(id, "");
    }

    private String normalizeFolder(String value) {
        if (value == null || value.isBlank() || ".".equals(value)) return "";
        return value.replace('\\', '/').replaceAll("^/+|/+$", "");
    }
}
