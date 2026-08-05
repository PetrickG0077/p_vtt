package com.petrick.vtt.network.client;

import com.petrick.vtt.core.session.VTTSession;
import com.petrick.vtt.editor.screen.VTTScreen;
import com.petrick.vtt.network.payload.VttShowCommandPayload;
import com.petrick.vtt.network.payload.VttShowUpdatePayload;
import com.petrick.vtt.network.payload.VttShowPreloadProgressPayload;
import com.petrick.vtt.network.payload.VttShowPreloadStatusPayload;
import com.petrick.vtt.feature.media.VttVideoFrameService;
import com.petrick.vtt.feature.media.VttAudioPlayerService;
import com.petrick.vtt.feature.media.VttVideoPreferences;
import com.petrick.vtt.VTT;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.List;

/** Client presentation state with local fade timing. */
public final class VttClientShowState {
    private static final Gson GSON = new Gson();
    private static final long FADE_IN_MILLIS = 450L;
    private static final long FADE_OUT_MILLIS = 350L;
    private static String relativePath = "";
    private static boolean targetActive;
    private static boolean playing;
    private static boolean loop;
    private static boolean endCommandSent;
    private static long playbackPositionMillis;
    private static long playbackReceivedAtMillis;
    private static final VttAudioPlayerService VIDEO_AUDIO = new VttAudioPlayerService();
    private static final VttVideoPreferences.State VIDEO_PREFERENCES =
            VttVideoPreferences.load();
    private static float videoVolume = VIDEO_PREFERENCES.volume();
    private static boolean videoMuted = VIDEO_PREFERENCES.muted();
    private static float transitionStartAlpha;
    private static long transitionStartedAt;
    private static String preloadPath = "";
    private static List<PreloadClientStatus> preloadStatuses = List.of();
    private static String lastReportedPreloadStatus = "";
    private static float lastReportedPreloadProgress = -1.0F;
    private static long lastPreloadReportAt;

    private VttClientShowState() {}

    public static void show(VTTSession session, String path) {
        if (session.isNetworkAuthorityActive()) {
            if (session.isLocalMaster()) PacketDistributor.sendToServer(
                    new VttShowCommandPayload(VttShowCommandPayload.SHOW, path, 0L));
        } else accept(new VttShowUpdatePayload(path, true, false, false,
                0L, System.currentTimeMillis()));
    }

    public static void forceShow(VTTSession session, String path) {
        if (session.isNetworkAuthorityActive()) {
            if (session.isLocalMaster()) PacketDistributor.sendToServer(
                    new VttShowCommandPayload(VttShowCommandPayload.FORCE_SHOW, path, 0L));
        } else show(session, path);
    }

    public static void preload(VTTSession session, String path) {
        if (session.isNetworkAuthorityActive()) {
            if (session.isLocalMaster()) PacketDistributor.sendToServer(
                    new VttShowCommandPayload(VttShowCommandPayload.PRELOAD, path, 0L));
        } else acceptPreload(path);
    }

    public static void acceptPreload(String path) {
        if (path == null || path.isBlank()) return;
        preloadPath = path;
        lastReportedPreloadStatus = "";
        lastReportedPreloadProgress = -1.0F;
        var session = VTT.getApplication().getActiveSession();
        if (session.getAssetLibraryScanResult() == null) return;
        session.getAssetLibraryScanResult().entries().stream()
                .filter(entry -> entry.relativePath().replace('\\', '/').equals(path))
                .map(entry -> entry.absolutePath()).findFirst()
                .ifPresent(VttVideoFrameService::preload);
    }

    public static void acceptPreloadStatus(VttShowPreloadStatusPayload payload) {
        if (payload == null) return;
        preloadPath = payload.relativePath() == null ? "" : payload.relativePath();
        try {
            List<PreloadClientStatus> decoded = GSON.fromJson(payload.statusesJson(),
                    new TypeToken<List<PreloadClientStatus>>() {}.getType());
            preloadStatuses = decoded == null ? List.of() : List.copyOf(decoded);
        } catch (Exception exception) {
            preloadStatuses = List.of();
            VTT.LOGGER.warn("Could not decode VTT show preload status", exception);
        }
    }

