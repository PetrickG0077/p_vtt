package com.petrick.vtt.feature.tabletop;

import java.util.ArrayList;
import java.util.List;

/**
 * Representa uma cena/mapa individual dentro de um Tabletop.
 *
 * Exemplo:
 * - Cidade
 * - Interior da Casa
 * - Caverna
 *
 * O mapa de fundo é uma propriedade explícita da cena,
 * não um CanvasObject normal.
 */
public final class VttScene {

    private String id;

    private String displayName;

    /**
     * ID do asset usado como mapa/fundo.
     *
     * Exemplo:
     * library:maps/cidade.png
     *
     * Pode ser null enquanto a cena ainda não possui mapa de fundo.
     */
    private String backgroundAssetId;

    private final List<VttSceneObject> objects = new ArrayList<>();

    /** Null is tolerated when loading scene JSON written before walls existed. */
    private List<VttWall> walls = new ArrayList<>();

    public VttScene() {
        this("default_scene", "Default Scene");
    }

    public VttScene(String id, String displayName) {
        this.id = normalizeId(id);
        this.displayName = normalizeDisplayName(displayName, this.id);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = normalizeId(id);
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = normalizeDisplayName(displayName, id);
    }

    public String getBackgroundAssetId() {
        return backgroundAssetId;
    }

    public void setBackgroundAssetId(String backgroundAssetId) {
        if (backgroundAssetId == null || backgroundAssetId.isBlank()) {
            this.backgroundAssetId = null;
            return;
        }

        this.backgroundAssetId = backgroundAssetId;
    }

    public List<VttSceneObject> getObjects() {
        return objects;
    }

    public void addObject(VttSceneObject object) {
        if (object == null) {
            return;
        }

        objects.add(object);
    }

    public void clearObjects() {
        objects.clear();
    }

    public List<VttWall> getWalls() {
        if (walls == null) walls = new ArrayList<>();
        return walls;
    }

    public void addWall(VttWall wall) {
        if (wall == null) return;
        getWalls().add(wall);
    }

    public boolean removeWall(String wallId) {
        if (wallId == null || wallId.isBlank()) return false;
        return getWalls().removeIf(wall -> wallId.equals(wall.getId()));
    }

    public void clearWalls() {
        getWalls().clear();
    }

    private String normalizeId(String value) {
        if (value == null || value.isBlank()) {
            return "default_scene";
        }

        return value
                .trim()
                .toLowerCase()
                .replace('\\', '/')
                .replaceAll("[^a-z0-9/_-]", "_");
    }

    private String normalizeDisplayName(String value, String fallbackId) {
        if (value == null || value.isBlank()) {
            return fallbackId == null || fallbackId.isBlank()
                    ? "Untitled Scene"
                    : fallbackId;
        }

        return value.trim();
    }
}
