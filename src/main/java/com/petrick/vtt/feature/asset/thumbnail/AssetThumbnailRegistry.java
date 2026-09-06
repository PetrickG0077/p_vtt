package com.petrick.vtt.feature.asset.thumbnail;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registro em memória das miniaturas carregadas.
 *
 * Também é responsável por liberar as texturas registradas
 * no TextureManager quando o registro é limpo.
 */
public final class AssetThumbnailRegistry {

    private final Map<String, AssetThumbnail> thumbnailsById = new HashMap<>();

    public void register(AssetThumbnail thumbnail) {
        if (thumbnail == null) {
            throw new IllegalArgumentException("AssetThumbnail cannot be null");
        }

        AssetThumbnail previous = thumbnailsById.put(thumbnail.id(), thumbnail);

        if (previous != null && !previous.texture().equals(thumbnail.texture())) {
            releaseTexture(previous.texture());
        }
    }

    public Optional<AssetThumbnail> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(thumbnailsById.get(id));
    }

    public boolean contains(String id) {
        return findById(id).isPresent();
    }

    public int size() {
        return thumbnailsById.size();
    }

    public Collection<AssetThumbnail> values() {
        return thumbnailsById.values();
    }

    /**
     * Libera todas as DynamicTexture pertencentes às thumbnails
     * e depois limpa o registro.
     */
    public void clear() {
        for (AssetThumbnail thumbnail : thumbnailsById.values()) {
            releaseTexture(thumbnail.texture());
        }

        thumbnailsById.clear();
    }

    private void releaseTexture(net.minecraft.resources.ResourceLocation textureLocation) {
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        textureManager.release(textureLocation);
    }
}