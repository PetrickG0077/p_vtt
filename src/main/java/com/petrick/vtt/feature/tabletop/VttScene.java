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

    /** Null is tolerated when loading scene JSON written before doors existed. */
    private List<VttDoor> doors = new ArrayList<>();

    /** Null is tolerated when loading scene JSON written before fog existed. */
    private VttFogOfWar fogOfWar;

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
        boolean removed = getWalls().removeIf(wall -> wall != null && wallId.equals(wall.getId()));
        if (removed) {
            for (VttDoor door : getDoors()) {
                if (door != null && wallId.equals(door.getWallId())) door.setWallId(null);
            }
        }
        return removed;
    }

    public void clearWalls() {
        getWalls().clear();
    }

    public List<VttDoor> getDoors() {
        if (doors == null) doors = new ArrayList<>();
        return doors;
    }

    public void addDoor(VttDoor door) {
        if (door == null) return;
        getDoors().add(door);
    }

    public boolean removeDoor(String doorId) {
        if (doorId == null || doorId.isBlank()) return false;
        return getDoors().removeIf(door -> door != null && doorId.equals(door.getId()));
    }

    public void clearDoors() {
        getDoors().clear();
    }

    public VttFogOfWar getFogOfWar() {
        if (fogOfWar == null) fogOfWar = new VttFogOfWar();
        return fogOfWar;
    }

    public void setFogOfWar(VttFogOfWar fogOfWar) {
        this.fogOfWar = fogOfWar == null ? new VttFogOfWar() : fogOfWar;
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
