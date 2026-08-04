package com.petrick.vtt.network.server;

import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttShowCommandPayload;
import com.petrick.vtt.network.payload.VttShowUpdatePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Locale;

/** Authoritative state and asset delivery for fullscreen image shows. */
public final class VttServerShowHandler {
    private static String path = "";
    private static boolean active;
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
        if (VttShowCommandPayload.SHOW.equals(command.operation())) {
            String selected = validPath(command.relativePath());
            if (selected == null) {
                VttServerFeedback.show(requester, "Invalid VTT show image");
                return;
            }
            synchronized (VttServerShowHandler.class) {
                path = selected;
                active = true;
                changedAtMillis = System.currentTimeMillis();
            }
        } else if (VttShowCommandPayload.CLOSE.equals(command.operation())) {
            synchronized (VttServerShowHandler.class) {
                active = false;
                changedAtMillis = System.currentTimeMillis();
            }
        } else return;
        broadcast(requester.getServer());
        VTT.LOGGER.info("VTT fullscreen show {} by {}: {}", command.operation(),
                requester.getGameProfile().getName(), path);
    }

    public static void sendCurrent(ServerPlayer player) {
        if (player == null) return;
        VttShowUpdatePayload update = current();
        if (update.active()) {
            VttServerAssetSyncService.sendLibraryAsset(player, update.relativePath(),
                    () -> PacketDistributor.sendToPlayer(player, update));
        } else PacketDistributor.sendToPlayer(player, update);
    }

    private static void broadcast(MinecraftServer server) {
        if (server == null) return;
        VttShowUpdatePayload update = current();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (update.active()) {
                VttServerAssetSyncService.sendLibraryAsset(player, update.relativePath(),
                        () -> PacketDistributor.sendToPlayer(player, update));
            } else PacketDistributor.sendToPlayer(player, update);
        }
    }

    private static synchronized VttShowUpdatePayload current() {
        return new VttShowUpdatePayload(path, active, changedAtMillis);
    }

    private static String validPath(String value) {
        if (value == null) return null;
        String normalized = value.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("shows/") || normalized.contains("../")) return null;
        if (!(lower.endsWith(".png") || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg") || lower.endsWith(".gif")
                || lower.endsWith(".webp"))) return null;
        return normalized;
    }
}
