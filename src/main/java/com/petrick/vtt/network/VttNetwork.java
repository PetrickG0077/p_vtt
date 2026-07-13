package com.petrick.vtt.network;

import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttIdentityPayload;
import com.petrick.vtt.network.payload.VttSceneSnapshotPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = VTT.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class VttNetwork {

    private static final String PROTOCOL_VERSION = "1";

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
    }
}
