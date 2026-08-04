package com.petrick.vtt.network.client;

import com.petrick.vtt.core.session.VTTSession;
import com.petrick.vtt.network.payload.VttShowCommandPayload;
import com.petrick.vtt.network.payload.VttShowUpdatePayload;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client presentation state with local fade timing. */
public final class VttClientShowState {
    private static final long FADE_IN_MILLIS = 450L;
    private static final long FADE_OUT_MILLIS = 350L;
    private static String relativePath = "";
    private static boolean targetActive;
    private static float transitionStartAlpha;
    private static long transitionStartedAt;

    private VttClientShowState() {}

    public static void show(VTTSession session, String path) {
        if (session.isNetworkAuthorityActive()) {
            if (session.isLocalMaster()) PacketDistributor.sendToServer(
                    new VttShowCommandPayload(VttShowCommandPayload.SHOW, path));
        } else accept(new VttShowUpdatePayload(path, true, System.currentTimeMillis()));
    }

    public static void close(VTTSession session) {
        if (session.isNetworkAuthorityActive()) {
            if (session.isLocalMaster()) PacketDistributor.sendToServer(
                    new VttShowCommandPayload(VttShowCommandPayload.CLOSE, ""));
        } else accept(new VttShowUpdatePayload(relativePath, false,
                System.currentTimeMillis()));
    }

    public static synchronized void accept(VttShowUpdatePayload update) {
        if (update == null) return;
        float current = alpha();
        if (update.relativePath() != null && !update.relativePath().isBlank()) {
            relativePath = update.relativePath();
        }
        transitionStartAlpha = current;
        targetActive = update.active();
        transitionStartedAt = System.currentTimeMillis();
    }

    public static synchronized float alpha() {
        if (transitionStartedAt == 0L) return targetActive ? 1.0F : 0.0F;
        long duration = targetActive ? FADE_IN_MILLIS : FADE_OUT_MILLIS;
        float progress = Math.min(1.0F,
                (System.currentTimeMillis() - transitionStartedAt) / (float) duration);
        return targetActive
                ? transitionStartAlpha + (1.0F - transitionStartAlpha) * progress
                : transitionStartAlpha * (1.0F - progress);
    }

    public static String relativePath() { return relativePath; }
    public static boolean isActive() { return targetActive; }
    public static boolean blocksInput() { return targetActive || alpha() > 0.01F; }

    public static synchronized void reset() {
        relativePath = "";
        targetActive = false;
        transitionStartAlpha = 0.0F;
        transitionStartedAt = 0L;
    }
}
