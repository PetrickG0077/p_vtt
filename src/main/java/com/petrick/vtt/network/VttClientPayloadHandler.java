package com.petrick.vtt.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.petrick.vtt.VTT;
import com.petrick.vtt.core.session.VttRole;
import com.petrick.vtt.core.session.VttPlayerRosterEntry;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttTabletop;
import com.petrick.vtt.feature.tabletop.VttSceneObject;
import com.petrick.vtt.network.payload.VttIdentityPayload;
import com.petrick.vtt.network.payload.VttPlayerRosterPayload;
import com.petrick.vtt.network.payload.VttEditorNoticePayload;
import com.petrick.vtt.network.payload.VttSceneSnapshotStartPayload;
import com.petrick.vtt.network.payload.VttSceneSnapshotChunkPayload;
import com.petrick.vtt.network.payload.VttSceneSnapshotCompletePayload;
import com.petrick.vtt.network.payload.VttAssetChunkPayload;
import com.petrick.vtt.network.payload.VttAssetManifestPayload;
import com.petrick.vtt.network.payload.VttAssetSyncCompletePayload;
import com.petrick.vtt.network.client.VttClientAssetCache;
import com.petrick.vtt.network.client.VttClientEditorNotice;
import com.petrick.vtt.network.client.VttClientSceneSnapshotReceiver;
import com.petrick.vtt.network.client.VttClientTokenTransformSync;
import com.petrick.vtt.network.payload.VttTokenTransformUpdatePayload;
import com.petrick.vtt.network.client.VttClientEnvironmentStateSync;
import com.petrick.vtt.network.client.VttClientEnvironmentCommandSync;
import com.petrick.vtt.network.payload.VttEnvironmentStateUpdatePayload;
import com.petrick.vtt.network.payload.VttEnvironmentCommandUpdatePayload;
import com.petrick.vtt.network.client.VttClientTokenLifecycleSync;
import com.petrick.vtt.network.payload.VttTokenLifecycleUpdatePayload;
import com.petrick.vtt.network.payload.VttVisionSourcesPayload;
import com.petrick.vtt.network.payload.VttPlayerReplicationPayload;
import com.petrick.vtt.network.client.VttClientPresentationState;
import com.petrick.vtt.network.payload.VttPresentationUpdatePayload;
import com.petrick.vtt.network.payload.VttMusicUpdatePayload;
import com.petrick.vtt.network.client.VttClientMusicSync;
import com.petrick.vtt.network.payload.VttShowUpdatePayload;
import com.petrick.vtt.network.payload.VttShowPreloadPayload;
import com.petrick.vtt.network.payload.VttShowPreloadStatusPayload;
import com.petrick.vtt.network.client.VttClientShowState;
import com.petrick.vtt.network.payload.VttAssetFolderResultPayload;
import com.petrick.vtt.network.client.VttClientAssetFolderResultState;
import com.petrick.vtt.network.payload.VttAssetManagerChangePayload;
import com.petrick.vtt.network.client.VttClientAssetManagerChangeState;
import com.petrick.vtt.network.payload.VttMapDefinitionResultPayload;
import com.petrick.vtt.network.client.VttClientMapDefinitionResultState;
import com.petrick.vtt.network.payload.VttTokenDefinitionResultPayload;
import com.petrick.vtt.network.client.VttClientTokenDefinitionResultState;
import com.petrick.vtt.network.payload.VttSceneCommandResultPayload;
import com.petrick.vtt.network.client.VttClientSceneCommandResultState;
import com.petrick.vtt.network.client.VttClientSceneClipboardPasteResultState;
import com.petrick.vtt.network.payload.VttAttachmentDefinitionResultPayload;
import com.petrick.vtt.network.payload.VttSceneClipboardPasteResultPayload;
import com.petrick.vtt.network.client.VttClientAttachmentDefinitionResultState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Type;
import java.util.List;

public final class VttClientPayloadHandler {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Type SCENE_OBJECT_LIST_TYPE = new TypeToken<List<VttSceneObject>>() {}.getType();
    private static final Type PLAYER_ROSTER_LIST_TYPE =
            new TypeToken<List<VttPlayerRosterEntry>>() {}.getType();

    private VttClientPayloadHandler() {
    }

    public static void handleIdentity(VttIdentityPayload payload, IPayloadContext context) {
        var session = VTT.getApplication().getActiveSession();

        try {
            session.applyNetworkIdentity(
                    payload.playerId(), VttRole.valueOf(payload.role()), payload.spectator());
        } catch (IllegalArgumentException exception) {
            session.applyNetworkIdentity(
                    payload.playerId(), VttRole.PLAYER, payload.spectator());
            VTT.LOGGER.warn("Received unknown VTT role from server: {}", payload.role());
        }

        VTT.LOGGER.info("Received VTT identity: player={}, role={}, spectator={}",
                payload.playerId(), session.getLocalRole(), session.isLocalSpectator());
    }

    public static void handlePlayerRoster(
            VttPlayerRosterPayload payload, IPayloadContext context
    ) {
        try {
            List<VttPlayerRosterEntry> roster = GSON.fromJson(
                    payload.rosterJson(), PLAYER_ROSTER_LIST_TYPE);
            VTT.getApplication().getActiveSession().applyNetworkPlayerRoster(roster);
        } catch (RuntimeException exception) {
            VTT.LOGGER.warn("Ignored invalid VTT player roster", exception);
        }
    }

