package com.petrick.vtt.network;

import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttIdentityPayload;
import com.petrick.vtt.network.payload.VttPlayerRosterPayload;
import com.petrick.vtt.network.payload.VttEditorNoticePayload;
import com.petrick.vtt.network.payload.VttSceneSnapshotStartPayload;
import com.petrick.vtt.network.payload.VttSceneSnapshotChunkPayload;
import com.petrick.vtt.network.payload.VttSceneSnapshotCompletePayload;
import com.petrick.vtt.network.payload.VttAssetChunkPayload;
import com.petrick.vtt.network.payload.VttAssetManifestPayload;
import com.petrick.vtt.network.payload.VttAssetRequestPayload;
import com.petrick.vtt.network.payload.VttAssetSyncCompletePayload;
import com.petrick.vtt.network.server.VttServerAssetRequestHandler;
import com.petrick.vtt.network.payload.VttTokenTransformRequestPayload;
import com.petrick.vtt.network.payload.VttTokenTransformUpdatePayload;
import com.petrick.vtt.network.server.VttServerTokenTransformHandler;
import com.petrick.vtt.network.payload.VttTokenDefinitionUpsertPayload;
import com.petrick.vtt.network.server.VttServerTokenDefinitionHandler;
import com.petrick.vtt.network.payload.VttTokenDefinitionCommandPayload;
import com.petrick.vtt.network.payload.VttTokenDefinitionResultPayload;
import com.petrick.vtt.network.payload.VttEnvironmentStateUpdatePayload;
import com.petrick.vtt.network.server.VttServerEnvironmentStateHandler;
import com.petrick.vtt.network.payload.VttEnvironmentCommandPayload;
import com.petrick.vtt.network.payload.VttEnvironmentCommandUpdatePayload;
import com.petrick.vtt.network.payload.VttTokenLifecycleRequestPayload;
import com.petrick.vtt.network.payload.VttTokenLifecycleUpdatePayload;
import com.petrick.vtt.network.server.VttServerTokenLifecycleHandler;
import com.petrick.vtt.network.payload.VttSceneCommandPayload;
import com.petrick.vtt.network.server.VttServerSceneCommandHandler;
import com.petrick.vtt.network.payload.VttVisionSourcesPayload;
import com.petrick.vtt.network.payload.VttPlayerReplicationPayload;
import com.petrick.vtt.network.payload.VttReplicationResyncRequestPayload;
import com.petrick.vtt.network.server.VttServerReplicationResyncHandler;
import com.petrick.vtt.network.payload.VttTokenOwnerCommandPayload;
import com.petrick.vtt.network.server.VttServerTokenOwnerHandler;
import com.petrick.vtt.network.payload.VttPlayerModeCommandPayload;
import com.petrick.vtt.network.server.VttServerPlayerModeHandler;
import com.petrick.vtt.network.payload.VttPresentationCommandPayload;
import com.petrick.vtt.network.payload.VttPresentationUpdatePayload;
import com.petrick.vtt.network.server.VttServerPresentationHandler;
import com.petrick.vtt.network.payload.VttAssetFolderCommandPayload;
import com.petrick.vtt.network.payload.VttAssetFolderResultPayload;
import com.petrick.vtt.network.payload.VttAssetManagerChangePayload;
import com.petrick.vtt.network.server.VttServerAssetFolderHandler;
import com.petrick.vtt.network.payload.VttMapDefinitionUpsertPayload;
import com.petrick.vtt.network.payload.VttMapDefinitionCommandPayload;
import com.petrick.vtt.network.server.VttServerMapDefinitionHandler;
import com.petrick.vtt.network.payload.VttMapDefinitionResultPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = VTT.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class VttNetwork {

    private static final String PROTOCOL_VERSION = "42";

    private VttNetwork() {
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToClient(
                VttIdentityPayload.TYPE,
                VttIdentityPayload.STREAM_CODEC,
                VttClientPayloadHandler::handleIdentity
        );
        registrar.playToClient(
                VttPlayerRosterPayload.TYPE,
                VttPlayerRosterPayload.STREAM_CODEC,
                VttClientPayloadHandler::handlePlayerRoster
        );
        registrar.playToClient(VttEditorNoticePayload.TYPE,
                VttEditorNoticePayload.STREAM_CODEC,
                VttClientPayloadHandler::handleEditorNotice);
        registrar.playToClient(VttSceneSnapshotStartPayload.TYPE,
                VttSceneSnapshotStartPayload.STREAM_CODEC,
                VttClientPayloadHandler::handleSceneSnapshotStart);
        registrar.playToClient(VttSceneSnapshotChunkPayload.TYPE,
                VttSceneSnapshotChunkPayload.STREAM_CODEC,
                VttClientPayloadHandler::handleSceneSnapshotChunk);
        registrar.playToClient(VttSceneSnapshotCompletePayload.TYPE,
                VttSceneSnapshotCompletePayload.STREAM_CODEC,
                VttClientPayloadHandler::handleSceneSnapshotComplete);
        registrar.playToClient(VttVisionSourcesPayload.TYPE, VttVisionSourcesPayload.STREAM_CODEC,
                VttClientPayloadHandler::handleVisionSources);
        registrar.playToClient(VttPlayerReplicationPayload.TYPE,
                VttPlayerReplicationPayload.STREAM_CODEC,
                VttClientPayloadHandler::handlePlayerReplication);
        registrar.playToServer(VttReplicationResyncRequestPayload.TYPE,
                VttReplicationResyncRequestPayload.STREAM_CODEC,
                VttServerReplicationResyncHandler::handle);
        registrar.playToClient(VttAssetManifestPayload.TYPE, VttAssetManifestPayload.STREAM_CODEC,
                VttClientPayloadHandler::handleAssetManifest);
        registrar.playToServer(VttAssetRequestPayload.TYPE, VttAssetRequestPayload.STREAM_CODEC,
                VttServerAssetRequestHandler::handle);
        registrar.playToClient(VttAssetChunkPayload.TYPE, VttAssetChunkPayload.STREAM_CODEC,
                VttClientPayloadHandler::handleAssetChunk);
        registrar.playToClient(VttAssetSyncCompletePayload.TYPE, VttAssetSyncCompletePayload.STREAM_CODEC,
                VttClientPayloadHandler::handleAssetSyncComplete);
        registrar.playToServer(VttTokenTransformRequestPayload.TYPE, VttTokenTransformRequestPayload.STREAM_CODEC,
                VttServerTokenTransformHandler::handle);
        registrar.playToClient(VttTokenTransformUpdatePayload.TYPE, VttTokenTransformUpdatePayload.STREAM_CODEC,
                VttClientPayloadHandler::handleTokenTransformUpdate);
        registrar.playToServer(VttTokenDefinitionUpsertPayload.TYPE, VttTokenDefinitionUpsertPayload.STREAM_CODEC,
                VttServerTokenDefinitionHandler::handle);
        registrar.playToServer(VttTokenDefinitionCommandPayload.TYPE,
                VttTokenDefinitionCommandPayload.STREAM_CODEC,
                VttServerTokenDefinitionHandler::handleCommand);
        registrar.playToClient(VttTokenDefinitionResultPayload.TYPE,
                VttTokenDefinitionResultPayload.STREAM_CODEC,
                VttClientPayloadHandler::handleTokenDefinitionResult);
        registrar.playToClient(VttEnvironmentStateUpdatePayload.TYPE, VttEnvironmentStateUpdatePayload.STREAM_CODEC,
                VttClientPayloadHandler::handleEnvironmentStateUpdate);
        registrar.playToServer(VttEnvironmentCommandPayload.TYPE, VttEnvironmentCommandPayload.STREAM_CODEC,
                VttServerEnvironmentStateHandler::handleCommand);
        registrar.playToClient(VttEnvironmentCommandUpdatePayload.TYPE,
                VttEnvironmentCommandUpdatePayload.STREAM_CODEC,
                VttClientPayloadHandler::handleEnvironmentCommandUpdate);
        registrar.playToServer(VttTokenLifecycleRequestPayload.TYPE, VttTokenLifecycleRequestPayload.STREAM_CODEC,
                VttServerTokenLifecycleHandler::handle);
        registrar.playToClient(VttTokenLifecycleUpdatePayload.TYPE, VttTokenLifecycleUpdatePayload.STREAM_CODEC,
                VttClientPayloadHandler::handleTokenLifecycleUpdate);
        registrar.playToServer(VttSceneCommandPayload.TYPE, VttSceneCommandPayload.STREAM_CODEC,
                VttServerSceneCommandHandler::handle);
        registrar.playToServer(VttTokenOwnerCommandPayload.TYPE,
                VttTokenOwnerCommandPayload.STREAM_CODEC,
                VttServerTokenOwnerHandler::handle);
        registrar.playToServer(VttPlayerModeCommandPayload.TYPE,
                VttPlayerModeCommandPayload.STREAM_CODEC,
                VttServerPlayerModeHandler::handle);
        registrar.playToServer(VttPresentationCommandPayload.TYPE,
                VttPresentationCommandPayload.STREAM_CODEC,
                VttServerPresentationHandler::handle);
        registrar.playToServer(VttAssetFolderCommandPayload.TYPE,
                VttAssetFolderCommandPayload.STREAM_CODEC,
                VttServerAssetFolderHandler::handle);
        registrar.playToClient(VttAssetFolderResultPayload.TYPE,
                VttAssetFolderResultPayload.STREAM_CODEC,
                VttClientPayloadHandler::handleAssetFolderResult);
        registrar.playToClient(VttAssetManagerChangePayload.TYPE,
                VttAssetManagerChangePayload.STREAM_CODEC,
                VttClientPayloadHandler::handleAssetManagerChange);
        registrar.playToServer(VttMapDefinitionUpsertPayload.TYPE,
                VttMapDefinitionUpsertPayload.STREAM_CODEC,
                VttServerMapDefinitionHandler::handleUpsert);
        registrar.playToServer(VttMapDefinitionCommandPayload.TYPE,
                VttMapDefinitionCommandPayload.STREAM_CODEC,
                VttServerMapDefinitionHandler::handleCommand);
        registrar.playToClient(VttMapDefinitionResultPayload.TYPE,
                VttMapDefinitionResultPayload.STREAM_CODEC,
                VttClientPayloadHandler::handleMapDefinitionResult);
        registrar.playToClient(VttPresentationUpdatePayload.TYPE,
                VttPresentationUpdatePayload.STREAM_CODEC,
                VttClientPayloadHandler::handlePresentationUpdate);
    }
}
