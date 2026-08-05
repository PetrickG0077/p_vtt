package com.petrick.vtt.network.server;

import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttShowCommandPayload;
import com.petrick.vtt.network.payload.VttShowUpdatePayload;
import com.petrick.vtt.network.payload.VttShowPreloadPayload;
import com.petrick.vtt.network.payload.VttShowPreloadProgressPayload;
import com.petrick.vtt.network.payload.VttShowPreloadStatusPayload;
import com.google.gson.Gson;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Locale;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Authoritative state and asset delivery for fullscreen image shows. */
public final class VttServerShowHandler {
    private static final Gson GSON = new Gson();
    private static String path = "";
    private static boolean active;
    private static boolean playing;
    private static boolean loop;
    private static long playbackPositionMillis;
    private static long playbackStartedAtMillis;
    private static long changedAtMillis;
    private static String preparedPath = "";
    private static final Map<UUID, PreloadStatus> PRELOAD_STATUSES = new LinkedHashMap<>();

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
                synchronized (VttServerShowHandler.class) {
                    preparedPath = selected;
                    PRELOAD_STATUSES.clear();
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        PRELOAD_STATUSES.put(player.getUUID(), new PreloadStatus(
                                player.getUUID().toString(),
                                player.getGameProfile().getName(), "WAITING", 0.0F));
                    }
                }
                VttShowPreloadPayload preload = new VttShowPreloadPayload(selected);
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    PacketDistributor.sendToPlayer(player, preload);
                }
                broadcastPreloadStatus(server);
            }
            VTT.LOGGER.info("VTT show preload requested by {}: {}",
                    requester.getGameProfile().getName(), selected);
            return;
        }
        if (VttShowCommandPayload.SHOW.equals(command.operation())
                || VttShowCommandPayload.FORCE_SHOW.equals(command.operation())) {
            String selected = validPath(command.relativePath());
            if (selected == null) {
                VttServerFeedback.show(requester,
                        "Show file is missing or unsupported on this server");
                return;
            }
            if (VttShowCommandPayload.SHOW.equals(command.operation())
                    && isVideo(selected) && !allPlayersReady(selected, requester.getServer())) {
                VttServerFeedback.show(requester,
                        "Not all players finished preloading; use Force Show to continue");
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
        synchronized (VttServerShowHandler.class) {
            if (!preparedPath.isBlank()) {
                PRELOAD_STATUSES.put(player.getUUID(), new PreloadStatus(
                        player.getUUID().toString(), player.getGameProfile().getName(),
                        "WAITING", 0.0F));
                PacketDistributor.sendToPlayer(player,
                        new VttShowPreloadPayload(preparedPath));
                broadcastPreloadStatus(player.getServer());
            }
        }
    }

    public static void handlePreloadProgress(
            VttShowPreloadProgressPayload payload, IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player) || payload == null) return;
        String status = switch (payload.status()) {
            case "WAITING", "LOADING", "READY", "FAILED" -> payload.status();
            default -> "FAILED";
        };
        synchronized (VttServerShowHandler.class) {
            if (preparedPath.isBlank() || !preparedPath.equals(payload.relativePath())) return;
            float progress = Float.isFinite(payload.progress())
                    ? Math.max(0.0F, Math.min(1.0F, payload.progress())) : 0.0F;
            PRELOAD_STATUSES.put(player.getUUID(), new PreloadStatus(
                    player.getUUID().toString(), player.getGameProfile().getName(),
                    status, progress));
        }
        broadcastPreloadStatus(player.getServer());
    }

    private static void broadcast(MinecraftServer server) {
        if (server == null) return;
        VttShowUpdatePayload update = current();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, update);
        }
    }

    private static synchronized void broadcastPreloadStatus(MinecraftServer server) {
        if (server == null) return;
        PRELOAD_STATUSES.keySet().removeIf(
                playerId -> server.getPlayerList().getPlayer(playerId) == null);
        VttShowPreloadStatusPayload payload = new VttShowPreloadStatusPayload(
                preparedPath, GSON.toJson(PRELOAD_STATUSES.values()));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static synchronized boolean allPlayersReady(
            String selected, MinecraftServer server
    ) {
        if (server == null || !selected.equals(preparedPath)) return false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PreloadStatus status = PRELOAD_STATUSES.get(player.getUUID());
            if (status == null || !"READY".equals(status.status())) return false;
        }
        return true;
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

    private record PreloadStatus(
            String playerId, String playerName, String status, float progress
    ) {}
}
