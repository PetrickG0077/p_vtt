package com.petrick.vtt.network.server;

import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttPresentationCommandPayload;
import com.petrick.vtt.network.payload.VttPresentationUpdatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Validates master presentation commands and broadcasts them to non-master clients. */
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
            boolean blackout = state.togglePresentationBlackout();
            update = blackoutUpdate(blackout);
            VTT.LOGGER.info("VTT player blackout set to {} by {}",
                    blackout, requester.getGameProfile().getName());
        } else if (VttPresentationCommandPayload.SYNC_CAMERA.equals(command.operation())) {
            if (!validCamera(command)) {
                reject(requester, "invalid camera");
                return;
            }
            update = new VttPresentationUpdatePayload(
                    VttPresentationCommandPayload.SYNC_CAMERA,
                    state.isPresentationBlackout(),
                    command.cameraX(), command.cameraY(), command.cameraZoom());
        } else {
            reject(requester, "unknown operation");
            return;
        }

        for (ServerPlayer player : requester.getServer().getPlayerList().getPlayers()) {
            if (!VttServerPlayerEvents.isMaster(player)) {
                PacketDistributor.sendToPlayer(player, update);
            }
        }
    }

    public static VttPresentationUpdatePayload currentBlackout(
            VttServerTabletopState state
    ) {
        return blackoutUpdate(state != null && state.isPresentationBlackout());
    }

    private static VttPresentationUpdatePayload blackoutUpdate(boolean blackout) {
        return new VttPresentationUpdatePayload(
                VttPresentationCommandPayload.TOGGLE_BLACKOUT,
                blackout, 0.0, 0.0, 1.0);
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
