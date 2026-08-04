package com.petrick.vtt.network.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petrick.vtt.VTT;
import com.petrick.vtt.feature.attachment.persistence.CreatedAttachmentSaveData;
import com.petrick.vtt.network.payload.VttAssetManagerChangePayload;
import com.petrick.vtt.network.payload.VttAttachmentDefinitionCommandPayload;
import com.petrick.vtt.network.payload.VttAttachmentDefinitionResultPayload;
import com.petrick.vtt.network.payload.VttAttachmentDefinitionUpsertPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

/** Authoritative persistence and lifecycle handling for attachment definitions. */
public final class VttServerAttachmentDefinitionHandler {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private VttServerAttachmentDefinitionHandler() {}

    public static void handleUpsert(
            VttAttachmentDefinitionUpsertPayload payload, IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer requester) || payload == null) return;
        VttServerTabletopState state = VttServerTabletopState.get();
        if (!authorize(requester, payload.requestId(), state)) return;
        if (!validRequestId(payload.requestId()) || payload.attachmentJson() == null
                || payload.folder() == null) {
            respond(requester, payload.requestId(), false,
                    VttAttachmentDefinitionResultPayload.INVALID_REQUEST,
                    "Invalid attachment definition request", state.authorityRevision(), "");
            return;
        }
        if (payload.authorityRevision() != state.authorityRevision()) {
            respond(requester, payload.requestId(), false,
                    VttAttachmentDefinitionResultPayload.STALE_REVISION,
                    "Attachment catalog changed on the server; resynchronizing",
                    state.authorityRevision(), "");
            return;
        }
        try {
            CreatedAttachmentSaveData data = GSON.fromJson(
                    payload.attachmentJson(), CreatedAttachmentSaveData.class);
            validate(data);
            Path previousFile = findFile(data.attachmentDefinitionId());
            Path folder = previousFile == null
                    ? safeFolder(payload.folder()) : previousFile.getParent();
            Path savedFile = save(data, folder);
            if (previousFile != null && !previousFile.equals(savedFile)) {
                Files.deleteIfExists(previousFile);
            }
            state.markAssetCatalogChanged();
            String message = previousFile == null ? "Attachment created" : "Attachment updated";
            respond(requester, payload.requestId(), true,
                    VttAttachmentDefinitionResultPayload.OK, message,
                    state.authorityRevision(), data.attachmentDefinitionId());
            broadcastReload(requester, state, "UPSERT", message);
            VTT.LOGGER.info("Saved server VTT attachment definition: {}",
                    data.attachmentDefinitionId());
        } catch (RuntimeException | IOException exception) {
            respond(requester, payload.requestId(), false,
                    VttAttachmentDefinitionResultPayload.REJECTED,
                    failureMessage(exception), state.authorityRevision(), "");
            VTT.LOGGER.error("Rejected invalid VTT attachment definition from {}",
                    requester.getGameProfile().getName(), exception);
        }
    }

    public static void handleCommand(
            VttAttachmentDefinitionCommandPayload payload, IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer requester) || payload == null) return;
        VttServerTabletopState state = VttServerTabletopState.get();
        if (!authorize(requester, payload.requestId(), state)) return;
        if (!validRequestId(payload.requestId())
                || payload.authorityRevision() != state.authorityRevision()) {
            boolean stale = payload.authorityRevision() != state.authorityRevision();
            respond(requester, payload.requestId(), false,
                    stale ? VttAttachmentDefinitionResultPayload.STALE_REVISION
                            : VttAttachmentDefinitionResultPayload.INVALID_REQUEST,
                    stale ? "Attachment catalog changed on the server; resynchronizing"
                            : "Invalid attachment request",
                    state.authorityRevision(), "");
            return;
        }
        if (payload.operation() == null || payload.definitionId() == null
                || payload.definitionId().length() > 256
                || !payload.definitionId().startsWith("user/attachments/")) {
            respond(requester, payload.requestId(), false,
                    VttAttachmentDefinitionResultPayload.INVALID_REQUEST,
                    "Invalid attachment definition command", state.authorityRevision(), "");
            return;
        }
        try {
            String message;
            String resultingId = payload.definitionId();
            if (VttAttachmentDefinitionCommandPayload.DUPLICATE.equals(payload.operation())) {
                resultingId = duplicate(payload.definitionId());
                if (resultingId == null) throw new IllegalArgumentException(
                        "Could not duplicate the attachment");
                message = "Attachment duplicated";
            } else if (VttAttachmentDefinitionCommandPayload.DELETE.equals(payload.operation())) {
                if (state.isAttachmentDefinitionInUse(payload.definitionId())) {
                    throw new IllegalArgumentException(
                            "Remove placed instances before deleting this attachment");
                }
                Path file = findFile(payload.definitionId());
                if (file == null || !Files.deleteIfExists(file)) {
                    throw new IllegalArgumentException("Could not delete the attachment");
                }
                resultingId = "";
                message = "Attachment deleted";
            } else {
                respond(requester, payload.requestId(), false,
                        VttAttachmentDefinitionResultPayload.INVALID_REQUEST,
                        "Unknown attachment definition command",
                        state.authorityRevision(), "");
                return;
            }
            state.markAssetCatalogChanged();
            respond(requester, payload.requestId(), true,
                    VttAttachmentDefinitionResultPayload.OK, message,
                    state.authorityRevision(), resultingId);
            broadcastReload(requester, state, payload.operation(), message);
        } catch (RuntimeException | IOException exception) {
            respond(requester, payload.requestId(), false,
                    VttAttachmentDefinitionResultPayload.REJECTED,
                    failureMessage(exception), state.authorityRevision(), "");
            VTT.LOGGER.error("Failed VTT attachment definition command {} for {}",
                    payload.operation(), payload.definitionId(), exception);
        }
    }

    private static boolean authorize(
            ServerPlayer requester, String requestId, VttServerTabletopState state
    ) {
        if (!VttServerPlayerEvents.isMaster(requester)) {
            respond(requester, requestId, false,
                    VttAttachmentDefinitionResultPayload.PERMISSION_DENIED,
                    "Only masters can update attachment definitions",
                    state.authorityRevision(), "");
            return false;
        }
        if (!VttServerRequestRateLimiter.allow(requester,
                VttServerRequestRateLimiter.Category.ATTACHMENT_DEFINITION)) {
            respond(requester, requestId, false,
                    VttAttachmentDefinitionResultPayload.REJECTED,
                    "Too many attachment operations; try again shortly",
                    state.authorityRevision(), "");
            return false;
        }
        return true;
    }

    private static void validate(CreatedAttachmentSaveData data) {
        if (data == null || data.schemaVersion() < 1
                || data.schemaVersion() > CreatedAttachmentSaveData.CURRENT_SCHEMA_VERSION
                || data.attachmentDefinitionId() == null
                || !data.attachmentDefinitionId().startsWith("user/attachments/")
                || data.attachmentDefinitionId().length() > 256
                || data.displayName() == null || data.displayName().isBlank()
                || data.displayName().length() > 64
                || !Double.isFinite(data.defaultWidth()) || data.defaultWidth() <= 0.0
                || data.defaultWidth() > 16_000.0
                || !Double.isFinite(data.defaultHeight()) || data.defaultHeight() <= 0.0
                || data.defaultHeight() > 16_000.0
                || data.assetId() != null && data.assetId().length() > 512) {
            throw new IllegalArgumentException("Invalid attachment definition metadata");
        }
        if (data.assetId() != null && !data.assetId().isBlank()) resolveAsset(data.assetId());
        if (data.states() != null) {
            if (data.states().size() > 64) throw new IllegalArgumentException("Too many attachment states");
            data.states().values().forEach(state -> {
                if (state == null || state.id().length() > 64
                        || state.displayName().length() > 64) {
                    throw new IllegalArgumentException("Invalid attachment state");
                }
                if (state.assetId() != null) resolveAsset(state.assetId());
            });
        }
        if (data.compositeNodes() != null) {
            if (data.compositeNodes().size() > 64) {
                throw new IllegalArgumentException("Too many composite attachment nodes");
            }
            java.util.Set<String> nodeIds = new java.util.HashSet<>();
            nodeIds.add(com.petrick.vtt.feature.attachment.AttachmentCompositeNode.ROOT_ID);
            data.compositeNodes().forEach(node -> {
                if (node == null || !nodeIds.add(node.templateNodeId())
                        || node.binding() == null || !node.binding().isBound()
                        || node.lights().size() > 32) {
                    throw new IllegalArgumentException("Invalid composite attachment node");
                }
            });
            data.compositeNodes().forEach(node -> {
                if (!nodeIds.contains(node.binding().getTargetObjectId())) {
                    throw new IllegalArgumentException("Invalid composite parent reference");
                }
            });
        }
        if (data.rootLights() != null && data.rootLights().size() > 32) {
            throw new IllegalArgumentException("Too many composite root lights");
        }
    }

    private static Path resolveAsset(String assetId) {
        if (!assetId.startsWith("library:")) {
            throw new IllegalArgumentException("Invalid attachment asset ID");
        }
        String relative = assetId.substring("library:".length()).replace('\\', '/');
        Path root = FMLPaths.GAMEDIR.get().resolve("config/vtt_assets/assets")
                .toAbsolutePath().normalize();
        Path file = root.resolve(relative).toAbsolutePath().normalize();
        if (relative.isBlank() || relative.startsWith("/") || relative.contains("../")
                || !file.startsWith(root) || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Attachment asset is not present on the server");
        }
        String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".png") || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg") || lower.endsWith(".webp")
                || lower.endsWith(".gif") || lower.endsWith(".apng"))) {
            throw new IllegalArgumentException("Attachment must reference an image");
        }
        return file;
    }

    private static String duplicate(String definitionId) throws IOException {
        Path sourceFile = findFile(definitionId);
        CreatedAttachmentSaveData source = read(sourceFile);
        if (source == null) return null;
        validate(source);
        String name = uniqueName(source.displayName() + " Copy");
        CreatedAttachmentSaveData copy = new CreatedAttachmentSaveData(
                CreatedAttachmentSaveData.CURRENT_SCHEMA_VERSION,
                "user/attachments/" + slug(name) + "_"
                        + UUID.randomUUID().toString().substring(0, 8),
                name, source.assetId(), source.defaultWidth(), source.defaultHeight(),
                source.states(), source.defaultStateId(),
                source.compositeNodes(), source.rootLights());
        save(copy, sourceFile.getParent());
        return copy.attachmentDefinitionId();
    }

    private static String uniqueName(String base) throws IOException {
        String candidate = base;
        int suffix = 2;
        while (displayNameExists(candidate)) candidate = base + " " + suffix++;
        return candidate.length() <= 64 ? candidate : candidate.substring(0, 64);
    }

    private static boolean displayNameExists(String name) throws IOException {
        if (!Files.isDirectory(root())) return false;
        try (Stream<Path> files = Files.walk(root())) {
            for (Path file : files.filter(Files::isRegularFile).filter(
                    VttServerAttachmentDefinitionHandler::isJson).toList()) {
                CreatedAttachmentSaveData data = read(file);
                if (data != null && name.equalsIgnoreCase(data.displayName())) return true;
            }
        }
        return false;
    }

    private static Path save(CreatedAttachmentSaveData data, Path folder) throws IOException {
        Files.createDirectories(folder);
        Path target = folder.resolve(fileName(data));
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary)) { GSON.toJson(data, writer); }
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private static CreatedAttachmentSaveData read(Path file) {
        if (file == null || !Files.isRegularFile(file)) return null;
        try (Reader reader = Files.newBufferedReader(file)) {
            return GSON.fromJson(reader, CreatedAttachmentSaveData.class);
        } catch (RuntimeException | IOException exception) { return null; }
    }

    private static Path findFile(String definitionId) throws IOException {
        if (!Files.isDirectory(root())) return null;
        try (Stream<Path> files = Files.walk(root())) {
            for (Path file : files.filter(Files::isRegularFile).filter(
                    VttServerAttachmentDefinitionHandler::isJson).toList()) {
                CreatedAttachmentSaveData data = read(file);
                if (data != null && definitionId.equals(data.attachmentDefinitionId())) return file;
            }
        }
        return null;
    }

    private static Path safeFolder(String relativeFolder) {
        String relative = relativeFolder == null ? "" : relativeFolder.replace('\\', '/');
        Path folder = root().resolve(relative).toAbsolutePath().normalize();
        if (relative.startsWith("/") || relative.contains("../") || !folder.startsWith(root())) {
            throw new IllegalArgumentException("Invalid attachment folder");
        }
        return folder;
    }

    private static Path root() {
        return FMLPaths.GAMEDIR.get().resolve("config/vtt_assets/created/attachments")
                .toAbsolutePath().normalize();
    }

    private static boolean validRequestId(String id) {
        return id != null && !id.isBlank() && id.length() <= 64;
    }

    private static boolean isJson(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    private static String fileName(CreatedAttachmentSaveData data) {
        String suffix = data.attachmentDefinitionId().substring(
                data.attachmentDefinitionId().lastIndexOf('/') + 1);
        return slug(data.displayName()) + "_" + suffix + ".json";
    }

    private static String slug(String value) {
        String result = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return result.isBlank() ? "attachment" : result;
    }

    private static String failureMessage(Exception exception) {
        String message = exception == null ? null : exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Could not update the attachment definition on the server";
        }
        return message.length() <= 256 ? message : message.substring(0, 256);
    }

    private static void respond(
            ServerPlayer player, String requestId, boolean success, String code,
            String message, long revision, String definitionId
    ) {
        PacketDistributor.sendToPlayer(player, new VttAttachmentDefinitionResultPayload(
                requestId == null ? "" : requestId, success, code, message, revision,
                definitionId == null ? "" : definitionId));
    }

    private static void broadcastReload(
            ServerPlayer requester, VttServerTabletopState state,
            String operation, String message
    ) {
        for (ServerPlayer connected : requester.getServer().getPlayerList().getPlayers()) {
            VttServerVisionSourceSync.markCurrentAssetsSent(connected, state);
            VttServerAssetSyncService.sendActiveSceneAssets(
                    connected, state.replicatedSceneFor(connected),
                    VttServerPlayerEvents.isMaster(connected), () -> {
                        VttServerSceneSnapshotSync.sendToPlayer(connected, state);
                        VttServerVisionSourceSync.sendToPlayer(connected, state);
                        if (connected != requester && VttServerPlayerEvents.isMaster(connected)) {
                            PacketDistributor.sendToPlayer(connected,
                                    new VttAssetManagerChangePayload(
                                            state.authorityRevision(), operation, "ATTACHMENTS",
                                            requester.getGameProfile().getName(), message));
                        }
                    });
        }
    }
}