    public static void tickPreload(VTTSession session) {
        if (preloadPath.isBlank() || session.getAssetLibraryScanResult() == null) return;
        Path file = session.getAssetLibraryScanResult().entries().stream()
                .filter(entry -> entry.relativePath().replace('\\', '/').equals(preloadPath))
                .map(entry -> entry.absolutePath()).findFirst().orElse(null);
        String status;
        float progress;
        if (file == null) {
            status = "WAITING";
            progress = 0.0F;
        } else if (!VttVideoFrameService.preloadFailure(file).isBlank()) {
            status = "FAILED";
            progress = VttVideoFrameService.preloadProgress(file);
        } else if (VttVideoFrameService.isPreloaded(file)) {
            status = "READY";
            progress = 1.0F;
        } else {
            if (!VttVideoFrameService.isPreloading(file)) {
                VttVideoFrameService.preload(file);
            }
            status = "LOADING";
            progress = VttVideoFrameService.preloadProgress(file);
        }
        long now = System.currentTimeMillis();
        boolean changed = !status.equals(lastReportedPreloadStatus)
                || Math.abs(progress - lastReportedPreloadProgress) >= 0.02F;
        if (!changed && now - lastPreloadReportAt < 750L) return;
        lastReportedPreloadStatus = status;
        lastReportedPreloadProgress = progress;
        lastPreloadReportAt = now;
        if (session.isNetworkAuthorityActive()) {
            PacketDistributor.sendToServer(new VttShowPreloadProgressPayload(
                    preloadPath, status, progress));
        } else {
            preloadStatuses = List.of(new PreloadClientStatus(
                    session.getLocalPlayerId(), "Local Player", status, progress));
        }
    }

    public static void close(VTTSession session) {
        if (session.isNetworkAuthorityActive()) {
            if (session.isLocalMaster()) PacketDistributor.sendToServer(
                    new VttShowCommandPayload(VttShowCommandPayload.CLOSE, "", 0L));
        } else accept(new VttShowUpdatePayload(relativePath, false, false, loop, 0L,
                System.currentTimeMillis()));
    }

    public static void togglePlayback(VTTSession session) {
        long duration = VttVideoFrameService.durationMillis();
        boolean restartFromEnd = !playing && duration > 0L && playbackMillis() >= duration;
        String operation = restartFromEnd ? VttShowCommandPayload.RESTART
                : playing ? VttShowCommandPayload.PAUSE : VttShowCommandPayload.PLAY;
        if (session.isNetworkAuthorityActive()) {
            if (session.isLocalMaster()) PacketDistributor.sendToServer(
                    new VttShowCommandPayload(operation, "", playbackMillis()));
        } else accept(new VttShowUpdatePayload(relativePath, targetActive, !playing, loop,
                restartFromEnd ? 0L : playbackMillis(),
                System.currentTimeMillis()));
    }

    public static void seek(VTTSession session, long positionMillis) {
        long safePosition = Math.max(0L, positionMillis);
        if (session.isNetworkAuthorityActive()) {
            if (session.isLocalMaster()) PacketDistributor.sendToServer(
                    new VttShowCommandPayload(VttShowCommandPayload.SEEK, "", safePosition));
        } else accept(new VttShowUpdatePayload(relativePath, targetActive, playing, loop,
                safePosition, System.currentTimeMillis()));
    }

    public static void resetPlayback(VTTSession session) {
        if (session.isNetworkAuthorityActive()) {
            if (session.isLocalMaster()) PacketDistributor.sendToServer(
                    new VttShowCommandPayload(VttShowCommandPayload.RESET, "", 0L));
        } else accept(new VttShowUpdatePayload(relativePath, targetActive, false, loop,
                0L, System.currentTimeMillis()));
    }

    public static void toggleLoop(VTTSession session) {
        if (session.isNetworkAuthorityActive()) {
            if (session.isLocalMaster()) PacketDistributor.sendToServer(
                    new VttShowCommandPayload(VttShowCommandPayload.TOGGLE_LOOP, "", 0L));
        } else accept(new VttShowUpdatePayload(relativePath, targetActive, playing,
                !loop, playbackMillis(), System.currentTimeMillis()));
    }

