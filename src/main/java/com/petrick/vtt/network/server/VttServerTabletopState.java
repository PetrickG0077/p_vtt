package com.petrick.vtt.network.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttTabletop;
import com.petrick.vtt.feature.tabletop.persistence.TabletopStorage;
import com.petrick.vtt.feature.tabletop.persistence.TabletopStoragePaths;
import com.petrick.vtt.network.payload.VttSceneSnapshotPayload;
import net.neoforged.fml.loading.FMLPaths;
import com.petrick.vtt.feature.tabletop.VttSceneObject;
import com.petrick.vtt.network.payload.VttTokenTransformRequestPayload;
import com.petrick.vtt.network.payload.VttTokenTransformUpdatePayload;

public final class VttServerTabletopState {

    private static final Gson GSON = new GsonBuilder().create();
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
                object.getState().isFlippedHorizontally(), object.getState().getActiveStateId());
    }

    public synchronized VttTokenTransformUpdatePayload currentTokenTransform(String objectId) {
        if (objectId == null || objectId.isBlank()) return null;
        VttSceneObject object = activeScene.getObjects().stream()
                .filter(candidate -> candidate != null && objectId.equals(candidate.getId()))
                .findFirst().orElse(null);
        if (object == null) return null;
        return new VttTokenTransformUpdatePayload(object.getId(), object.getTransform().getX(),
                object.getTransform().getY(), object.getTransform().getRotationDegrees(),
                object.getState().isFlippedHorizontally(), object.getState().getActiveStateId());
    }

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