    public static void handleEditorNotice(VttEditorNoticePayload payload, IPayloadContext context) {
        if (payload != null) VttClientEditorNotice.show(payload.message());
    }

    public static void handleSceneSnapshotStart(
            VttSceneSnapshotStartPayload payload, IPayloadContext context
    ) {
        VttClientSceneSnapshotReceiver.begin(payload);
    }

    public static void handleSceneSnapshotChunk(
            VttSceneSnapshotChunkPayload payload, IPayloadContext context
    ) {
        VttClientSceneSnapshotReceiver.accept(payload);
    }

    public static void handleSceneSnapshotComplete(
            VttSceneSnapshotCompletePayload payload, IPayloadContext context
    ) {
        var snapshot = VttClientSceneSnapshotReceiver.finish(payload);
        if (snapshot == null) return;
        if (!VttClientAssetCache.isReadyForServerState()) {
            VttClientAssetCache.recoverServerState();
            return;
        }
        try {
            VttTabletop tabletop = GSON.fromJson(snapshot.tabletopJson(), VttTabletop.class);
            VttScene scene = GSON.fromJson(snapshot.sceneJson(), VttScene.class);
            if (tabletop == null || scene == null) {
                throw new IllegalArgumentException("Snapshot contains null tabletop or scene");
            }
            VTT.getApplication().getActiveSession().applyNetworkSnapshot(
                    tabletop, scene, snapshot.authorityRevision());
        } catch (RuntimeException exception) {
            VTT.LOGGER.error("Failed to apply VTT scene snapshot from server", exception);
            VttClientSceneSnapshotReceiver.recoverAfterApplyFailure(
                    snapshot.authorityRevision());
        }
    }

    public static void handleVisionSources(VttVisionSourcesPayload payload, IPayloadContext context) {
        if (!VttClientAssetCache.isReadyForServerState()) return;
        VTT.getApplication().getActiveSession().applyNetworkVisionSources(
                payload.authorityRevision(), payload.visionRevision(), payload.sceneId(),
                payload.maskWhenEmpty(), payload.regions());
    }

    public static void handlePlayerReplication(
            VttPlayerReplicationPayload payload, IPayloadContext context
    ) {
        if (!VttClientAssetCache.isReadyForServerState()) return;
        try {
            List<VttSceneObject> objects = GSON.fromJson(
                    payload.spawnedObjectsJson(), SCENE_OBJECT_LIST_TYPE);
            VTT.getApplication().getActiveSession().applyNetworkReplication(
                    payload.authorityRevision(), payload.replicationRevision(), payload.sceneId(),
                    objects, payload.despawnObjectIds());
        } catch (RuntimeException exception) {
            VTT.LOGGER.error("Failed to apply VTT player replication payload", exception);
        }
    }

    public static void handleAssetManifest(VttAssetManifestPayload payload, IPayloadContext context) {
        VttClientAssetCache.begin(payload);
    }

    public static void handleAssetChunk(VttAssetChunkPayload payload, IPayloadContext context) {
        VttClientAssetCache.accept(payload);
    }

    public static void handleAssetSyncComplete(VttAssetSyncCompletePayload payload, IPayloadContext context) {
        VttClientAssetCache.finish(payload);
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

    public static void handlePresentationUpdate(
            VttPresentationUpdatePayload payload, IPayloadContext context
    ) {
        var session = VTT.getApplication().getActiveSession();
        VttClientPresentationState.accept(payload, !session.isLocalMaster());
    }

    public static void handleMusicUpdate(
            VttMusicUpdatePayload payload, IPayloadContext context
    ) {
        VttClientMusicSync.accept(payload);
    }

    public static void handleShowUpdate(
            VttShowUpdatePayload payload, IPayloadContext context
    ) {
        VttClientShowState.accept(payload);
    }

    public static void handleShowPreload(
            VttShowPreloadPayload payload, IPayloadContext context
    ) {
        VttClientShowState.acceptPreload(payload.relativePath());
    }

    public static void handleShowPreloadStatus(
            VttShowPreloadStatusPayload payload, IPayloadContext context
    ) {
        VttClientShowState.acceptPreloadStatus(payload);
    }

    public static void handleAssetFolderResult(
            VttAssetFolderResultPayload payload, IPayloadContext context
    ) {
        VttClientAssetFolderResultState.accept(payload);
    }

    public static void handleAssetManagerChange(
            VttAssetManagerChangePayload payload, IPayloadContext context
    ) {
        VttClientAssetManagerChangeState.accept(payload);
    }

    public static void handleMapDefinitionResult(
            VttMapDefinitionResultPayload payload, IPayloadContext context
    ) {
        VttClientMapDefinitionResultState.accept(payload);
    }

    public static void handleAttachmentDefinitionResult(
            VttAttachmentDefinitionResultPayload payload, IPayloadContext context
    ) {
        VttClientAttachmentDefinitionResultState.accept(payload);
    }

    public static void handleTokenDefinitionResult(
            VttTokenDefinitionResultPayload payload, IPayloadContext context
    ) {
        VttClientTokenDefinitionResultState.accept(payload);
    }

    public static void handleSceneCommandResult(
            VttSceneCommandResultPayload payload, IPayloadContext context
    ) {
        VttClientSceneCommandResultState.accept(payload);
    }

    public static void handleSceneClipboardPasteResult(
            VttSceneClipboardPasteResultPayload payload, IPayloadContext context
    ) {
        VttClientSceneClipboardPasteResultState.accept(payload);
    }
}
