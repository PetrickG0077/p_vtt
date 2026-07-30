package com.petrick.vtt.network.server;

import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttAssetFolderCommandPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Applies Asset Manager folder operations on the authoritative server filesystem. */
public final class VttServerAssetFolderHandler {
    private VttServerAssetFolderHandler() {
    }

    public static void handle(
            VttAssetFolderCommandPayload request,
            IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer requester)
                || request == null
                || !VttServerPlayerEvents.isMaster(requester)
                || request.operation() == null
                || request.section() == null
                || request.source() == null
                || request.value() == null
                || request.source().length() > 256
                || request.value().length() > 256) return;

        VttServerTabletopState state = VttServerTabletopState.get();
        if (request.authorityRevision() != state.authorityRevision()
                || !state.applyAssetFolderCommand(request)) return;

        MinecraftServer server = requester.getServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            VttServerVisionSourceSync.markCurrentAssetsSent(player, state);
            VttServerAssetSyncService.sendActiveSceneAssets(
                    player,
                    state.replicatedSceneFor(player),
                    VttServerPlayerEvents.isMaster(player),
                    () -> {
                        VttServerSceneSnapshotSync.sendToPlayer(player, state);
                        VttServerVisionSourceSync.sendToPlayer(player, state);
                    });
        }
        VTT.LOGGER.info("Applied VTT Asset Manager folder command {} from {}",
                request.operation(), requester.getGameProfile().getName());
    }
}
