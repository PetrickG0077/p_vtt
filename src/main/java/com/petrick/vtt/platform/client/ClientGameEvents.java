package com.petrick.vtt.platform.client;

import com.petrick.vtt.VTT;
import com.petrick.vtt.editor.screen.VTTScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import com.petrick.vtt.network.client.VttClientTokenTransformSync;
import com.petrick.vtt.network.client.VttClientEnvironmentStateSync;
import com.petrick.vtt.network.client.VttClientTokenLifecycleSync;

/**
 * Eventos do cliente executados durante o jogo.
 */
@EventBusSubscriber(
        modid = VTT.MOD_ID,
        value = Dist.CLIENT
)
public final class ClientGameEvents {

    private ClientGameEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        VTT.getApplication().initialize();
        VttClientTokenLifecycleSync.tick(VTT.getApplication().getActiveSession());
        VttClientEnvironmentStateSync.tick(VTT.getApplication().getActiveSession());
        VttClientTokenTransformSync.tick(VTT.getApplication().getActiveSession());

        Minecraft minecraft = Minecraft.getInstance();

        while (ClientKeyMappings.OPEN_VTT_SCREEN.consumeClick()) {
            minecraft.setScreen(new VTTScreen());
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        var session = VTT.getApplication().getActiveSession();
        if (!session.isNetworkAuthorityActive()) return;
        VttClientTokenTransformSync.reset();
        VttClientTokenLifecycleSync.reset();
        VttClientEnvironmentStateSync.reset();
        session.restoreLocalSessionAfterDisconnect();
    }
}
