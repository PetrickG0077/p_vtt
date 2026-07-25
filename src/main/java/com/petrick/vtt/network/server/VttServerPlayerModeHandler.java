package com.petrick.vtt.network.server;

import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttPlayerModeCommandPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Applies the transient VTT spectator flag without changing the OP-derived role. */
public final class VttServerPlayerModeHandler {
    private VttServerPlayerModeHandler() {
    }

    public static void handle(VttPlayerModeCommandPayload request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer requester)) return;
        if (!VttServerRequestRateLimiter.allow(
                requester, VttServerRequestRateLimiter.Category.PLAYER_MODE)) return;
        if (!VttServerPlayerEvents.isMaster(requester)) {
            reject(requester, "permission denied");
            return;
        }
        VttServerTabletopState state = VttServerTabletopState.get();
        if (request == null || request.authorityRevision() != state.authorityRevision()
                || request.targetPlayerId() == null) {
            reject(requester, "invalid request");
            return;
        }

        MinecraftServer server = requester.getServer();
        ServerPlayer target = findPlayer(server, request.targetPlayerId());
        if (target == null || VttServerPlayerEvents.isMaster(target)
                || !VttServerPlayerEvents.setSpectator(target, request.spectator())) {
            reject(requester, "player mode did not change");
            return;
        }

        VttServerPlayerEvents.sendIdentity(target);
        VttServerPlayerEvents.broadcastRoster(server);
        VttServerVisionSourceSync.resetPlayerScope(target.getUUID());
        VttServerVisionSourceSync.markCurrentAssetsSent(target, state);
        VttServerAssetSyncService.sendActiveSceneAssets(
                target, state.replicatedSceneFor(target),
                VttServerPlayerEvents.isMaster(target), () -> {
                    VttServerSceneSnapshotSync.sendToPlayer(target, state);
                    VttServerVisionSourceSync.sendToPlayer(target, state);
                });
        VTT.LOGGER.info("Set VTT spectator mode for {} to {} by {}",
                target.getGameProfile().getName(), request.spectator(),
                requester.getGameProfile().getName());
    }

    private static ServerPlayer findPlayer(MinecraftServer server, String playerId) {
        if (server == null || playerId == null) return null;
        try {
            return server.getPlayerList().getPlayer(UUID.fromString(playerId));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static void reject(ServerPlayer requester, String reason) {
        VttServerRequestRateLimiter.reject(
                requester, VttServerRequestRateLimiter.Category.PLAYER_MODE, reason);
    }
}
