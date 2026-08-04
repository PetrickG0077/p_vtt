package com.petrick.vtt.network.client;

import com.petrick.vtt.VTT;
import com.petrick.vtt.core.session.VTTSession;
import com.petrick.vtt.feature.asset.library.AssetLibraryEntry;
import com.petrick.vtt.feature.media.VttAudioPlayerService;
import com.petrick.vtt.feature.media.VttMusicPreferences;
import com.petrick.vtt.network.payload.VttMusicCommandPayload;
import com.petrick.vtt.network.payload.VttMusicUpdatePayload;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Path;

/** Process-wide music player and client/server transport bridge. */
public final class VttClientMusicSync {
    private static final VttAudioPlayerService PLAYER = createPlayer();

    private VttClientMusicSync() {}

    public static VttAudioPlayerService player() { return PLAYER; }

    private static VttAudioPlayerService createPlayer() {
        VttAudioPlayerService player = new VttAudioPlayerService();
        player.setMasterVolume(VttMusicPreferences.loadMasterVolume());
        return player;
    }

    public static void persistVolume() {
        VttMusicPreferences.saveMasterVolume(PLAYER.masterVolume());
    }

    public static void reset() { PLAYER.stop(); }

    public static void play(VTTSession session, String relativePath, Path localPath) {
        if (session.isNetworkAuthorityActive()) {
            if (!session.isLocalMaster()) return;
            send(VttMusicCommandPayload.PLAY, relativePath, 0.0,
                    PLAYER.isLoop(), PLAYER.trackVolume(), PLAYER.masterVolume());
        } else {
            PLAYER.transitionTo(localPath, 0.0, false);
        }
    }

    public static void togglePause(VTTSession session) {
        if (session.isNetworkAuthorityActive()) {
            if (session.isLocalMaster()) send(VttMusicCommandPayload.PAUSE, path(session),
                    PLAYER.positionSeconds(), PLAYER.isLoop(), PLAYER.trackVolume(),
                    PLAYER.masterVolume());
        } else PLAYER.togglePause();
    }

    public static void stop(VTTSession session) {
        if (session.isNetworkAuthorityActive()) {
            if (session.isLocalMaster()) send(VttMusicCommandPayload.STOP, path(session),
                    0.0, PLAYER.isLoop(), PLAYER.trackVolume(), PLAYER.masterVolume());
        } else PLAYER.fadeOutAndStop();
    }

    public static void seek(VTTSession session, double ratio) {
        double position = Math.max(0.0, Math.min(1.0, ratio)) * PLAYER.durationSeconds();
        if (session.isNetworkAuthorityActive()) {
            if (session.isLocalMaster()) send(VttMusicCommandPayload.SEEK, path(session),
                    position, PLAYER.isLoop(), PLAYER.trackVolume(), PLAYER.masterVolume());
        } else PLAYER.seek(ratio);
    }

    public static void toggleLoop(VTTSession session) {
        boolean loop = !PLAYER.isLoop();
        if (session.isNetworkAuthorityActive()) {
            if (session.isLocalMaster()) send(VttMusicCommandPayload.LOOP, path(session),
                    PLAYER.positionSeconds(), loop, PLAYER.trackVolume(), PLAYER.masterVolume());
        } else PLAYER.setLoop(loop);
    }

    public static void setMasterVolume(VTTSession session, float value) {
        PLAYER.setMasterVolume(value);
        if (session.isNetworkAuthorityActive() && session.isLocalMaster()) {
            send(VttMusicCommandPayload.VOLUME, path(session), PLAYER.positionSeconds(),
                    PLAYER.isLoop(), PLAYER.trackVolume(), PLAYER.masterVolume());
        }
    }

    public static void previewMasterVolume(float value) {
        PLAYER.setMasterVolume(value);
    }

    public static void commitMasterVolume(VTTSession session) {
        setMasterVolume(session, PLAYER.masterVolume());
        persistVolume();
    }

    public static void accept(VttMusicUpdatePayload update) {
        if (update == null) return;
        PLAYER.setLoop(update.loop());
        PLAYER.setTrackVolume(update.trackVolume());
        PLAYER.setMasterVolume(update.masterVolume());
        if (!update.playing()) {
            PLAYER.fadeOutAndStop();
            return;
        }
        Path file = resolve(update.relativePath());
        if (file == null) {
            VTT.LOGGER.warn("Synchronized VTT music is missing from the client cache: {}",
                    update.relativePath());
            return;
        }
        double synchronizedPosition = update.positionSeconds();
        if (!update.paused()) {
            synchronizedPosition += Math.max(0L,
                    System.currentTimeMillis() - update.serverEpochMillis()) / 1000.0;
        }
        if (VttMusicCommandPayload.PLAY.equals(update.operation())
                || "STATE".equals(update.operation())
                || PLAYER.track() == null || !PLAYER.track().equals(file)) {
            PLAYER.transitionTo(file, synchronizedPosition, update.paused());
        } else if (VttMusicCommandPayload.PAUSE.equals(update.operation())) {
            if (PLAYER.isPaused() != update.paused()) PLAYER.togglePause();
        } else if (VttMusicCommandPayload.SEEK.equals(update.operation())) {
            double ratio = PLAYER.durationSeconds() <= 0.0 ? 0.0
                    : synchronizedPosition / PLAYER.durationSeconds();
            PLAYER.seek(ratio);
        }
    }

    private static Path resolve(String relativePath) {
        VTTSession session = VTT.getApplication().getActiveSession();
        if (session.getAssetLibraryScanResult() == null) return null;
        return session.getAssetLibraryScanResult().entries().stream()
                .filter(entry -> entry.relativePath().replace('\\', '/')
                        .equals(relativePath))
                .map(AssetLibraryEntry::absolutePath).findFirst().orElse(null);
    }

    private static String path(VTTSession session) {
        Path track = PLAYER.track();
        if (track == null || session.getAssetLibraryScanResult() == null) return "";
        return session.getAssetLibraryScanResult().entries().stream()
                .filter(entry -> entry.absolutePath().equals(track))
                .map(AssetLibraryEntry::relativePath).findFirst().orElse("");
    }

    private static void send(String operation, String path, double position,
                             boolean loop, float trackVolume, float masterVolume) {
        PacketDistributor.sendToServer(new VttMusicCommandPayload(operation,
                path == null ? "" : path, position, loop, trackVolume, masterVolume));
    }
}
