package com.petrick.vtt.platform.client;

import com.petrick.vtt.VTT;
import com.petrick.vtt.editor.screen.VTTScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import com.petrick.vtt.network.client.VttClientTokenTransformSync;
import com.petrick.vtt.network.client.VttClientEnvironmentCommandSync;
import com.petrick.vtt.network.client.VttClientTokenLifecycleSync;
import com.petrick.vtt.network.client.VttClientTokenOwnershipSync;
import com.petrick.vtt.network.client.VttClientSceneSnapshotReceiver;
import com.petrick.vtt.network.client.VttClientEditorNotice;
import com.petrick.vtt.network.client.VttClientAssetCache;
import com.petrick.vtt.network.client.VttClientPresentationState;
import com.petrick.vtt.network.client.VttClientSceneHistorySync;
import com.petrick.vtt.network.client.VttClientMusicSync;
import com.petrick.vtt.network.client.VttClientShowState;
import com.petrick.vtt.feature.media.VttVideoFrameService;

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
        VttClientSceneHistorySync.tick(VTT.getApplication().getActiveSession());
        VttClientTokenLifecycleSync.tick(VTT.getApplication().getActiveSession());
        VttClientTokenOwnershipSync.tick(VTT.getApplication().getActiveSession());
        VttClientEnvironmentCommandSync.tick(VTT.getApplication().getActiveSession());
        VttClientTokenTransformSync.tick(VTT.getApplication().getActiveSession());
        VttClientSceneSnapshotReceiver.tick();
        VttClientAssetCache.tick();
        VttClientShowState.tickPreload(VTT.getApplication().getActiveSession());
        VttClientShowState.tickPlaybackSynchronization();

        Minecraft minecraft = Minecraft.getInstance();

        while (ClientKeyMappings.OPEN_VTT_SCREEN.consumeClick()) {
            minecraft.setScreen(new VTTScreen());
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        VttAssetSyncHudOverlay.render(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        VttClientSceneSnapshotReceiver.reset();
        VttClientEditorNotice.reset();
        VttVideoFrameService.clear();
        VttClientAssetCache.clearActiveServerCache();
        VttClientAssetCache.reset();
        VttClientPresentationState.reset();
        VttClientSceneHistorySync.reset();
        VttClientMusicSync.reset();
        VttClientShowState.reset();
        var session = VTT.getApplication().getActiveSession();
        if (!session.isNetworkAuthorityActive()) return;
        VttClientTokenTransformSync.reset();
        VttClientTokenLifecycleSync.reset();
        VttClientTokenOwnershipSync.reset();
        VttClientEnvironmentCommandSync.reset();
        session.restoreLocalSessionAfterDisconnect();
    }
}
