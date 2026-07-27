package com.petrick.vtt.network.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petrick.vtt.VTT;
import com.petrick.vtt.core.session.VttPlayerRosterEntry;
import com.petrick.vtt.core.session.VttRole;
import com.petrick.vtt.network.payload.VttIdentityPayload;
import com.petrick.vtt.network.payload.VttPlayerRosterPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;

@EventBusSubscriber(modid = VTT.MOD_ID)
public final class VttServerPlayerEvents {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<UUID, VttRole> LAST_ROLES = new HashMap<>();

    private VttServerPlayerEvents() {
    }

    public static boolean isMaster(ServerPlayer player) {
        return player != null && player.hasPermissions(2);
    }

    public static boolean isSpectator(ServerPlayer player) {
        return player != null && !isMaster(player)
                && VttServerTabletopState.get().isPlayerSpectator(player.getUUID());
    }

    public static boolean canViewFullTabletop(ServerPlayer player) {
        return isMaster(player) || isSpectator(player);
    }

    public static boolean setSpectator(ServerPlayer player, boolean spectator) {
        if (player == null || spectator && isMaster(player)) return false;
        return VttServerTabletopState.get().setPlayerSpectator(
                player.getUUID(), spectator);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        sendIdentity(player);
        broadcastRoster(player.getServer());
        VttServerTabletopState state = VttServerTabletopState.get();
        VttServerVisionSourceSync.markCurrentAssetsSent(player, state);
        VttServerAssetSyncService.sendActiveSceneAssets(
                player, state.replicatedSceneFor(player), isMaster(player), () -> {
                    VttServerSceneSnapshotSync.sendToPlayer(player, state);
                    VttServerVisionSourceSync.sendToPlayer(player, state);
                });
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        VttRole current = isMaster(player) ? VttRole.MASTER : VttRole.PLAYER;
        VttServerTabletopState state = VttServerTabletopState.get();
        if (LAST_ROLES.get(player.getUUID()) != current) {
            if (current == VttRole.MASTER) setSpectator(player, false);
            sendIdentity(player);
            broadcastRoster(player.getServer());
            VttServerVisionSourceSync.markCurrentAssetsSent(player, state);
            VttServerAssetSyncService.sendActiveSceneAssets(
                    player, state.replicatedSceneFor(player), isMaster(player), () -> {
                        VttServerSceneSnapshotSync.sendToPlayer(player, state);
                        VttServerVisionSourceSync.sendToPlayer(player, state);
                    });
            return;
        }
        VttServerVisionSourceSync.heartbeat(player, state);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (isMaster(player)) {
                VttServerPresentationHandler.stopCameraFollow(
                        player.getServer(), player.getUUID());
            }
            broadcastRoster(player.getServer(), player.getUUID());
        }
        LAST_ROLES.remove(event.getEntity().getUUID());
        VttServerVisionSourceSync.forget(event.getEntity().getUUID());
        VttServerAssetSyncService.forget(event.getEntity().getUUID());
        VttServerRequestRateLimiter.forget(event.getEntity().getUUID());
    }

    public static VttRole sendIdentity(ServerPlayer player) {
        VttRole role = isMaster(player) ? VttRole.MASTER : VttRole.PLAYER;
        LAST_ROLES.put(player.getUUID(), role);
        VTT.LOGGER.info("Assigned VTT {} role to {} ({}) based on server permissions; spectator={}",
                role, player.getGameProfile().getName(), player.getUUID(), isSpectator(player));
        PacketDistributor.sendToPlayer(player,
                new VttIdentityPayload(
                        player.getUUID().toString(), role.name(), isSpectator(player)));
        PacketDistributor.sendToPlayer(player,
                VttServerPresentationHandler.currentPresentation(
                        VttServerTabletopState.get()));
        return role;
    }

    public static void broadcastRoster(MinecraftServer server) {
        broadcastRoster(server, null);
    }

    private static void broadcastRoster(MinecraftServer server, UUID excludedPlayerId) {
        if (server == null) return;
        List<VttPlayerRosterEntry> roster = server.getPlayerList().getPlayers().stream()
                .filter(player -> excludedPlayerId == null
                        || !excludedPlayerId.equals(player.getUUID()))
                .map(player -> new VttPlayerRosterEntry(
                        player.getUUID().toString(),
                        player.getGameProfile().getName(),
                        isMaster(player) ? VttRole.MASTER : VttRole.PLAYER,
                        isSpectator(player)))
                .toList();
        VttPlayerRosterPayload payload = new VttPlayerRosterPayload(GSON.toJson(roster));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (excludedPlayerId == null || !excludedPlayerId.equals(player.getUUID())) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
