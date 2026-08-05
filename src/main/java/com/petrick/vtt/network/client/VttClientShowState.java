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
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

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
    private static int videoCacheMemoryMb = VIDEO_PREFERENCES.cacheMemoryMb();
    static {
        VttVideoFrameService.setPreloadMemoryLimitMb(videoCacheMemoryMb);
    }
    private static float transitionStartAlpha;
    private static long transitionStartedAt;
    private static final Set<String> requestedPreloadPaths = new LinkedHashSet<>();
    private static List<PreparedPreloadStatus> preloadVideos = List.of();
    private static final Map<String, ReportState> preloadReports = new LinkedHashMap<>();
    private static long lastServerChangedAt;
    private static long lastReturnedPlaybackMillis;
    private static long lastAudioSyncAt;
    private static long synchronizationSuppressedUntil;
    private static int softVideoCorrections;
    private static int hardVideoCorrections;
    private static int audioCorrections;
    private static long lastCheckpointDeltaMillis;

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
        requestedPreloadPaths.add(path);
        preloadReports.remove(path);
        var session = VTT.getApplication().getActiveSession();
        if (session.getAssetLibraryScanResult() == null) return;
        session.getAssetLibraryScanResult().entries().stream()
                .filter(entry -> entry.relativePath().replace('\\', '/').equals(path))
                .map(entry -> entry.absolutePath()).findFirst()
                .ifPresent(VttVideoFrameService::preload);
    }

    public static void acceptPreloadStatus(VttShowPreloadStatusPayload payload) {
        if (payload == null) return;
        try {
            List<PreparedPreloadStatus> decoded = GSON.fromJson(payload.preloadsJson(),
                    new TypeToken<List<PreparedPreloadStatus>>() {}.getType());
            preloadVideos = decoded == null ? List.of() : List.copyOf(decoded);
        } catch (Exception exception) {
            preloadVideos = List.of();
            VTT.LOGGER.warn("Could not decode VTT show preload status", exception);
        }
    }

    public static void tickPreload(VTTSession session) {
        if (requestedPreloadPaths.isEmpty() || session.getAssetLibraryScanResult() == null) return;
        Map<String, PreloadClientStatus> localStatuses = new LinkedHashMap<>();
        for (String preloadPath : List.copyOf(requestedPreloadPaths)) {
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
            ReportState report = preloadReports.get(preloadPath);
            boolean changed = report == null || !status.equals(report.status())
                    || Math.abs(progress - report.progress()) >= 0.02F;
            if (changed || now - (report == null ? 0L : report.reportedAt()) >= 750L) {
                preloadReports.put(preloadPath, new ReportState(status, progress, now));
                if (session.isNetworkAuthorityActive()) {
                    PacketDistributor.sendToServer(new VttShowPreloadProgressPayload(
                            preloadPath, status, progress));
                }
            }
            localStatuses.put(preloadPath, new PreloadClientStatus(
                    session.getLocalPlayerId(), "Local Player", status, progress));
        }
        if (!session.isNetworkAuthorityActive()) {
            List<PreparedPreloadStatus> local = new java.util.ArrayList<>();
            for (Map.Entry<String, PreloadClientStatus> entry : localStatuses.entrySet()) {
                local.add(new PreparedPreloadStatus(entry.getKey(), List.of(entry.getValue())));
            }
            preloadVideos = List.copyOf(local);
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
        boolean checkpoint = update.changedAtMillis() == lastServerChangedAt
                && update.active() == targetActive && update.playing() == playing
                && update.loop() == loop
                && (update.relativePath() == null || update.relativePath().isBlank()
                || update.relativePath().equals(relativePath));
        if (checkpoint && playing) {
            long localPosition = rawPlaybackMillis();
            long delta = update.positionMillis() - localPosition;
            lastCheckpointDeltaMillis = delta;
            long absoluteDelta = Math.abs(delta);
            if (absoluteDelta < 120L) return;
            if (absoluteDelta < 750L) {
                playbackPositionMillis = Math.max(lastReturnedPlaybackMillis,
                        localPosition + delta / 4L);
                playbackReceivedAtMillis = System.currentTimeMillis();
                softVideoCorrections++;
                return;
            }
            playbackPositionMillis = Math.max(0L, update.positionMillis());
            playbackReceivedAtMillis = System.currentTimeMillis();
            lastReturnedPlaybackMillis = playbackPositionMillis;
            hardVideoCorrections++;
            synchronizationSuppressedUntil = System.currentTimeMillis() + 1_500L;
            if (isVideo()) VttVideoFrameService.requestSeek(playbackPositionMillis);
            synchronizeVideoAudioClock(true);
            return;
        }
        float current = alpha();
        if (update.relativePath() != null && !update.relativePath().isBlank()) {
            relativePath = update.relativePath();
        }
        transitionStartAlpha = current;
        targetActive = update.active();
        playing = update.playing();
        loop = update.loop();
        endCommandSent = false;
        lastServerChangedAt = update.changedAtMillis();
        playbackPositionMillis = Math.max(0L, update.positionMillis());
        playbackReceivedAtMillis = System.currentTimeMillis();
        synchronizationSuppressedUntil = playbackReceivedAtMillis + 1_500L;
        lastReturnedPlaybackMillis = playbackPositionMillis;
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
    public static List<PreparedPreloadStatus> preloadVideos() { return preloadVideos; }
    public static boolean allPreloadClientsReady(String path) {
        if (path == null) return false;
        return preloadVideos.stream().filter(video -> path.equals(video.relativePath()))
                .findFirst().map(video -> !video.players().isEmpty()
                        && video.players().stream().allMatch(status -> "READY".equals(status.status())))
                .orElse(false);
    }
    public static void tickPlaybackSynchronization() {
        if (!targetActive || !playing || !isVideo()) return;
        long now = System.currentTimeMillis();
        if (now < synchronizationSuppressedUntil) return;
        if (now - lastAudioSyncAt < 1_000L) return;
        lastAudioSyncAt = now;
        synchronizeVideoAudioClock(false);
    }
    public static String videoSyncDiagnostics() {
        long authority = playbackMillis();
        long videoDelta = VttVideoFrameService.displayedPositionMillis() - authority;
        long audioDelta = VIDEO_AUDIO.isPlaying()
                ? Math.round(VIDEO_AUDIO.positionSeconds() * 1_000.0) - authority : 0L;
        return "Video sync: frame " + signed(videoDelta) + "ms, audio "
                + signed(audioDelta) + "ms, checkpoint "
                + signed(lastCheckpointDeltaMillis) + "ms, corrections "
                + softVideoCorrections + "/" + hardVideoCorrections + "/"
                + audioCorrections;
    }
    public static void setVideoVolume(float value, boolean save) {
        videoVolume = Math.max(0.0F, Math.min(1.0F, value));
        applyVideoVolume(alpha());
        if (save) VttVideoPreferences.save(videoVolume, videoMuted, videoCacheMemoryMb);
    }
    public static void toggleVideoMute() {
        videoMuted = !videoMuted;
        applyVideoVolume(alpha());
        VttVideoPreferences.save(videoVolume, videoMuted, videoCacheMemoryMb);
    }
    public static int videoCacheMemoryMb() { return videoCacheMemoryMb; }
    public static void setVideoCacheMemoryMb(int value) {
        videoCacheMemoryMb = VttVideoPreferences.clampCacheMemory(value);
        VttVideoFrameService.setPreloadMemoryLimitMb(videoCacheMemoryMb);
        VttVideoPreferences.save(videoVolume, videoMuted, videoCacheMemoryMb);
    }
    public static synchronized long playbackMillis() {
        long position = rawPlaybackMillis();
        long duration = VttVideoFrameService.durationMillis();
        if (duration > 0L) position = Math.min(position, duration);
        if (playing) {
            position = Math.max(lastReturnedPlaybackMillis, position);
            lastReturnedPlaybackMillis = position;
        }
        return position;
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

    private static long rawPlaybackMillis() {
        return playing ? playbackPositionMillis + Math.max(0L,
                System.currentTimeMillis() - playbackReceivedAtMillis) : playbackPositionMillis;
    }

    private static void synchronizeVideoAudioClock(boolean force) {
        if (!VIDEO_AUDIO.isPlaying() || VIDEO_AUDIO.durationSeconds() <= 0.0) return;
        long authority = rawPlaybackMillis();
        long audio = Math.round(VIDEO_AUDIO.positionSeconds() * 1_000.0);
        long delta = authority - audio;
        if (!force && Math.abs(delta) < 900L) return;
        double ratio = authority / (VIDEO_AUDIO.durationSeconds() * 1_000.0);
        VIDEO_AUDIO.seek(Math.max(0.0, Math.min(1.0, ratio)));
        audioCorrections++;
    }

    private static String signed(long value) {
        return value > 0L ? "+" + value : Long.toString(value);
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
        requestedPreloadPaths.clear();
        preloadVideos = List.of();
        preloadReports.clear();
        lastServerChangedAt = 0L;
        lastReturnedPlaybackMillis = 0L;
        lastAudioSyncAt = 0L;
        synchronizationSuppressedUntil = 0L;
        softVideoCorrections = 0;
        hardVideoCorrections = 0;
        audioCorrections = 0;
        lastCheckpointDeltaMillis = 0L;
    }

    public record PreloadClientStatus(
            String playerId, String playerName, String status, float progress
    ) {}
    public record PreparedPreloadStatus(
            String relativePath, List<PreloadClientStatus> players
    ) {}
    private record ReportState(String status, float progress, long reportedAt) {}
}
