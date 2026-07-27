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

    private int schemaVersion = 1;

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

    /** Transform kept separate because the background is not a selectable canvas object. */
    private VttSceneBackgroundTransform backgroundTransform;

    /** Optional camera framing applied when the scene is opened or switched. */
    private VttSceneCameraView initialCameraView;

    /** Canvas object currently used as the persistent player vision origin. */
    private String visionSourceObjectId;

    /** Persistent player vision origins. The singular field above is retained for JSON migration. */
    private List<String> visionSourceObjectIds = new ArrayList<>();

    private final List<VttSceneObject> objects = new ArrayList<>();

    /** Locked map instances rendered below regular scene objects. */
    private List<VttSceneMap> maps = new ArrayList<>();

    /** Null is tolerated when loading scene JSON written before walls existed. */
    private List<VttWall> walls = new ArrayList<>();

    /** Null is tolerated when loading scene JSON written before doors existed. */
    private List<VttDoor> doors = new ArrayList<>();

    /** Null is tolerated when loading scene JSON written before fog existed. */
    private VttFogOfWar fogOfWar;

    /** Null is tolerated when loading scene JSON written before grid settings existed. */
    private VttSceneGrid grid;

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

    public VttSceneBackgroundTransform getBackgroundTransform() {
        if (backgroundTransform == null) {
            backgroundTransform = new VttSceneBackgroundTransform();
        }
        backgroundTransform.normalize();
        return backgroundTransform;
    }

    public void setBackgroundTransform(VttSceneBackgroundTransform backgroundTransform) {
        this.backgroundTransform = backgroundTransform == null
                ? new VttSceneBackgroundTransform()
                : backgroundTransform.copy();
    }

    public VttSceneCameraView getInitialCameraView() {
        if (initialCameraView != null) initialCameraView.normalize();
        return initialCameraView;
    }

    public void setInitialCameraView(VttSceneCameraView initialCameraView) {
        this.initialCameraView = initialCameraView == null ? null : initialCameraView.copy();
    }

    public void clearInitialCameraView() {
        initialCameraView = null;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getVisionSourceObjectId() {
        List<String> sourceIds = getVisionSourceObjectIds();
        return sourceIds.isEmpty() ? null : sourceIds.getFirst();
    }

    public void setVisionSourceObjectId(String visionSourceObjectId) {
        getVisionSourceObjectIds().clear();
        if (visionSourceObjectId == null || visionSourceObjectId.isBlank()) {
            this.visionSourceObjectId = null;
            return;
        }
        this.visionSourceObjectId = null;
        addVisionSourceObjectId(visionSourceObjectId);
    }

    public List<String> getVisionSourceObjectIds() {
        if (visionSourceObjectIds == null) visionSourceObjectIds = new ArrayList<>();
        if (visionSourceObjectId != null && !visionSourceObjectId.isBlank()) {
            String legacyId = visionSourceObjectId.trim();
            if (!visionSourceObjectIds.contains(legacyId)) visionSourceObjectIds.add(legacyId);
            visionSourceObjectId = null;
        }
        return visionSourceObjectIds;
    }

    public boolean addVisionSourceObjectId(String objectId) {
        if (objectId == null || objectId.isBlank()) return false;
        String normalizedId = objectId.trim();
        if (getVisionSourceObjectIds().contains(normalizedId)) return false;
        return getVisionSourceObjectIds().add(normalizedId);
    }

    public boolean removeVisionSourceObjectId(String objectId) {
        return objectId != null && getVisionSourceObjectIds().remove(objectId);
    }

    public void clearVisionSourceObjectIds() {
        getVisionSourceObjectIds().clear();
    }

    public List<VttSceneObject> getObjects() {
        return objects;
    }

    public List<VttSceneMap> getMaps() {
        if (maps == null) maps = new ArrayList<>();
        return maps;
    }

    public void addMap(VttSceneMap map) {
        if (map != null) getMaps().add(map);
    }

    public boolean removeMap(String mapId) {
        return mapId != null && getMaps().removeIf(
                map -> map != null && mapId.equals(map.getId()));
    }

    public void addObject(VttSceneObject object) {
        if (object == null) {
            return;
        }

        objects.add(object);
    }

    public boolean removeObject(String objectId) {
        if (objectId == null || objectId.isBlank()) return false;
        return objects.removeIf(object -> object != null && objectId.equals(object.getId()));
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

    public VttSceneGrid getGrid() {
        if (grid == null) grid = new VttSceneGrid();
        grid.normalize();
        return grid;
    }

    public void setGrid(VttSceneGrid grid) {
        this.grid = grid == null ? new VttSceneGrid() : grid;
        this.grid.normalize();
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
