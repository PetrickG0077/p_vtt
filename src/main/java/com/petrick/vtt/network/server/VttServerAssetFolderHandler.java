package com.petrick.vtt.network.server;

import com.petrick.vtt.VTT;
import com.petrick.vtt.network.payload.VttAssetFolderCommandPayload;
import com.petrick.vtt.network.payload.VttAssetFolderResultPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Applies Asset Manager folder operations on the authoritative server filesystem. */
public final class VttServerAssetFolderHandler {
    private VttServerAssetFolderHandler() {
    }

    public static void handle(
            VttAssetFolderCommandPayload request,
            IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer requester)
                || request == null) return;
        VttServerTabletopState state = VttServerTabletopState.get();
        String requestId = request.requestId() == null ? "" : request.requestId();
        if (!VttServerPlayerEvents.isMaster(requester)) {
            respond(requester, requestId, false,
                    VttAssetFolderResultPayload.PERMISSION_DENIED,
                    "Only masters can update Asset Manager folders", state.authorityRevision());
            return;
        }
        if (requestId.isBlank() || requestId.length() > 64
                || request.operation() == null
                || request.section() == null
                || request.source() == null
                || request.value() == null
                || request.source().length() > 32767
                || request.value().length() > 256) {
            respond(requester, requestId, false,
                    VttAssetFolderResultPayload.INVALID_REQUEST,
                    "Invalid Asset Manager request", state.authorityRevision());
            return;
        }

        if (request.authorityRevision() != state.authorityRevision()) {
            respond(requester, requestId, false,
                    VttAssetFolderResultPayload.STALE_REVISION,
                    "Asset Manager changed on the server; resynchronizing",
                    state.authorityRevision());
            return;
        }
        if (!state.applyAssetFolderCommand(request)) {
            respond(requester, requestId, false,
                    VttAssetFolderResultPayload.REJECTED,
                    failureMessage(request.operation()), state.authorityRevision());
            return;
        }

        respond(requester, requestId, true, VttAssetFolderResultPayload.OK,
                successMessage(request.operation()), state.authorityRevision());

        MinecraftServer server = requester.getServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            VttServerVisionSourceSync.markCurrentAssetsSent(player, state);
            VttServerAssetSyncService.sendActiveSceneAssets(
                    player,
                    state.replicatedSceneFor(player),
                    VttServerPlayerEvents.isMaster(player),
                    () -> {
                        VttServerSceneSnapshotSync.sendToPlayer(player, state);
                        VttServerVisionSourceSync.sendToPlayer(player, state);
                    });
        }
        VTT.LOGGER.info("Applied VTT Asset Manager folder command {} from {}",
                request.operation(), requester.getGameProfile().getName());
    }

    private static void respond(
            ServerPlayer player, String requestId, boolean success,
            String code, String message, long authorityRevision
    ) {
        PacketDistributor.sendToPlayer(player, new VttAssetFolderResultPayload(
                requestId == null ? "" : requestId, success, code, message,
                authorityRevision));
    }

    private static String successMessage(String operation) {
        return switch (operation) {
            case VttAssetFolderCommandPayload.CREATE_FOLDER -> "Folder created";
            case VttAssetFolderCommandPayload.REFRESH -> "Asset folders refreshed";
            case VttAssetFolderCommandPayload.RENAME_FOLDER -> "Folder renamed";
            case VttAssetFolderCommandPayload.DUPLICATE_FOLDER -> "Folder duplicated";
            case VttAssetFolderCommandPayload.MOVE_FOLDER -> "Folder moved";
            case VttAssetFolderCommandPayload.MOVE_ITEM -> "Asset moved";
            case VttAssetFolderCommandPayload.MOVE_SELECTION -> "Assets moved";
            case VttAssetFolderCommandPayload.DELETE_SELECTION -> "Selection deleted";
            case VttAssetFolderCommandPayload.DELETE_FOLDER -> "Folder deleted";
            case VttAssetFolderCommandPayload.MOVE_CONTENTS_AND_DELETE_FOLDER ->
                    "Folder contents moved and folder deleted";
            default -> "Asset folders updated";
        };
    }

    private static String failureMessage(String operation) {
        if (VttAssetFolderCommandPayload.DELETE_FOLDER.equals(operation)) {
            return "Folder must be empty before it can be deleted";
        }
        return "The server could not update the Asset Manager";
    }
}
