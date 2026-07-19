package com.petrick.vtt.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.petrick.vtt.VTT;
import com.petrick.vtt.core.session.VttRole;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttTabletop;
import com.petrick.vtt.feature.tabletop.VttSceneObject;
import com.petrick.vtt.network.payload.VttIdentityPayload;
import com.petrick.vtt.network.payload.VttSceneSnapshotPayload;
import com.petrick.vtt.network.payload.VttAssetChunkPayload;
import com.petrick.vtt.network.payload.VttAssetSyncCompletePayload;
import com.petrick.vtt.network.payload.VttAssetSyncStartPayload;
import com.petrick.vtt.network.client.VttClientAssetCache;
import com.petrick.vtt.network.client.VttClientTokenTransformSync;
import com.petrick.vtt.network.payload.VttTokenTransformUpdatePayload;
import com.petrick.vtt.network.client.VttClientEnvironmentStateSync;
import com.petrick.vtt.network.client.VttClientEnvironmentCommandSync;
import com.petrick.vtt.network.payload.VttEnvironmentStateUpdatePayload;
import com.petrick.vtt.network.payload.VttEnvironmentCommandUpdatePayload;
import com.petrick.vtt.network.client.VttClientTokenLifecycleSync;
import com.petrick.vtt.network.payload.VttTokenLifecycleUpdatePayload;
import com.petrick.vtt.network.payload.VttVisionSourcesPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Type;
import java.util.List;

public final class VttClientPayloadHandler {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Type SCENE_OBJECT_LIST_TYPE = new TypeToken<List<VttSceneObject>>() {}.getType();

    private VttClientPayloadHandler() {
    }

    public static void handleIdentity(VttIdentityPayload payload, IPayloadContext context) {
        var session = VTT.getApplication().getActiveSession();

        try {
            session.applyNetworkIdentity(payload.playerId(), VttRole.valueOf(payload.role()));
        } catch (IllegalArgumentException exception) {
            session.applyNetworkIdentity(payload.playerId(), VttRole.PLAYER);
            VTT.LOGGER.warn("Received unknown VTT role from server: {}", payload.role());
        }

        VTT.LOGGER.info("Received VTT identity: player={}, role={}", payload.playerId(), session.getLocalRole());
    }

    public static void handleSceneSnapshot(VttSceneSnapshotPayload payload, IPayloadContext context) {
        try {
            VttTabletop tabletop = GSON.fromJson(payload.tabletopJson(), VttTabletop.class);
            VttScene scene = GSON.fromJson(payload.sceneJson(), VttScene.class);
            VTT.getApplication().getActiveSession().applyNetworkSnapshot(
                    tabletop, scene, payload.authorityRevision());
        } catch (RuntimeException exception) {
            VTT.LOGGER.error("Failed to apply VTT scene snapshot from server", exception);
        }
    }

    public static void handleVisionSources(VttVisionSourcesPayload payload, IPayloadContext context) {
        try {
            List<VttSceneObject> objects = GSON.fromJson(
                    payload.replicatedObjectsJson(), SCENE_OBJECT_LIST_TYPE);
            VTT.getApplication().getActiveSession().applyNetworkVisionSources(
                    payload.authorityRevision(), payload.visionRevision(), payload.sceneId(),
                    payload.maskWhenEmpty(), payload.regions(), payload.visibleObjectIds(), objects);
        } catch (RuntimeException exception) {
            VTT.LOGGER.error("Failed to apply VTT player replication payload", exception);
        }
    }

    public static void handleAssetSyncStart(VttAssetSyncStartPayload payload, IPayloadContext context) {
        VttClientAssetCache.begin(payload.fileCount(), payload.clearExisting());
    }

    public static void handleAssetChunk(VttAssetChunkPayload payload, IPayloadContext context) {
        VttClientAssetCache.accept(payload);
    }

    public static void handleAssetSyncComplete(VttAssetSyncCompletePayload payload, IPayloadContext context) {
        VttClientAssetCache.finish();
    }

    public static void handleTokenTransformUpdate(VttTokenTransformUpdatePayload payload, IPayloadContext context) {
        VttClientTokenTransformSync.acceptConfirmed(VTT.getApplication().getActiveSession(), payload);
    }

    public static void handleEnvironmentStateUpdate(VttEnvironmentStateUpdatePayload payload, IPayloadContext context) {
        VttClientEnvironmentStateSync.acceptConfirmed(VTT.getApplication().getActiveSession(), payload);
    }

    public static void handleEnvironmentCommandUpdate(
            VttEnvironmentCommandUpdatePayload payload, IPayloadContext context) {
        VttClientEnvironmentCommandSync.accept(VTT.getApplication().getActiveSession(), payload);
    }

    public static void handleTokenLifecycleUpdate(VttTokenLifecycleUpdatePayload payload, IPayloadContext context) {
        VttClientTokenLifecycleSync.acceptConfirmed(VTT.getApplication().getActiveSession(), payload);
    }
}
