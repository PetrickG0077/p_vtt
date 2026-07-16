package com.petrick.vtt.network.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petrick.vtt.VTT;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttTabletop;
import com.petrick.vtt.feature.tabletop.persistence.TabletopStorage;
import com.petrick.vtt.feature.tabletop.persistence.TabletopStoragePaths;
import com.petrick.vtt.network.payload.VttSceneSnapshotPayload;
import net.neoforged.fml.loading.FMLPaths;
import com.petrick.vtt.feature.tabletop.VttSceneObject;
import com.petrick.vtt.network.payload.VttTokenTransformRequestPayload;
import com.petrick.vtt.network.payload.VttTokenTransformUpdatePayload;
import com.google.gson.reflect.TypeToken;
import com.petrick.vtt.feature.tabletop.VttDoor;
import com.petrick.vtt.feature.tabletop.VttFogOfWar;
import com.petrick.vtt.feature.tabletop.VttWall;
import com.petrick.vtt.network.payload.VttEnvironmentStateRequestPayload;
import com.petrick.vtt.network.payload.VttEnvironmentStateUpdatePayload;

import java.lang.reflect.Type;
import java.util.List;

public final class VttServerTabletopState {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Type DOOR_LIST_TYPE = new TypeToken<List<VttDoor>>() {}.getType();
    private static final Type WALL_LIST_TYPE = new TypeToken<List<VttWall>>() {}.getType();
    private static final Type VISION_LIST_TYPE = new TypeToken<List<VisionState>>() {}.getType();
    private static VttServerTabletopState instance;

    private final VttTabletop tabletop;
    private final VttScene activeScene;
    private final TabletopStorage storage;

    private VttServerTabletopState() {
        TabletopStoragePaths paths = new TabletopStoragePaths(FMLPaths.GAMEDIR.get());
        paths.ensureBaseFoldersExist();
        paths.ensureTabletopFoldersExist("default");

        this.storage = new TabletopStorage(paths);
        this.tabletop = storage.loadOrCreateDefaultTabletop();
        this.activeScene = storage.loadOrCreateActiveScene(tabletop);
    }

    public static synchronized VttServerTabletopState get() {
        if (instance == null) {
            instance = new VttServerTabletopState();
        }
        return instance;
    }

    public VttSceneSnapshotPayload createSnapshotPayload() {
        return new VttSceneSnapshotPayload(GSON.toJson(tabletop), GSON.toJson(activeScene));
    }

    public VttScene activeScene() {
        return activeScene;
    }

    public synchronized void updateTokenDefinitionOwnership(String definitionId, String ownerId) {
        if (definitionId == null || definitionId.isBlank()) return;
        activeScene.getObjects().stream()
                .filter(object -> object != null && definitionId.equals(object.getSourceTokenDefinitionId()))
                .forEach(object -> object.setOwnerId(ownerId));
        storage.saveScene(tabletop.getId(), activeScene);
    }

    public synchronized VttTokenTransformUpdatePayload applyTokenTransform(
            VttTokenTransformRequestPayload request, String playerId, boolean master
    ) {
        if (request == null || playerId == null || !valid(request)) return null;
        VttSceneObject object = activeScene.getObjects().stream()
                .filter(candidate -> candidate != null && request.objectId().equals(candidate.getId()))
                .findFirst().orElse(null);
        if (object == null || (!master && !playerId.equals(object.getOwnerId()))) return null;

        object.getTransform().setX(request.x());
        object.getTransform().setY(request.y());
        object.getTransform().setRotationDegrees(normalizeRotation(request.rotationDegrees()));
        object.getState().setFlippedHorizontally(request.flippedHorizontally());
        object.getState().setActiveStateId(request.activeStateId());
        storage.saveScene(tabletop.getId(), activeScene);

        return new VttTokenTransformUpdatePayload(object.getId(), object.getTransform().getX(),
                object.getTransform().getY(), object.getTransform().getRotationDegrees(),
                object.getState().isFlippedHorizontally(), object.getState().getActiveStateId(), playerId, true);
    }

