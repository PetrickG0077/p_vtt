package com.petrick.vtt.network.client;

import com.petrick.vtt.core.session.VTTSession;
import com.petrick.vtt.editor.screen.VTTScreen;
import com.petrick.vtt.network.payload.VttShowCommandPayload;
import com.petrick.vtt.network.payload.VttShowUpdatePayload;
import com.petrick.vtt.feature.media.VttVideoFrameService;
import com.petrick.vtt.feature.media.VttAudioPlayerService;
import com.petrick.vtt.VTT;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client presentation state with local fade timing. */
public final class VttClientShowState {
    private static final long FADE_IN_MILLIS = 450L;
    private static final long FADE_OUT_MILLIS = 350L;
    private static String relativePath = "";
    private static boolean targetActive;
    private static boolean playing;
    private static long playbackPositionMillis;
    private static long playbackReceivedAtMillis;
    private static final VttAudioPlayerService VIDEO_AUDIO = new VttAudioPlayerService();
    private static float transitionStartAlpha;
    private static long transitionStartedAt;

    private VttClientShowState() {}

    public static void show(VTTSession session, String path) {
        if (session.isNetworkAuthorityActive()) {
            if (session.isLocalMaster()) PacketDistributor.sendToServer(
                    new VttShowCommandPayload(VttShowCommandPayload.SHOW, path, 0L));
        } else accept(new VttShowUpdatePayload(path, true, false, 0L, System.currentTimeMillis()));
    }

    public static void close(VTTSession session) {
        if (session.isNetworkAuthorityActive()) {
            if (session.isLocalMaster()) PacketDistributor.sendToServer(
                    new VttShowCommandPayload(VttShowCommandPayload.CLOSE, "", 0L));
        } else accept(new VttShowUpdatePayload(relativePath, false, false, 0L,
                System.currentTimeMillis()));
    }

    public static void togglePlayback(VTTSession session) {
        String operation = playing ? VttShowCommandPayload.PAUSE : VttShowCommandPayload.PLAY;
        if (session.isNetworkAuthorityActive()) {
            if (session.isLocalMaster()) PacketDistributor.sendToServer(
                    new VttShowCommandPayload(operation, "", playbackMillis()));
        } else accept(new VttShowUpdatePayload(relativePath, targetActive, !playing,
                playbackMillis(),
                System.currentTimeMillis()));
    }

    public static void seek(VTTSession session, long positionMillis) {
        long safePosition = Math.max(0L, positionMillis);
        if (session.isNetworkAuthorityActive()) {
            if (session.isLocalMaster()) PacketDistributor.sendToServer(
                    new VttShowCommandPayload(VttShowCommandPayload.SEEK, "", safePosition));
        } else accept(new VttShowUpdatePayload(relativePath, targetActive, playing,
                safePosition, System.currentTimeMillis()));
    }

    public static synchronized void accept(VttShowUpdatePayload update) {
        if (update == null) return;
        float current = alpha();
        if (update.relativePath() != null && !update.relativePath().isBlank()) {
            relativePath = update.relativePath();
        }
        transitionStartAlpha = current;
        targetActive = update.active();
        playing = update.playing();
        playbackPositionMillis = Math.max(0L, update.positionMillis());
        playbackReceivedAtMillis = System.currentTimeMillis();
        if (isVideo()) VttVideoFrameService.requestSeek(playbackPositionMillis);
        syncVideoAudio();
        transitionStartedAt = System.currentTimeMillis();
        if (targetActive) {
            Minecraft minecraft = Minecraft.getInstance();
            if (!(minecraft.screen instanceof VTTScreen)) {
                minecraft.setScreen(new VTTScreen());
            }
        }
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
    public static boolean isPlaying() { return playing; }
    public static long playbackMillis() {
        return playing ? playbackPositionMillis + Math.max(0L,
                System.currentTimeMillis() - playbackReceivedAtMillis) : playbackPositionMillis;
    }
    public static boolean isVideo() { return relativePath.toLowerCase().endsWith(".mp4"); }
    public static boolean blocksInput() { return targetActive || alpha() > 0.01F; }

    private static void syncVideoAudio() {
        if (!targetActive || !isVideo()) {
            VIDEO_AUDIO.stop();
            return;
        }
        var session = VTT.getApplication().getActiveSession();
        if (session.getAssetLibraryScanResult() == null) return;
        Path file = session.getAssetLibraryScanResult().entries().stream()
                .filter(entry -> entry.relativePath().replace('\\', '/').equals(relativePath))
                .map(entry -> entry.absolutePath()).findFirst().orElse(null);
        if (file == null) return;
        if (VIDEO_AUDIO.track() != null && VIDEO_AUDIO.track().equals(file)
                && !VIDEO_AUDIO.error().isBlank()) return;
        double position = playbackPositionMillis / 1_000.0;
        if (VIDEO_AUDIO.track() == null || !VIDEO_AUDIO.track().equals(file)
                || !VIDEO_AUDIO.isPlaying()) {
            VIDEO_AUDIO.play(file, position, !playing);
            return;
        }
        if (VIDEO_AUDIO.durationSeconds() > 0.0
                && Math.abs(VIDEO_AUDIO.positionSeconds() - position) > 0.75) {
            VIDEO_AUDIO.seek(position / VIDEO_AUDIO.durationSeconds());
        }
        if (playing == VIDEO_AUDIO.isPaused()) VIDEO_AUDIO.togglePause();
    }

    public static synchronized void reset() {
        relativePath = "";
        targetActive = false;
        playing = false;
        playbackPositionMillis = 0L;
        playbackReceivedAtMillis = 0L;
        VIDEO_AUDIO.stop();
        transitionStartAlpha = 0.0F;
        transitionStartedAt = 0L;
    }
}
