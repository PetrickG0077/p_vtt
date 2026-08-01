package com.petrick.vtt.network.server;

import com.petrick.vtt.VTT;
import com.petrick.vtt.feature.tabletop.VttSceneLimits;
import com.petrick.vtt.network.payload.VttSceneCommandPayload;
import com.petrick.vtt.network.payload.VttSceneCommandResultPayload;
import com.petrick.vtt.network.payload.VttAssetManagerChangePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Applies scene lifecycle commands exclusively on the authoritative server. */
public final class VttServerSceneCommandHandler {
    private VttServerSceneCommandHandler() {}

    public static void handle(VttSceneCommandPayload request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer requester) || request == null) return;
        VttServerTabletopState state = VttServerTabletopState.get();
        if (!VttServerPlayerEvents.isMaster(requester)) {
            respond(requester, request.requestId(), false,
                    VttSceneCommandResultPayload.PERMISSION_DENIED,
                    "Only masters can update scenes", state.authorityRevision(), "");
            return;
        }
        if (!VttServerRequestRateLimiter.allow(
                requester, VttServerRequestRateLimiter.Category.SCENE_COMMAND)) {
            respond(requester, request.requestId(), false,
                    VttSceneCommandResultPayload.REJECTED,
                    "Too many scene operations; try again shortly",
                    state.authorityRevision(), "");
            return;
        }
        if (request.requestId() == null || request.requestId().isBlank()
                || request.requestId().length() > 64
                || request.operation() == null || request.targetId() == null
                || request.value() == null || request.backgroundAssetId() == null
                || request.mapTextureMode() == null
                || request.backgroundAssetId().length() > 512
                || request.mapTextureMode().length() > 16) {
            respond(requester, request.requestId(), false,
                    VttSceneCommandResultPayload.INVALID_REQUEST,
                    "Invalid scene request", state.authorityRevision(), "");
            return;
        }
        if (request.authorityRevision() != state.authorityRevision()) {
            respond(requester, request.requestId(), false,
                    VttSceneCommandResultPayload.STALE_REVISION,
                    "Scenes changed on the server; resynchronizing",
                    state.authorityRevision(), "");
            return;
        }

        if (VttSceneCommandPayload.CREATE.equals(request.operation())
                || VttSceneCommandPayload.DUPLICATE.equals(request.operation())) {
            var violation = VttSceneLimits.sceneCreation(state.activeTabletop());
            if (violation != null) {
                respond(requester, request.requestId(), false,
                        VttSceneCommandResultPayload.LIMIT_REACHED,
                        violation.message(), state.authorityRevision(), "");
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
            case VttSceneCommandPayload.CLEAR_MAPS -> state.clearActiveSceneMaps();
            case VttSceneCommandPayload.RENAME -> state.renameScene(request.targetId(), request.value());
            case VttSceneCommandPayload.DELETE -> state.deleteScene(request.targetId());
            case VttSceneCommandPayload.DUPLICATE ->
                    state.duplicateAndActivateScene(request.targetId());
            default -> false;
        };
        if (!changed) {
            respond(requester, request.requestId(), false,
                    VttSceneCommandResultPayload.REJECTED,
                    rejectionMessage(request.operation()), state.authorityRevision(), "");
            return;
        }

        MinecraftServer server = requester.getServer();
        if (server == null) {
            respond(requester, request.requestId(), false,
                    VttSceneCommandResultPayload.REJECTED,
                    "Server unavailable", state.authorityRevision(), "");
            return;
        }
        String resultSceneId = switch (request.operation()) {
            case VttSceneCommandPayload.RENAME -> request.targetId();
            case VttSceneCommandPayload.DELETE, VttSceneCommandPayload.CREATE,
                 VttSceneCommandPayload.DUPLICATE, VttSceneCommandPayload.SWITCH,
                 VttSceneCommandPayload.SET_BACKGROUND,
                 VttSceneCommandPayload.CLEAR_MAPS -> state.activeScene().getId();
            default -> "";
        };
        respond(requester, request.requestId(), true,
                VttSceneCommandResultPayload.OK, sceneChangeMessage(request.operation()),
                state.authorityRevision(), resultSceneId);
        boolean sceneChanged = !previousSceneId.equals(state.activeScene().getId());
        boolean assetsChanged = sceneChanged
                || VttSceneCommandPayload.SET_BACKGROUND.equals(request.operation());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != requester && VttServerPlayerEvents.isMaster(player)
                    && isCatalogMutation(request.operation())) {
                PacketDistributor.sendToPlayer(player, new VttAssetManagerChangePayload(
                        state.authorityRevision(), request.operation(), "SCENES",
                        requester.getGameProfile().getName(),
                        sceneChangeMessage(request.operation())));
            }
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

    private static void respond(
            ServerPlayer player, String requestId, boolean success, String code,
            String message, long authorityRevision, String sceneId
    ) {
        PacketDistributor.sendToPlayer(player, new VttSceneCommandResultPayload(
                requestId == null ? "" : requestId, success, code, message,
                authorityRevision, sceneId == null ? "" : sceneId));
    }

    private static String rejectionMessage(String operation) {
        return switch (operation) {
            case VttSceneCommandPayload.CREATE -> "Could not create the scene";
            case VttSceneCommandPayload.SWITCH -> "Could not activate the scene";
            case VttSceneCommandPayload.SET_BACKGROUND -> "Could not change the scene background";
            case VttSceneCommandPayload.CLEAR_MAPS -> "Could not clear the scene maps";
            case VttSceneCommandPayload.RENAME -> "Could not rename the scene";
            case VttSceneCommandPayload.DELETE -> "The last scene cannot be deleted";
            case VttSceneCommandPayload.DUPLICATE -> "Could not duplicate the scene";
            default -> "Unknown scene command";
        };
    }

    private static boolean isCatalogMutation(String operation) {
        return VttSceneCommandPayload.CREATE.equals(operation)
                || VttSceneCommandPayload.RENAME.equals(operation)
                || VttSceneCommandPayload.DELETE.equals(operation)
                || VttSceneCommandPayload.DUPLICATE.equals(operation);
    }

    private static String sceneChangeMessage(String operation) {
        return switch (operation) {
            case VttSceneCommandPayload.CREATE -> "Scene created";
            case VttSceneCommandPayload.SWITCH -> "Scene activated";
            case VttSceneCommandPayload.SET_BACKGROUND -> "Scene background updated";
            case VttSceneCommandPayload.CLEAR_MAPS -> "Scene maps cleared";
            case VttSceneCommandPayload.RENAME -> "Scene renamed";
            case VttSceneCommandPayload.DELETE -> "Scene deleted";
            case VttSceneCommandPayload.DUPLICATE -> "Scene duplicated";
            default -> "Scenes updated";
        };
    }
}
