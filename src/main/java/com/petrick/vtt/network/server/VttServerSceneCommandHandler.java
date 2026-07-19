package com.petrick.vtt.network.server;

import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttSceneCommandPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Applies scene lifecycle commands exclusively on the authoritative server. */
public final class VttServerSceneCommandHandler {
    private VttServerSceneCommandHandler() {}

    public static void handle(VttSceneCommandPayload request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer requester)) return;
        VttServerTabletopState state = VttServerTabletopState.get();
        if (!VttServerPlayerEvents.isMaster(requester) || request == null
                || request.authorityRevision() != state.authorityRevision()
                || request.operation() == null || request.targetId() == null || request.value() == null) {
            reject(requester, state, "unauthorized or invalid request");
            return;
        }

        String previousSceneId = state.activeScene().getId();
        boolean changed = switch (request.operation()) {
            case VttSceneCommandPayload.CREATE -> state.createAndActivateScene(request.value());
            case VttSceneCommandPayload.SWITCH -> state.switchToScene(request.targetId());
            case VttSceneCommandPayload.SET_BACKGROUND -> state.setActiveSceneBackground(request.value());
            case VttSceneCommandPayload.RENAME -> state.renameScene(request.targetId(), request.value());
            case VttSceneCommandPayload.DELETE -> state.deleteScene(request.targetId());
            default -> false;
        };
        if (!changed) {
            reject(requester, state, "command was not accepted");
            return;
        }

        MinecraftServer server = requester.getServer();
        if (server == null) {
            reject(requester, state, "server unavailable");
            return;
        }
        boolean sceneChanged = !previousSceneId.equals(state.activeScene().getId());
        boolean assetsChanged = sceneChanged
                || VttSceneCommandPayload.SET_BACKGROUND.equals(request.operation());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (assetsChanged) {
                VttServerAssetSyncService.sendActiveSceneAssets(
                        player, state.replicatedSceneFor(player), VttServerPlayerEvents.isMaster(player));
                VttServerVisionSourceSync.markCurrentAssetsSent(player, state);
            }
            PacketDistributor.sendToPlayer(player, state.createSnapshotPayload(player));
            VttServerVisionSourceSync.sendToPlayer(player, state);
        }
        VTT.LOGGER.info("Applied VTT scene command {} from {}: active scene is {}",
                request.operation(), requester.getGameProfile().getName(), state.activeScene().getId());
    }

    private static void reject(ServerPlayer requester, VttServerTabletopState state, String reason) {
        VTT.LOGGER.warn("Rejected VTT scene command from {}: {}",
                requester.getGameProfile().getName(), reason);
        PacketDistributor.sendToPlayer(requester, state.createSnapshotPayload(requester));
        VttServerVisionSourceSync.sendToPlayer(requester, state);
    }
}
