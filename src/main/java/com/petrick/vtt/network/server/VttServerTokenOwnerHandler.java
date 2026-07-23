package com.petrick.vtt.network.server;

import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttTokenOwnerCommandPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Applies placed-token ownership changes exclusively on the authoritative server. */
public final class VttServerTokenOwnerHandler {
    private VttServerTokenOwnerHandler() {
    }

    public static void handle(VttTokenOwnerCommandPayload request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer requester)) return;
        if (!VttServerRequestRateLimiter.allow(
                requester, VttServerRequestRateLimiter.Category.TOKEN_OWNERSHIP)) return;
        if (!VttServerPlayerEvents.isMaster(requester)) {
            reject(requester, "permission denied");
            return;
        }

        VttServerTabletopState state = VttServerTabletopState.get();
        if (request == null || request.authorityRevision() != state.authorityRevision()
                || request.sceneId() == null || request.objectId() == null
                || request.ownerId() == null || state.activeScene() == null
                || !request.sceneId().equals(state.activeScene().getId())) {
            reject(requester, "invalid request");
            return;
        }

        MinecraftServer server = requester.getServer();
        if (server == null) {
            reject(requester, "server unavailable");
            return;
        }
        String ownerId = request.ownerId().isBlank() ? null : request.ownerId().trim();
        if (ownerId != null && server.getPlayerList().getPlayers().stream()
                .noneMatch(player -> ownerId.equals(player.getUUID().toString()))) {
            reject(requester, "owner is not connected");
            return;
        }
        if (!state.setTokenOwner(request.objectId(), ownerId)) {
            reject(requester, "token or ownership did not change");
            return;
        }

        for (ServerPlayer connected : server.getPlayerList().getPlayers()) {
            VttServerVisionSourceSync.resetPlayerScope(connected.getUUID());
            VttServerVisionSourceSync.markCurrentAssetsSent(connected, state);
            VttServerAssetSyncService.sendActiveSceneAssets(
                    connected, state.replicatedSceneFor(connected),
                    VttServerPlayerEvents.isMaster(connected), () -> {
                        VttServerSceneSnapshotSync.sendToPlayer(connected, state);
                        VttServerVisionSourceSync.sendToPlayer(connected, state);
                    });
        }
        VTT.LOGGER.info("Assigned VTT token {} to {} by {}",
                request.objectId(), ownerId == null ? "no owner" : ownerId,
                requester.getGameProfile().getName());
    }

    private static void reject(ServerPlayer requester, String reason) {
        VttServerRequestRateLimiter.reject(
                requester, VttServerRequestRateLimiter.Category.TOKEN_OWNERSHIP, reason);
    }
}
