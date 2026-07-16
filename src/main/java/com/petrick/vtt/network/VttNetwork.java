package com.petrick.vtt.network;

import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttIdentityPayload;
import com.petrick.vtt.network.payload.VttSceneSnapshotPayload;
import com.petrick.vtt.network.payload.VttAssetChunkPayload;
import com.petrick.vtt.network.payload.VttAssetSyncCompletePayload;
import com.petrick.vtt.network.payload.VttAssetSyncStartPayload;
import com.petrick.vtt.network.payload.VttTokenTransformRequestPayload;
import com.petrick.vtt.network.payload.VttTokenTransformUpdatePayload;
import com.petrick.vtt.network.server.VttServerTokenTransformHandler;
import com.petrick.vtt.network.payload.VttTokenDefinitionUpsertPayload;
import com.petrick.vtt.network.server.VttServerTokenDefinitionHandler;
import com.petrick.vtt.network.payload.VttEnvironmentStateRequestPayload;
import com.petrick.vtt.network.payload.VttEnvironmentStateUpdatePayload;
import com.petrick.vtt.network.server.VttServerEnvironmentStateHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = VTT.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class VttNetwork {

    private static final String PROTOCOL_VERSION = "6";

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
                VttSceneSnapshotPayload.TYPE,
                VttSceneSnapshotPayload.STREAM_CODEC,
                VttClientPayloadHandler::handleSceneSnapshot
        );
        registrar.playToClient(VttAssetSyncStartPayload.TYPE, VttAssetSyncStartPayload.STREAM_CODEC,
                VttClientPayloadHandler::handleAssetSyncStart);
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
        registrar.playToServer(VttEnvironmentStateRequestPayload.TYPE, VttEnvironmentStateRequestPayload.STREAM_CODEC,
                VttServerEnvironmentStateHandler::handle);
        registrar.playToClient(VttEnvironmentStateUpdatePayload.TYPE, VttEnvironmentStateUpdatePayload.STREAM_CODEC,
                VttClientPayloadHandler::handleEnvironmentStateUpdate);
    }
}
