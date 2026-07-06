package com.petrick.vtt.feature.asset;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registro simples de assets disponíveis para o VTT.
 *
 * Nesta fase, ele fica apenas em memória.
 * Futuramente poderá receber assets importados pelo usuário,
 * assets cacheados em disco e assets sincronizados pelo servidor.
 */
public final class AssetRegistry {

    private final Map<String, AssetRef> assetsById = new HashMap<>();

    public void register(AssetRef assetRef) {
        if (assetRef == null) {
            throw new IllegalArgumentException("AssetRef cannot be null");
        }

        if (assetRef.id() == null || assetRef.id().isBlank()) {
            throw new IllegalArgumentException("AssetRef id cannot be null or blank");
        }

        assetsById.put(assetRef.id(), assetRef);
    }

    public Optional<AssetRef> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(assetsById.get(id));
    }

    public AssetRef getRequired(String id) {
        return findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + id));
    }

    public boolean contains(String id) {
        return findById(id).isPresent();
    }

    public Collection<AssetRef> getAll() {
        return assetsById.values();
    }

    public int size() {
        return assetsById.size();
    }

    public void clear() {
        assetsById.clear();
    }
}