    public static synchronized void updatePlaybackEnd(VTTSession session, long durationMillis) {
        if (!playing || durationMillis <= 0L || playbackMillis() < durationMillis
                || endCommandSent || !session.isLocalMaster()) return;
        endCommandSent = true;
        String operation = loop ? VttShowCommandPayload.RESTART : VttShowCommandPayload.END;
        if (session.isNetworkAuthorityActive()) {
            PacketDistributor.sendToServer(new VttShowCommandPayload(
                    operation, "", durationMillis));
        } else {
            accept(new VttShowUpdatePayload(relativePath, targetActive, loop, loop,
                    loop ? 0L : durationMillis, System.currentTimeMillis()));
        }
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
        loop = update.loop();
        endCommandSent = false;
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
        if (transitionStartedAt == 0L) {
            float result = targetActive ? 1.0F : 0.0F;
            applyVideoVolume(result);
            return result;
        }
        long duration = targetActive ? FADE_IN_MILLIS : FADE_OUT_MILLIS;
        float progress = Math.min(1.0F,
                (System.currentTimeMillis() - transitionStartedAt) / (float) duration);
        float result = targetActive
                ? transitionStartAlpha + (1.0F - transitionStartAlpha) * progress
                : transitionStartAlpha * (1.0F - progress);
        applyVideoVolume(result);
        if (!targetActive && result <= 0.001F && VIDEO_AUDIO.isPlaying()) {
            VIDEO_AUDIO.stop();
        }
        return result;
    }

    public static String relativePath() { return relativePath; }
    public static boolean isActive() { return targetActive; }
    public static boolean isPlaying() { return playing; }
    public static boolean isLoop() { return loop; }
    public static float videoVolume() { return videoVolume; }
    public static boolean isVideoMuted() { return videoMuted; }
    public static String preloadPath() { return preloadPath; }
    public static List<PreloadClientStatus> preloadStatuses() { return preloadStatuses; }
    public static boolean allPreloadClientsReady(String path) {
        return path != null && path.equals(preloadPath) && !preloadStatuses.isEmpty()
                && preloadStatuses.stream().allMatch(status -> "READY".equals(status.status()));
    }
    public static void setVideoVolume(float value, boolean save) {
        videoVolume = Math.max(0.0F, Math.min(1.0F, value));
        applyVideoVolume(alpha());
        if (save) VttVideoPreferences.save(videoVolume, videoMuted);
    }
    public static void toggleVideoMute() {
        videoMuted = !videoMuted;
        applyVideoVolume(alpha());
        VttVideoPreferences.save(videoVolume, videoMuted);
    }
    public static long playbackMillis() {
        long position = playing ? playbackPositionMillis + Math.max(0L,
                System.currentTimeMillis() - playbackReceivedAtMillis) : playbackPositionMillis;
        long duration = VttVideoFrameService.durationMillis();
        return duration > 0L ? Math.min(position, duration) : position;
    }
    public static boolean isVideo() { return relativePath.toLowerCase().endsWith(".mp4"); }
    public static boolean blocksInput() { return targetActive || alpha() > 0.01F; }

    private static void syncVideoAudio() {
        if (!isVideo()) {
            VIDEO_AUDIO.stop();
            return;
        }
        if (!targetActive) return;
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

    private static void applyVideoVolume(float presentationAlpha) {
        VIDEO_AUDIO.setMasterVolume((videoMuted ? 0.0F : videoVolume)
                * Math.max(0.0F, Math.min(1.0F, presentationAlpha)));
    }

    public static synchronized void reset() {
        relativePath = "";
        targetActive = false;
        playing = false;
        loop = false;
        endCommandSent = false;
        playbackPositionMillis = 0L;
        playbackReceivedAtMillis = 0L;
        VIDEO_AUDIO.stop();
        transitionStartAlpha = 0.0F;
        transitionStartedAt = 0L;
        preloadPath = "";
        preloadStatuses = List.of();
        lastReportedPreloadStatus = "";
        lastReportedPreloadProgress = -1.0F;
        lastPreloadReportAt = 0L;
    }

    public record PreloadClientStatus(
            String playerId, String playerName, String status, float progress
    ) {}
}
