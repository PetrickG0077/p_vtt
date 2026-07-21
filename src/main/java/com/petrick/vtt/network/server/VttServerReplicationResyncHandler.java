package com.petrick.vtt.network.server;

import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttReplicationResyncRequestPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sends a private recovery snapshot, with a server-side rate limit against request spam. */
public final class VttServerReplicationResyncHandler {
    private VttServerReplicationResyncHandler() {}

    public static void handle(
            VttReplicationResyncRequestPayload request, IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player) || request == null) return;
        if (!VttServerRequestRateLimiter.allow(
                player, VttServerRequestRateLimiter.Category.REPLICATION_RESYNC)) return;

        VttServerTabletopState state = VttServerTabletopState.get();
        VttServerVisionSourceSync.resetPlayerScope(player.getUUID());
        var replicatedScene = state.replicatedSceneFor(player);
        VttServerVisionSourceSync.markCurrentAssetsSent(player, state);
        VttServerAssetSyncService.sendActiveSceneAssets(
                player, replicatedScene, VttServerPlayerEvents.isMaster(player), () -> {
                    VttServerSceneSnapshotSync.sendToPlayer(player, state);
                    VttServerVisionSourceSync.sendToPlayer(player, state);
                });
        VTT.LOGGER.warn(
                "Recovered VTT replication for {} after {} (client A={}, V={}, R={})",
                player.getGameProfile().getName(), request.reason(), request.authorityRevision(),
                request.lastVisionRevision(), request.lastReplicationRevision());
    }
}
