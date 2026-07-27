package com.petrick.vtt.network.server;

import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttPresentationCommandPayload;
import com.petrick.vtt.network.payload.VttPresentationUpdatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Validates master presentation commands and broadcasts the confirmed state. */
public final class VttServerPresentationHandler {
    private static final double MAX_CAMERA_COORDINATE = 10_000_000.0;
    private static final double MIN_CAMERA_ZOOM = 0.1;
    private static final double MAX_CAMERA_ZOOM = 8.0;

    private VttServerPresentationHandler() {
    }

    public static void handle(VttPresentationCommandPayload command, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer requester)) return;
        if (!VttServerRequestRateLimiter.allow(
                requester, VttServerRequestRateLimiter.Category.PRESENTATION)) return;
        if (!VttServerPlayerEvents.isMaster(requester)) {
            reject(requester, "permission denied");
            return;
        }
        VttServerTabletopState state = VttServerTabletopState.get();
        if (command == null || command.authorityRevision() != state.authorityRevision()) {
            reject(requester, "invalid authority revision");
            return;
        }

        VttPresentationUpdatePayload update;
        if (VttPresentationCommandPayload.TOGGLE_BLACKOUT.equals(command.operation())) {
            state.togglePresentationBlackout();
            update = update(
                    VttPresentationCommandPayload.TOGGLE_BLACKOUT, state.presentationState());
            VTT.LOGGER.info("VTT player blackout set to {} by {}",
                    state.isPresentationBlackout(), requester.getGameProfile().getName());
        } else if (VttPresentationCommandPayload.TOGGLE_CAMERA_FOLLOW.equals(
                command.operation())) {
            if (!validCamera(command)) {
                reject(requester, "invalid camera");
                return;
            }
            boolean following = state.togglePresentationCameraFollow(
                    command.cameraX(), command.cameraY(), command.cameraZoom());
            update = update(
                    VttPresentationCommandPayload.TOGGLE_CAMERA_FOLLOW,
                    state.presentationState());
            VTT.LOGGER.info("VTT player camera follow set to {} by {}",
                    following, requester.getGameProfile().getName());
        } else if (VttPresentationCommandPayload.SYNC_CAMERA.equals(command.operation())) {
            if (!validCamera(command)) {
                reject(requester, "invalid camera");
                return;
            }
            state.updatePresentationCamera(
                    command.cameraX(), command.cameraY(), command.cameraZoom());
            update = update(
                    VttPresentationCommandPayload.SYNC_CAMERA, state.presentationState());
        } else {
            reject(requester, "unknown operation");
            return;
        }

        for (ServerPlayer player : requester.getServer().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, update);
        }
    }

    public static VttPresentationUpdatePayload currentPresentation(
            VttServerTabletopState state
    ) {
        VttServerTabletopState.PresentationState current = state == null
                ? new VttServerTabletopState.PresentationState(
                false, false, 0.0, 0.0, 1.0)
                : state.presentationState();
        return update(VttPresentationCommandPayload.CURRENT_STATE, current);
    }

    public static void stopCameraFollow(
            MinecraftServer server, UUID excludedPlayerId
    ) {
        if (server == null) return;
        VttServerTabletopState state = VttServerTabletopState.get();
        if (!state.disablePresentationCameraFollow()) return;
        VttPresentationUpdatePayload update = update(
                VttPresentationCommandPayload.TOGGLE_CAMERA_FOLLOW,
                state.presentationState());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (excludedPlayerId == null || !excludedPlayerId.equals(player.getUUID())) {
                PacketDistributor.sendToPlayer(player, update);
            }
        }
    }

    private static VttPresentationUpdatePayload update(
            String operation, VttServerTabletopState.PresentationState state
    ) {
        return new VttPresentationUpdatePayload(
                operation, state.blackout(), state.cameraFollow(),
                state.cameraX(), state.cameraY(), state.cameraZoom());
    }

    private static boolean validCamera(VttPresentationCommandPayload command) {
        return Double.isFinite(command.cameraX())
                && Double.isFinite(command.cameraY())
                && Double.isFinite(command.cameraZoom())
                && Math.abs(command.cameraX()) <= MAX_CAMERA_COORDINATE
                && Math.abs(command.cameraY()) <= MAX_CAMERA_COORDINATE
                && command.cameraZoom() >= MIN_CAMERA_ZOOM
                && command.cameraZoom() <= MAX_CAMERA_ZOOM;
    }

    private static void reject(ServerPlayer requester, String reason) {
        VttServerRequestRateLimiter.reject(
                requester, VttServerRequestRateLimiter.Category.PRESENTATION, reason);
    }
}
