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

    public void register(TokenDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("TokenDefinition cannot be null");
        }

        definitionsById.put(definition.id(), definition);
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
    }

    public boolean contains(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        return definitionsById.containsKey(id);
    }

    public void clear() {
        definitionsById.clear();
    }
}