package com.petrick.vtt.feature.asset.thumbnail;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registro em memória das miniaturas carregadas.
 */
public final class AssetThumbnailRegistry {

    private final Map<String, AssetThumbnail> thumbnailsById = new HashMap<>();

    public void register(AssetThumbnail thumbnail) {
        if (thumbnail == null) {
            throw new IllegalArgumentException("AssetThumbnail cannot be null");
        }

        thumbnailsById.put(thumbnail.id(), thumbnail);
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

    public void clear() {
        thumbnailsById.clear();
    }
}