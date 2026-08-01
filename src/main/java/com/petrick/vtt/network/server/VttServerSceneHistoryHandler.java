package com.petrick.vtt.network.server;

import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttSceneCommandResultPayload;
import com.petrick.vtt.network.payload.VttSceneHistoryCommandPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Validates and atomically applies whole-scene undo/redo targets. */
public final class VttServerSceneHistoryHandler {
    private VttServerSceneHistoryHandler() {}

    public static void handle(VttSceneHistoryCommandPayload request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer requester) || request == null) return;
        VttServerTabletopState state = VttServerTabletopState.get();
        if (!validRequest(request)) {
            respond(requester, request, false, VttSceneCommandResultPayload.INVALID_REQUEST,
                    "Invalid undo/redo request", state.authorityRevision());
            return;
        }
        if (!VttServerPlayerEvents.isMaster(requester)) {
            respond(requester, request, false, VttSceneCommandResultPayload.PERMISSION_DENIED,
                    "Only masters can use undo and redo", state.authorityRevision());
            return;
        }
        if (!VttServerRequestRateLimiter.allow(
                requester, VttServerRequestRateLimiter.Category.SCENE_HISTORY)) {
            respond(requester, request, false, VttSceneCommandResultPayload.REJECTED,
                    "Too many history operations; try again shortly", state.authorityRevision());
            return;
        }
        if (request.authorityRevision() != state.authorityRevision()) {
            respond(requester, request, false, VttSceneCommandResultPayload.STALE_REVISION,
                    "The scene changed on the server; resynchronizing",
                    state.authorityRevision());
            syncRequester(requester, state);
            return;
        }

        VttServerTabletopState.SceneHistoryApplyResult result = state.applySceneHistory(
                request.sceneId(), request.expectedFingerprint(), request.targetSceneJson());
        if (result != VttServerTabletopState.SceneHistoryApplyResult.APPLIED) {
            String code = result == VttServerTabletopState.SceneHistoryApplyResult.STALE
                    ? VttSceneCommandResultPayload.STALE_REVISION
                    : VttSceneCommandResultPayload.REJECTED;
            String message = switch (result) {
                case STALE -> "Another master changed the scene; resynchronizing";
                case INVALID -> "The server rejected the history state";
                case SAVE_FAILED -> "The server could not save the history state";
                default -> "Could not apply undo/redo";
            };
            respond(requester, request, false, code, message, state.authorityRevision());
            syncRequester(requester, state);
            return;
        }

        String message = VttSceneHistoryCommandPayload.REDO.equals(request.operation())
                ? "Redo applied" : "Undo applied";
        respond(requester, request, true, VttSceneCommandResultPayload.OK,
                message, state.authorityRevision());
        for (ServerPlayer player : requester.getServer().getPlayerList().getPlayers()) {
            VttServerVisionSourceSync.markCurrentAssetsSent(player, state);
            VttServerAssetSyncService.sendActiveSceneAssets(
                    player, state.replicatedSceneFor(player),
                    VttServerPlayerEvents.isMaster(player), () -> {
                        VttServerSceneSnapshotSync.sendToPlayer(player, state);
                        VttServerVisionSourceSync.sendToPlayer(player, state);
                    });
        }
        VTT.LOGGER.info("Applied authoritative VTT {} from {} in scene {}",
                request.operation(), requester.getGameProfile().getName(), request.sceneId());
    }

    private static boolean validRequest(VttSceneHistoryCommandPayload request) {
        return request.requestId() != null && !request.requestId().isBlank()
                && request.requestId().length() <= 64
                && request.authorityRevision() > 0L
                && (VttSceneHistoryCommandPayload.UNDO.equals(request.operation())
                || VttSceneHistoryCommandPayload.REDO.equals(request.operation()))
                && request.sceneId() != null && !request.sceneId().isBlank()
                && request.sceneId().length() <= 128
                && request.expectedFingerprint() != null
                && request.expectedFingerprint().matches("[0-9a-f]{64}")
                && request.targetSceneJson() != null && !request.targetSceneJson().isBlank()
                && request.targetSceneJson().length()
                <= VttSceneHistoryCommandPayload.MAX_SCENE_JSON_LENGTH;
    }

    private static void respond(
            ServerPlayer player, VttSceneHistoryCommandPayload request,
            boolean success, String code, String message, long authorityRevision
    ) {
        PacketDistributor.sendToPlayer(player, new VttSceneCommandResultPayload(
                request.requestId(), success, code, message, authorityRevision,
                request.sceneId()));
    }

    private static void syncRequester(ServerPlayer player, VttServerTabletopState state) {
        VttServerVisionSourceSync.markCurrentAssetsSent(player, state);
        VttServerAssetSyncService.sendActiveSceneAssets(
                player, state.replicatedSceneFor(player),
                VttServerPlayerEvents.isMaster(player), () -> {
                    VttServerSceneSnapshotSync.sendToPlayer(player, state);
                    VttServerVisionSourceSync.sendToPlayer(player, state);
                });
    }
}