    public synchronized VttTokenTransformUpdatePayload currentTokenTransform(String objectId, String playerId) {
        if (objectId == null || objectId.isBlank()) return null;
        VttSceneObject object = activeScene.getObjects().stream()
                .filter(candidate -> candidate != null && objectId.equals(candidate.getId()))
                .findFirst().orElse(null);
        if (object == null) return null;
        return new VttTokenTransformUpdatePayload(object.getId(), object.getTransform().getX(),
                object.getTransform().getY(), object.getTransform().getRotationDegrees(),
                object.getState().isFlippedHorizontally(), object.getState().getActiveStateId(), playerId, false);
    }

    public synchronized VttEnvironmentStateUpdatePayload applyEnvironmentState(
            VttEnvironmentStateRequestPayload request) {
        if (request == null || request.wallsJson() == null || request.doorsJson() == null
                || request.fogJson() == null || request.visionJson() == null
                || request.wallsJson().length() > 1_000_000 || request.doorsJson().length() > 1_000_000
                || request.fogJson().length() > 1_000_000 || request.visionJson().length() > 1_000_000) return null;
        try {
            List<VttWall> walls = GSON.fromJson(request.wallsJson(), WALL_LIST_TYPE);
            List<VttDoor> doors = GSON.fromJson(request.doorsJson(), DOOR_LIST_TYPE);
            VttFogOfWar fog = GSON.fromJson(request.fogJson(), VttFogOfWar.class);
            List<VisionState> visionStates = GSON.fromJson(request.visionJson(), VISION_LIST_TYPE);
            if (walls == null || doors == null || fog == null || visionStates == null
                    || walls.size() > 10_000 || doors.size() > 10_000 || visionStates.size() > 10_000
                    || fog.getHiddenAreas().size() + fog.getRevealedAreas().size() > 10_000) return null;
            activeScene.clearWalls();
            walls.stream().filter(wall -> wall != null && wall.getId() != null && !wall.getId().isBlank())
                    .forEach(activeScene::addWall);
            activeScene.clearDoors();
            doors.stream().filter(door -> door != null && door.getId() != null && !door.getId().isBlank())
                    .forEach(activeScene::addDoor);
            activeScene.setFogOfWar(fog);
            for (VisionState vision : visionStates) {
                if (vision == null || vision.objectId() == null || !Double.isFinite(vision.innerRadius())
                        || !Double.isFinite(vision.outerRadius())) continue;
                activeScene.getObjects().stream()
                        .filter(object -> object != null && vision.objectId().equals(object.getId()))
                        .findFirst().ifPresent(object -> {
                            double outer = Math.max(64.0, Math.min(100_000.0, vision.outerRadius()));
                            object.setVisionOuterRadius(outer);
                            object.setVisionInnerRadius(Math.min(outer, Math.max(0.0, vision.innerRadius())));
                        });
            }
            storage.saveScene(tabletop.getId(), activeScene);
            return currentEnvironmentState();
        } catch (RuntimeException exception) {
            VTT.LOGGER.warn("Could not decode VTT environment update", exception);
            return null;
        }
    }

    public synchronized VttEnvironmentStateUpdatePayload currentEnvironmentState() {
        return new VttEnvironmentStateUpdatePayload(
                GSON.toJson(activeScene.getWalls()), GSON.toJson(activeScene.getDoors()),
                GSON.toJson(activeScene.getFogOfWar()),
                GSON.toJson(activeScene.getObjects().stream()
                        .filter(object -> object != null && object.getId() != null)
                        .map(object -> new VisionState(object.getId(), object.getVisionInnerRadius(),
                                object.getVisionOuterRadius() > 0.0
                                        ? object.getVisionOuterRadius() : 512.0)).toList()));
    }

    private record VisionState(String objectId, double innerRadius, double outerRadius) {}

    private boolean valid(VttTokenTransformRequestPayload request) {
        return request.objectId() != null && !request.objectId().isBlank()
                && request.activeStateId() != null && !request.activeStateId().isBlank()
                && Double.isFinite(request.x()) && Double.isFinite(request.y())
                && Double.isFinite(request.rotationDegrees())
                && Math.abs(request.x()) <= 10_000_000.0 && Math.abs(request.y()) <= 10_000_000.0;
    }

    private double normalizeRotation(double degrees) {
        double normalized = degrees % 360.0;
        return normalized < 0.0 ? normalized + 360.0 : normalized;
    }
}
