package com.petrick.vtt.network.server;

import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttShowCommandPayload;
import com.petrick.vtt.network.payload.VttShowUpdatePayload;
import com.petrick.vtt.network.payload.VttShowPreloadPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Locale;
import java.nio.file.Files;
import java.nio.file.Path;

/** Authoritative state and asset delivery for fullscreen image shows. */
public final class VttServerShowHandler {
    private static String path = "";
    private static boolean active;
    private static boolean playing;
    private static boolean loop;
    private static long playbackPositionMillis;
    private static long playbackStartedAtMillis;
    private static long changedAtMillis;

    private VttServerShowHandler() {}

    public static void handle(VttShowCommandPayload command, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer requester) || command == null) return;
        if (!VttServerRequestRateLimiter.allow(
                requester, VttServerRequestRateLimiter.Category.PRESENTATION)) return;
        if (!VttServerPlayerEvents.isMaster(requester)) {
            VttServerFeedback.show(requester, "Only a master can control VTT shows");
            return;
        }
        if (VttShowCommandPayload.PRELOAD.equals(command.operation())) {
            String selected = validPath(command.relativePath());
            if (selected == null || !isVideo(selected)) {
                VttServerFeedback.show(requester, "Video is missing or unsupported on this server");
                return;
            }
            MinecraftServer server = requester.getServer();
            if (server != null) {
                VttShowPreloadPayload preload = new VttShowPreloadPayload(selected);
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    PacketDistributor.sendToPlayer(player, preload);
                }
            }
            VTT.LOGGER.info("VTT show preload requested by {}: {}",
                    requester.getGameProfile().getName(), selected);
            return;
        }
        if (VttShowCommandPayload.SHOW.equals(command.operation())) {
            String selected = validPath(command.relativePath());
            if (selected == null) {
                VttServerFeedback.show(requester,
                        "Show file is missing or unsupported on this server");
                return;
            }
            synchronized (VttServerShowHandler.class) {
                path = selected;
                active = true;
                playing = false;
                loop = false;
                playbackPositionMillis = 0L;
                playbackStartedAtMillis = 0L;
                changedAtMillis = System.currentTimeMillis();
            }
        } else if (VttShowCommandPayload.CLOSE.equals(command.operation())) {
            synchronized (VttServerShowHandler.class) {
                active = false;
                playing = false;
                loop = false;
                playbackPositionMillis = 0L;
                playbackStartedAtMillis = 0L;
                changedAtMillis = System.currentTimeMillis();
            }
        } else if (VttShowCommandPayload.PLAY.equals(command.operation()) && isVideo(path)) {
            synchronized (VttServerShowHandler.class) {
                playing = true;
                playbackStartedAtMillis = System.currentTimeMillis();
                changedAtMillis = System.currentTimeMillis();
            }
        } else if (VttShowCommandPayload.PAUSE.equals(command.operation()) && isVideo(path)) {
            synchronized (VttServerShowHandler.class) {
                playbackPositionMillis = currentPlaybackPosition();
                playing = false;
                playbackStartedAtMillis = 0L;
                changedAtMillis = System.currentTimeMillis();
            }
        } else if (VttShowCommandPayload.SEEK.equals(command.operation()) && isVideo(path)) {
            synchronized (VttServerShowHandler.class) {
                playbackPositionMillis = Math.max(0L,
                        Math.min(86_400_000L, command.positionMillis()));
                playbackStartedAtMillis = playing ? System.currentTimeMillis() : 0L;
                changedAtMillis = System.currentTimeMillis();
            }
        } else if (VttShowCommandPayload.RESET.equals(command.operation()) && isVideo(path)) {
            synchronized (VttServerShowHandler.class) {
                playing = false;
                playbackPositionMillis = 0L;
                playbackStartedAtMillis = 0L;
                changedAtMillis = System.currentTimeMillis();
            }
        } else if (VttShowCommandPayload.TOGGLE_LOOP.equals(command.operation())
                && isVideo(path)) {
            synchronized (VttServerShowHandler.class) {
                loop = !loop;
                changedAtMillis = System.currentTimeMillis();
            }
        } else if (VttShowCommandPayload.END.equals(command.operation()) && isVideo(path)) {
            synchronized (VttServerShowHandler.class) {
                playbackPositionMillis = Math.max(0L,
                        Math.min(86_400_000L, command.positionMillis()));
                playing = false;
                playbackStartedAtMillis = 0L;
                changedAtMillis = System.currentTimeMillis();
            }
        } else if (VttShowCommandPayload.RESTART.equals(command.operation()) && isVideo(path)) {
            synchronized (VttServerShowHandler.class) {
                playbackPositionMillis = 0L;
                playing = true;
                playbackStartedAtMillis = System.currentTimeMillis();
                changedAtMillis = System.currentTimeMillis();
            }
        } else return;
        broadcast(requester.getServer());
        VTT.LOGGER.info("VTT fullscreen show {} by {}: {}", command.operation(),
                requester.getGameProfile().getName(), path);
    }

    public static void sendCurrent(ServerPlayer player) {
        if (player == null) return;
        PacketDistributor.sendToPlayer(player, current());
    }

    private static void broadcast(MinecraftServer server) {
        if (server == null) return;
        VttShowUpdatePayload update = current();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, update);
        }
    }

    private static synchronized VttShowUpdatePayload current() {
        return new VttShowUpdatePayload(path, active, playing, loop,
                currentPlaybackPosition(), changedAtMillis);
    }

    private static long currentPlaybackPosition() {
        return playing ? playbackPositionMillis + Math.max(0L,
                System.currentTimeMillis() - playbackStartedAtMillis) : playbackPositionMillis;
    }

    private static String validPath(String value) {
        if (value == null) return null;
        String normalized = value.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("shows/") || normalized.contains("../")) return null;
        if (!(lower.endsWith(".png") || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg") || lower.endsWith(".gif")
                || lower.endsWith(".webp") || lower.endsWith(".mp4"))) return null;
        Path assetsRoot = FMLPaths.GAMEDIR.get().resolve("config/vtt_assets/assets")
                .toAbsolutePath().normalize();
        Path file = assetsRoot.resolve(normalized).toAbsolutePath().normalize();
        if (!file.startsWith(assetsRoot) || !Files.isRegularFile(file)) return null;
        return normalized;
    }

    private static boolean isVideo(String candidate) {
        return candidate != null && candidate.toLowerCase(Locale.ROOT).endsWith(".mp4");
    }
}
