package com.petrick.vtt.network.server;

import com.petrick.vtt.VTT;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Drives debounced authoritative persistence and guarantees a final shutdown flush. */
@EventBusSubscriber(modid = VTT.MOD_ID)
public final class VttServerPersistenceEvents {
    private VttServerPersistenceEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        VttServerTabletopState.tickPersistenceIfInitialized();
        VttServerAssetSyncService.tick();
        VttServerShowHandler.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        VttServerTabletopState.shutdown();
        VttServerAssetSyncService.clear();
        VttServerRequestRateLimiter.clear();
    }
}
