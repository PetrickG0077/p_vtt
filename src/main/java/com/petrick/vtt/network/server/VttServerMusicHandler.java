package com.petrick.vtt.network.server;

import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttMusicCommandPayload;
import com.petrick.vtt.network.payload.VttMusicUpdatePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Locale;

/** Validates master music commands and retains the current transport state. */
public final class VttServerMusicHandler {
    private static MusicState state = MusicState.stopped();
    private static long positionAnchorMillis = System.currentTimeMillis();

    private VttServerMusicHandler() {}

    public static void handle(VttMusicCommandPayload command, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer requester) || command == null) return;
        if (!VttServerRequestRateLimiter.allow(
                requester, VttServerRequestRateLimiter.Category.PRESENTATION)) return;
        if (!VttServerPlayerEvents.isMaster(requester)) {
            VttServerFeedback.show(requester, "Only a master can control VTT music");
            return;
        }

        VttMusicUpdatePayload update;
        synchronized (VttServerMusicHandler.class) {
            String operation = command.operation();
            if (VttMusicCommandPayload.PLAY.equals(operation)) {
                String path = validMusicPath(command.relativePath());
                if (path == null) {
                    VttServerFeedback.show(requester, "Invalid VTT music path");
                    return;
                }
                state = new MusicState(path, Math.max(0.0, command.positionSeconds()),
                        true, false, command.loop(), clamp(command.trackVolume()),
                        clamp(command.masterVolume()));
                positionAnchorMillis = System.currentTimeMillis();
            } else if (VttMusicCommandPayload.PAUSE.equals(operation)) {
                state = new MusicState(state.path,
                        Math.max(0.0, command.positionSeconds()), state.playing,
                        state.playing && !state.paused, state.loop,
                        state.trackVolume, state.masterVolume);
                positionAnchorMillis = System.currentTimeMillis();
            } else if (VttMusicCommandPayload.STOP.equals(operation)) {
                state = new MusicState(state.path, 0.0, false, false, state.loop,
                        state.trackVolume, state.masterVolume);
                positionAnchorMillis = System.currentTimeMillis();
            } else if (VttMusicCommandPayload.SEEK.equals(operation)) {
                state = state.withPosition(Math.max(0.0, command.positionSeconds()));
                positionAnchorMillis = System.currentTimeMillis();
            } else if (VttMusicCommandPayload.LOOP.equals(operation)) {
                state = state.withLoop(command.loop());
            } else if (VttMusicCommandPayload.VOLUME.equals(operation)) {
                state = state.withVolumes(clamp(command.trackVolume()),
                        clamp(command.masterVolume()));
            } else {
                VttServerFeedback.show(requester, "Unknown VTT music command");
                return;
            }
            update = update(operation);
        }
        broadcast(requester.getServer(), update);
        VTT.LOGGER.info("VTT music {} by {}: {}", command.operation(),
                requester.getGameProfile().getName(), state.path);
    }

    public static void sendCurrent(ServerPlayer player) {
        sendCurrent(player, null);
    }

    public static void sendCurrent(ServerPlayer player, Runnable completion) {
        if (player == null) return;
        VttMusicUpdatePayload update;
        synchronized (VttServerMusicHandler.class) {
            update = update("STATE");
        }
        if (update.playing() && !update.relativePath().isBlank()) {
            VttServerAssetSyncService.sendLibraryAsset(player, update.relativePath(),
                    () -> {
                        PacketDistributor.sendToPlayer(player, update);
                        if (completion != null) completion.run();
                    });
        } else {
            PacketDistributor.sendToPlayer(player, update);
            if (completion != null) completion.run();
        }
    }

    private static void broadcast(MinecraftServer server, VttMusicUpdatePayload update) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if ((VttMusicCommandPayload.PLAY.equals(update.operation())
                    || "STATE".equals(update.operation()))
                    && update.playing() && !update.relativePath().isBlank()) {
                VttServerAssetSyncService.sendLibraryAsset(player, update.relativePath(),
                        () -> PacketDistributor.sendToPlayer(player, update));
            } else {
                PacketDistributor.sendToPlayer(player, update);
            }
        }
    }

    private static VttMusicUpdatePayload update(String operation) {
        long now = System.currentTimeMillis();
        double position = state.position;
        if (state.playing && !state.paused) {
            position += Math.max(0L, now - positionAnchorMillis) / 1000.0;
        }
        return new VttMusicUpdatePayload(operation, state.path, position,
                state.playing, state.paused, state.loop,
                state.trackVolume, state.masterVolume, now);
    }

    private static String validMusicPath(String value) {
        if (value == null) return null;
        String path = value.replace('\\', '/');
        while (path.startsWith("/")) path = path.substring(1);
        String lower = path.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("musics/") || path.contains("../")
                || !(lower.endsWith(".mp3") || lower.endsWith(".ogg"))) return null;
        return path;
    }

    private static float clamp(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 1.0F;
    }

    private record MusicState(String path, double position, boolean playing,
                              boolean paused, boolean loop,
                              float trackVolume, float masterVolume) {
        private static MusicState stopped() {
            return new MusicState("", 0.0, false, false, false, 1.0F, 1.0F);
        }
        private MusicState withPaused(boolean value) {
            return new MusicState(path, position, playing, value, loop,
                    trackVolume, masterVolume);
        }
        private MusicState withPosition(double value) {
            return new MusicState(path, value, playing, paused, loop,
                    trackVolume, masterVolume);
        }
        private MusicState withLoop(boolean value) {
            return new MusicState(path, position, playing, paused, value,
                    trackVolume, masterVolume);
        }
        private MusicState withVolumes(float track, float master) {
            return new MusicState(path, position, playing, paused, loop, track, master);
        }
    }
}
