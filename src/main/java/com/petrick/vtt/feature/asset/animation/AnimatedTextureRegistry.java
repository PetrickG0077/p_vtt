package com.petrick.vtt.feature.asset.animation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registro simples em memória para texturas animadas carregadas.
 */
public final class AnimatedTextureRegistry {

    private static final AnimatedTextureRegistry INSTANCE = new AnimatedTextureRegistry();

    private final Map<String, AnimatedTexture> texturesById = new HashMap<>();

    private AnimatedTextureRegistry() {}

    public static AnimatedTextureRegistry getInstance() {
        return INSTANCE;
    }

    public void register(AnimatedTexture texture) {
        if (texture == null) {
            throw new IllegalArgumentException("AnimatedTexture cannot be null");
        }

        texturesById.put(texture.id(), texture);
    }

    public Optional<AnimatedTexture> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(texturesById.get(id));
    }

    public boolean contains(String id) {
        return findById(id).isPresent();
    }

    public void clear() {
        texturesById.clear();
    }

    public int size() {
        return texturesById.size();
    }
}