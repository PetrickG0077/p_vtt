package com.petrick.vtt.feature.asset.animation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registro de texturas animadas carregadas.
 *
 * Além de manter as AnimatedTexture em memória, este registry
 * também é responsável por liberar as DynamicTexture associadas
 * quando uma animação é removida.
 */
public final class AnimatedTextureRegistry {

    private final Map<String, AnimatedTexture> texturesById = new HashMap<>();

    public void register(AnimatedTexture texture) {
        if (texture == null) {
            throw new IllegalArgumentException("AnimatedTexture cannot be null");
        }

        AnimatedTexture previous = texturesById.put(texture.id(), texture);

        if (previous != null && previous != texture) {
            releaseTexture(previous);
        }
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

    /**
     * Libera todas as DynamicTexture dos frames antes
     * de limpar o registry.
     */
    public void clear() {
        for (AnimatedTexture texture : texturesById.values()) {
            releaseTexture(texture);
        }

        texturesById.clear();
    }

    public int size() {
        return texturesById.size();
    }

    public Collection<AnimatedTexture> values() {
        return texturesById.values();
    }

    private void releaseTexture(AnimatedTexture texture) {
        if (texture == null) {
            return;
        }

        TextureManager textureManager =
                Minecraft.getInstance().getTextureManager();

        for (AnimatedTextureFrame frame : texture.frames()) {
            if (frame == null) {
                continue;
            }

            ResourceLocation textureLocation = frame.texture();

            if (textureLocation != null) {
                textureManager.release(textureLocation);
            }
        }
    }
}