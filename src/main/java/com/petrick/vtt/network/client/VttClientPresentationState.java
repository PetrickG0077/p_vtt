package com.petrick.vtt.network.client;

import com.petrick.vtt.network.payload.VttPresentationCommandPayload;
import com.petrick.vtt.network.payload.VttPresentationUpdatePayload;

/** Client-side animation and pending camera state for master presentation controls. */
public final class VttClientPresentationState {
    private static final long CURTAIN_DURATION_MS = 700L;

    private static boolean blackout;
    private static boolean cameraFollow;
    private static float curtainFrom;
    private static long curtainStartedAt;
    private static CameraTarget pendingCamera;

    private VttClientPresentationState() {
    }

    public static synchronized void accept(
            VttPresentationUpdatePayload update, boolean acceptCamera
    ) {
        if (update == null) return;
        if (VttPresentationCommandPayload.TOGGLE_BLACKOUT.equals(update.operation())) {
            long now = System.currentTimeMillis();
            curtainFrom = curtainProgress(now);
            blackout = update.blackout();
            cameraFollow = update.cameraFollow();
            curtainStartedAt = now;
            return;
        }
        if (VttPresentationCommandPayload.TOGGLE_CAMERA_FOLLOW.equals(update.operation())
                || VttPresentationCommandPayload.CURRENT_STATE.equals(update.operation())) {
            if (VttPresentationCommandPayload.CURRENT_STATE.equals(update.operation())) {
                long now = System.currentTimeMillis();
                curtainFrom = curtainProgress(now);
                blackout = update.blackout();
                curtainStartedAt = now;
            }
            cameraFollow = update.cameraFollow();
            if (!cameraFollow) pendingCamera = null;
            if (acceptCamera && cameraFollow && validCamera(update)) {
                pendingCamera = new CameraTarget(
                        update.cameraX(), update.cameraY(), update.cameraZoom(), true);
            }
            return;
        }
        if (VttPresentationCommandPayload.SYNC_CAMERA.equals(update.operation())
                && validCamera(update)) {
            cameraFollow = update.cameraFollow();
            if (acceptCamera) {
                pendingCamera = new CameraTarget(
                        update.cameraX(), update.cameraY(), update.cameraZoom(),
                        update.cameraFollow());
            }
        }
    }

    public static synchronized float curtainProgress() {
        return curtainProgress(System.currentTimeMillis());
    }

    private static float curtainProgress(long now) {
        if (curtainStartedAt == 0L) return blackout ? 1.0F : 0.0F;
        float elapsed = Math.min(1.0F,
                Math.max(0.0F, (now - curtainStartedAt) / (float) CURTAIN_DURATION_MS));
        float eased = elapsed * elapsed * (3.0F - 2.0F * elapsed);
        float target = blackout ? 1.0F : 0.0F;
        return curtainFrom + (target - curtainFrom) * eased;
    }

    public static synchronized CameraTarget consumeCamera() {
        CameraTarget result = pendingCamera;
        pendingCamera = null;
        return result;
    }

    public static synchronized boolean isFollowingMasterCamera() {
        return cameraFollow;
    }

    public static synchronized void reset() {
        blackout = false;
        cameraFollow = false;
        curtainFrom = 0.0F;
        curtainStartedAt = 0L;
        pendingCamera = null;
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    private static boolean validCamera(VttPresentationUpdatePayload update) {
        return finite(update.cameraX(), update.cameraY(), update.cameraZoom())
                && update.cameraZoom() > 0.0;
    }

    public record CameraTarget(double x, double y, double zoom, boolean following) {
    }
}
