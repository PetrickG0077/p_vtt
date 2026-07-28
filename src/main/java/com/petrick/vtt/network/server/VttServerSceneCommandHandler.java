package com.petrick.vtt.network.server;

import com.petrick.vtt.VTT;
import com.petrick.vtt.feature.tabletop.VttSceneLimits;
import com.petrick.vtt.network.payload.VttSceneCommandPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Applies scene lifecycle commands exclusively on the authoritative server. */
public final class VttServerSceneCommandHandler {
    private VttServerSceneCommandHandler() {}

    public static void handle(VttSceneCommandPayload request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer requester)) return;
        if (!VttServerRequestRateLimiter.allow(
                requester, VttServerRequestRateLimiter.Category.SCENE_COMMAND)) return;
        if (!VttServerPlayerEvents.isMaster(requester)) {
            VttServerRequestRateLimiter.reject(
                    requester, VttServerRequestRateLimiter.Category.SCENE_COMMAND,
                    "permission denied");
            return;
        }
        VttServerTabletopState state = VttServerTabletopState.get();
        if (request == null
                || request.authorityRevision() != state.authorityRevision()
                || request.operation() == null || request.targetId() == null
                || request.value() == null || request.backgroundAssetId() == null
                || request.mapTextureMode() == null
                || request.backgroundAssetId().length() > 512
                || request.mapTextureMode().length() > 16) {
            reject(requester, "invalid request");
            return;
        }

        if (VttSceneCommandPayload.CREATE.equals(request.operation())) {
            var violation = VttSceneLimits.sceneCreation(state.activeTabletop());
            if (violation != null) {
                reject(requester, violation.code());
                VttServerFeedback.showLimit(requester, violation.message());
                return;
            }
        }

        String previousSceneId = state.activeScene().getId();
        boolean changed = switch (request.operation()) {
            case VttSceneCommandPayload.CREATE -> state.createAndActivateScene(
                    request.value(), request.targetId(), request.backgroundAssetId(),
                    request.mapTextureMode());
            case VttSceneCommandPayload.SWITCH -> state.switchToScene(request.targetId());
            case VttSceneCommandPayload.SET_BACKGROUND -> state.setActiveSceneBackground(request.value());
            case VttSceneCommandPayload.RENAME -> state.renameScene(request.targetId(), request.value());
            case VttSceneCommandPayload.DELETE -> state.deleteScene(request.targetId());
            default -> false;
        };
        if (!changed) {
            reject(requester, "command was not accepted");
            return;
        }

        MinecraftServer server = requester.getServer();
        if (server == null) {
            reject(requester, "server unavailable");
            return;
        }
        boolean sceneChanged = !previousSceneId.equals(state.activeScene().getId());
        boolean assetsChanged = sceneChanged
                || VttSceneCommandPayload.SET_BACKGROUND.equals(request.operation());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (assetsChanged) {
                VttServerVisionSourceSync.markCurrentAssetsSent(player, state);
                VttServerAssetSyncService.sendActiveSceneAssets(
                        player, state.replicatedSceneFor(player),
                        VttServerPlayerEvents.isMaster(player), () -> {
                            VttServerSceneSnapshotSync.sendToPlayer(player, state);
                            VttServerVisionSourceSync.sendToPlayer(player, state);
                        });
            } else {
                VttServerAssetSyncService.runAfterPending(player, () -> {
                    VttServerSceneSnapshotSync.sendToPlayer(player, state);
                    VttServerVisionSourceSync.sendToPlayer(player, state);
                });
            }
        }
        VTT.LOGGER.info("Applied VTT scene command {} from {}: active scene is {}",
                request.operation(), requester.getGameProfile().getName(), state.activeScene().getId());
    }

    private static void reject(ServerPlayer requester, String reason) {
        VttServerRequestRateLimiter.reject(
                requester, VttServerRequestRateLimiter.Category.SCENE_COMMAND, reason);
    }
}
