package com.petrick.vtt.network.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petrick.vtt.VTT;
import com.petrick.vtt.core.session.VTTSession;
import com.petrick.vtt.feature.tabletop.VttDoor;
import com.petrick.vtt.feature.tabletop.VttFogArea;
import com.petrick.vtt.feature.tabletop.VttSceneCollisionBox;
import com.petrick.vtt.feature.tabletop.VttSceneBackgroundTransform;
import com.petrick.vtt.feature.tabletop.VttSceneCameraView;
import com.petrick.vtt.feature.tabletop.VttSceneGrid;
import com.petrick.vtt.feature.tabletop.VttSceneMap;
import com.petrick.vtt.feature.tabletop.VttWall;
import com.petrick.vtt.feature.tabletop.VttLight;
import com.petrick.vtt.feature.tabletop.VttAttachmentBinding;
import com.petrick.vtt.feature.tabletop.VttTokenStateAppearance;
import com.petrick.vtt.network.payload.VttEnvironmentCommandPayload;
import com.petrick.vtt.network.payload.VttEnvironmentCommandUpdatePayload;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Detects and applies granular server-authoritative environment mutations. */
public final class VttClientEnvironmentCommandSync {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<String, String> lastWalls = new LinkedHashMap<>();
    private static final Map<String, String> lastMaps = new LinkedHashMap<>();
    private static final Map<String, String> lastDoors = new LinkedHashMap<>();
    private static final Map<String, String> lastHiddenFog = new LinkedHashMap<>();
    private static final Map<String, String> lastRevealedFog = new LinkedHashMap<>();
    private static final Map<String, String> lastVision = new LinkedHashMap<>();
    private static final Map<String, String> lastLights = new LinkedHashMap<>();
    private static final Map<String, Long> nextSequences = new LinkedHashMap<>();
    private static final Map<String, Long> latestSentSequences = new LinkedHashMap<>();
    private static final Map<String, Long> latestRevisions = new LinkedHashMap<>();
    private static final Set<String> pendingEntityTypes = new LinkedHashSet<>();
    private static final long CONFIRMATION_IDLE_MS = 300L;
    private static final long CONFIRMATION_TIMEOUT_MS = 10_000L;
    private static long snapshotVersion = -1L;
    private static long authorityRevision = -1L;
    private static String lastFogConfigJson;
    private static String lastGridConfigJson;
    private static String observedGridConfigJson;
    private static int stableGridConfigTicks;
    private static String lastLightingConfigJson;
    private static String observedLightingConfigJson;
    private static int stableLightingConfigTicks;
    private static String activeSceneId;
    private static boolean mapPreviewActive;
    private static boolean environmentConfirmationPending;
    private static boolean environmentCorrectionReceived;
    private static long lastCommandSentAt;

    private VttClientEnvironmentCommandSync() {}

    public static void tick(VTTSession session) {
        if (session == null || !session.hasNetworkSnapshot() || !session.isLocalMaster()) return;
        activeSceneId = session.getActiveScene().getId();
        Map<String, String> walls = jsonById(session.getActiveScene().getWalls(), VttWall::getId);
        Map<String, String> maps = jsonById(session.getActiveScene().getMaps(), VttSceneMap::getId);
        Map<String, String> doors = jsonById(session.getActiveScene().getDoors(), VttDoor::getId);
        Map<String, String> hiddenFog = jsonById(
                session.getActiveScene().getFogOfWar().getHiddenAreas(), VttFogArea::getId);
        Map<String, String> revealedFog = jsonById(
                session.getActiveScene().getFogOfWar().getRevealedAreas(), VttFogArea::getId);
        Map<String, String> vision = visionById(session);
        Map<String, String> lights = jsonById(
                session.getActiveScene().getLights(), VttLight::getId);
        String fogConfigJson = fogConfigJson(session);
        String gridConfigJson = GSON.toJson(session.getActiveScene().getGrid());
        String lightingConfigJson = GSON.toJson(new LightingRaycastConfig(
                session.getActiveScene().getLighting().getVisionRayCount()));

        if (snapshotVersion != session.getNetworkSnapshotVersion()) {
            snapshotVersion = session.getNetworkSnapshotVersion();
            if (authorityRevision != session.getNetworkAuthorityRevision()) {
                authorityRevision = session.getNetworkAuthorityRevision();
                nextSequences.clear();
                latestRevisions.clear();
                latestSentSequences.clear();
                pendingEntityTypes.clear();
                environmentConfirmationPending = false;
                environmentCorrectionReceived = false;
            }
            replace(lastWalls, walls);
            replace(lastMaps, maps);
            replace(lastDoors, doors);
            replace(lastHiddenFog, hiddenFog);
            replace(lastRevealedFog, revealedFog);
            replace(lastVision, vision);
            replace(lastLights, lights);
            lastFogConfigJson = fogConfigJson;
            lastGridConfigJson = gridConfigJson;
            observedGridConfigJson = gridConfigJson;
            stableGridConfigTicks = 0;
            lastLightingConfigJson = lightingConfigJson;
            observedLightingConfigJson = lightingConfigJson;
            stableLightingConfigTicks = 0;
            return;
        }

        if (VttClientSceneHistorySync.isPending()) return;

        if (!mapPreviewActive) {
            syncMap(VttEnvironmentCommandPayload.MAP, maps, lastMaps);
        }
        syncMap(VttEnvironmentCommandPayload.WALL, walls, lastWalls);
        syncMap(VttEnvironmentCommandPayload.DOOR, doors, lastDoors);
        syncMap(VttEnvironmentCommandPayload.FOG_HIDDEN, hiddenFog, lastHiddenFog);
        syncMap(VttEnvironmentCommandPayload.FOG_REVEALED, revealedFog, lastRevealedFog);
        syncMap(VttEnvironmentCommandPayload.VISION, vision, lastVision);
        syncMap(VttEnvironmentCommandPayload.LIGHT, lights, lastLights);
        if (!Objects.equals(fogConfigJson, lastFogConfigJson)) {
            send(VttEnvironmentCommandPayload.UPSERT, VttEnvironmentCommandPayload.FOG_CONFIG,
                    "fog", fogConfigJson);
            lastFogConfigJson = fogConfigJson;
        }
        if (!Objects.equals(gridConfigJson, observedGridConfigJson)) {
            observedGridConfigJson = gridConfigJson;
            stableGridConfigTicks = 0;
        } else if (stableGridConfigTicks < 3) {
            stableGridConfigTicks++;
        }
        if (stableGridConfigTicks >= 3
                && !Objects.equals(gridConfigJson, lastGridConfigJson)) {
            send(VttEnvironmentCommandPayload.UPSERT, VttEnvironmentCommandPayload.GRID_CONFIG,
                    "grid", gridConfigJson);
            lastGridConfigJson = gridConfigJson;
        }
        if (!Objects.equals(lightingConfigJson, observedLightingConfigJson)) {
            observedLightingConfigJson = lightingConfigJson;
            stableLightingConfigTicks = 0;
        } else if (stableLightingConfigTicks < 3) {
            stableLightingConfigTicks++;
        }
        if (stableLightingConfigTicks >= 3
                && !Objects.equals(lightingConfigJson, lastLightingConfigJson)) {
            send(VttEnvironmentCommandPayload.UPSERT,
                    VttEnvironmentCommandPayload.LIGHTING_CONFIG,
                    "lighting", lightingConfigJson);
            lastLightingConfigJson = lightingConfigJson;
        }
        finishEnvironmentConfirmationIfReady(session);
    }

    public static void accept(VTTSession session, VttEnvironmentCommandUpdatePayload update) {
        if (session == null || update == null || !session.hasNetworkSnapshot()) return;
        if (update.authorityRevision() != session.getNetworkAuthorityRevision()) {
            if (update.authorityRevision() > session.getNetworkAuthorityRevision()) {
                session.requestEnvironmentResync();
            }
            return;
        }
        if (!update.sceneId().equals(session.getActiveScene().getId())) return;
        String revisionKey = entityKey(update.entityType(), update.entityId());
        long latestSent = latestSentSequences.getOrDefault(revisionKey, 0L);
        boolean localOrigin = update.originPlayerId().equals(session.getLocalPlayerId());
        boolean serverCorrection = "server".equals(update.originPlayerId());
        boolean acknowledgement = (localOrigin || serverCorrection)
                && latestSent > 0L
                && update.clientSequence() >= latestSent;
        if (update.entityRevision() <= latestRevisions.getOrDefault(revisionKey, -1L)) {
            if (acknowledgement) {
                latestSentSequences.remove(revisionKey);
                if (serverCorrection) environmentCorrectionReceived = true;
            }
            return;
        }
        if ((localOrigin || serverCorrection)
                && update.clientSequence() < latestSent) return;
        latestRevisions.put(revisionKey, update.entityRevision());
        try {
            boolean delete = VttEnvironmentCommandPayload.DELETE.equals(update.operation());
            switch (update.entityType()) {
                case VttEnvironmentCommandPayload.MAP -> {
                    session.getActiveScene().removeMap(update.entityId());
                    if (!delete) session.getActiveScene().addMap(
                            GSON.fromJson(update.entityJson(), VttSceneMap.class));
                    updateLast(lastMaps, update, delete);
                }
                case VttEnvironmentCommandPayload.WALL -> {
                    if (delete) {
                        session.getActiveScene().removeWall(update.entityId());
                    } else {
                        session.getActiveScene().getWalls().removeIf(
                                wall -> wall != null && update.entityId().equals(wall.getId()));
                        session.getActiveScene().addWall(GSON.fromJson(update.entityJson(), VttWall.class));
                    }
                    updateLast(lastWalls, update, delete);
                }
                case VttEnvironmentCommandPayload.DOOR -> {
                    session.getActiveScene().getDoors().removeIf(
                            door -> door != null && update.entityId().equals(door.getId()));
                    if (!delete) session.getActiveScene().addDoor(
                            GSON.fromJson(update.entityJson(), VttDoor.class));
                    updateLast(lastDoors, update, delete);
                }
                case VttEnvironmentCommandPayload.FOG_HIDDEN -> {
                    session.getActiveScene().getFogOfWar().removeArea(update.entityId());
                    if (!delete) session.getActiveScene().getFogOfWar().addHiddenArea(
                            GSON.fromJson(update.entityJson(), VttFogArea.class));
                    updateLast(lastHiddenFog, update, delete);
                    lastRevealedFog.remove(update.entityId());
                }
                case VttEnvironmentCommandPayload.FOG_REVEALED -> {
                    session.getActiveScene().getFogOfWar().removeArea(update.entityId());
                    if (!delete) session.getActiveScene().getFogOfWar().addRevealedArea(
                            GSON.fromJson(update.entityJson(), VttFogArea.class));
                    updateLast(lastRevealedFog, update, delete);
                    lastHiddenFog.remove(update.entityId());
                }
                case VttEnvironmentCommandPayload.FOG_CONFIG -> {
                    FogConfig config = GSON.fromJson(update.entityJson(), FogConfig.class);
                    session.getActiveScene().getFogOfWar().setEnabled(config.enabled());
                    session.getActiveScene().getFogOfWar().setDefaultHidden(config.defaultHidden());
                    lastFogConfigJson = update.entityJson();
                }
                case VttEnvironmentCommandPayload.GRID_CONFIG -> {
                    VttSceneGrid grid = GSON.fromJson(update.entityJson(), VttSceneGrid.class);
                    session.getActiveScene().setGrid(grid);
                    lastGridConfigJson = GSON.toJson(session.getActiveScene().getGrid());
                    observedGridConfigJson = lastGridConfigJson;
                    stableGridConfigTicks = 0;
                }
                case VttEnvironmentCommandPayload.LIGHTING_CONFIG -> {
                    LightingRaycastConfig lighting = GSON.fromJson(
                            update.entityJson(), LightingRaycastConfig.class);
                    if (lighting != null) session.getActiveScene().getLighting()
                            .setVisionRayCount(lighting.visionRayCount());
                    lastLightingConfigJson = GSON.toJson(new LightingRaycastConfig(
                            session.getActiveScene().getLighting().getVisionRayCount()));
                    observedLightingConfigJson = lastLightingConfigJson;
                    stableLightingConfigTicks = 0;
                }
                case VttEnvironmentCommandPayload.LIGHTING_COLOR -> {
                    DarknessColorConfig color = GSON.fromJson(
                            update.entityJson(), DarknessColorConfig.class);
                    if (color != null) session.getActiveScene().getLighting()
                            .setDarknessColorRgb(color.darknessColorRgb());
                }
                case VttEnvironmentCommandPayload.BACKGROUND_CONFIG -> {
                    VttSceneBackgroundTransform transform = GSON.fromJson(
                            update.entityJson(), VttSceneBackgroundTransform.class);
                    session.getActiveScene().setBackgroundTransform(transform);
                }
                case VttEnvironmentCommandPayload.CAMERA_CONFIG -> {
                    if (delete) {
                        session.getActiveScene().clearInitialCameraView();
                    } else {
                        VttSceneCameraView view = GSON.fromJson(
                                update.entityJson(), VttSceneCameraView.class);
                        session.getActiveScene().setInitialCameraView(view);
                    }
                }
                case VttEnvironmentCommandPayload.VISION -> {
                    if (!delete) applyVision(session,
                            GSON.fromJson(update.entityJson(), VisionState.class));
                    updateLast(lastVision, update, delete);
                }
                case VttEnvironmentCommandPayload.LIGHT -> {
                    session.getActiveScene().removeLight(update.entityId());
                    if (!delete) session.getActiveScene().addLight(
                            GSON.fromJson(update.entityJson(), VttLight.class));
                    updateLast(lastLights, update, delete);
                }
                case VttEnvironmentCommandPayload.ATTACHMENT_BINDING -> {
                    session.getActiveScene().getObjects().stream()
                            .filter(object -> object != null
                                    && update.entityId().equals(object.getId()))
                            .findFirst().ifPresent(object -> object.setAttachmentBinding(delete
                                    ? null : GSON.fromJson(
                                    update.entityJson(), VttAttachmentBinding.class)));
                }
                case VttEnvironmentCommandPayload.TOKEN_STATE_OVERRIDE -> {
                    TokenStateOverrideSnapshot snapshot = GSON.fromJson(
                            update.entityJson(), TokenStateOverrideSnapshot.class);
                    session.getActiveScene().getObjects().stream()
                            .filter(object -> object != null
                                    && update.entityId().equals(object.getId()))
                            .findFirst().ifPresent(object -> {
                                object.setGlobalStateAppearance(snapshot == null
                                        ? null : snapshot.globalAppearance());
                                object.setStateAppearances(snapshot == null
                                        ? Map.of() : snapshot.stateAppearances());
                            });
                }
                default -> VTT.LOGGER.warn("Ignored unknown VTT environment entity: {}", update.entityType());
            }
            if (acknowledgement) latestSentSequences.remove(revisionKey);
            if (serverCorrection) environmentCorrectionReceived = true;
        } catch (RuntimeException exception) {
            VTT.LOGGER.error("Failed to apply VTT environment command", exception);
            if (acknowledgement) latestSentSequences.remove(revisionKey);
            environmentCorrectionReceived = true;
            session.requestEnvironmentResync();
        }
    }

    public static void reset() {
        snapshotVersion = -1L;
        lastWalls.clear();
        lastMaps.clear();
        lastDoors.clear();
        lastHiddenFog.clear();
        lastRevealedFog.clear();
        lastVision.clear();
        lastLights.clear();
        nextSequences.clear();
        latestRevisions.clear();
        latestSentSequences.clear();
        pendingEntityTypes.clear();
        lastFogConfigJson = null;
        lastGridConfigJson = null;
        observedGridConfigJson = null;
        stableGridConfigTicks = 0;
        lastLightingConfigJson = null;
        observedLightingConfigJson = null;
        stableLightingConfigTicks = 0;
        activeSceneId = null;
        authorityRevision = -1L;
        mapPreviewActive = false;
        environmentConfirmationPending = false;
        environmentCorrectionReceived = false;
        lastCommandSentAt = 0L;
    }

    private static <T> Map<String, String> jsonById(List<T> values, Function<T, String> idGetter) {
        Map<String, String> result = new LinkedHashMap<>();
        for (T value : values) {
            if (value == null) continue;
            String id = idGetter.apply(value);
            if (id != null && !id.isBlank()) result.put(id, GSON.toJson(value));
        }
        return result;
    }

    private static Map<String, String> visionById(VTTSession session) {
        Map<String, String> result = new LinkedHashMap<>();
        session.getActiveScene().getObjects().stream()
                .filter(object -> object != null && object.getId() != null)
                .forEach(object -> result.put(object.getId(), GSON.toJson(new VisionState(
                        object.getId(), object.getVisionInnerRadius(),
                        object.getVisionOuterRadius() > 0.0 ? object.getVisionOuterRadius() : 512.0,
                        object.isVisionEnabled(), object.isVisionOwnLightEnabled(),
                        object.getState().getTintColorRgb(), object.getCollisionBox()))));
        return result;
    }

    private static String fogConfigJson(VTTSession session) {
        return GSON.toJson(new FogConfig(
                session.getActiveScene().getFogOfWar().isEnabled(),
                session.getActiveScene().getFogOfWar().isDefaultHidden()));
    }

    private static void syncMap(String entityType, Map<String, String> current, Map<String, String> last) {
        for (String oldId : List.copyOf(last.keySet())) {
            if (!current.containsKey(oldId)) send(
                    VttEnvironmentCommandPayload.DELETE, entityType, oldId, "");
        }
        current.forEach((id, json) -> {
            if (!Objects.equals(json, last.get(id))) send(
                    VttEnvironmentCommandPayload.UPSERT, entityType, id, json);
        });
        replace(last, current);
    }

    private static void send(String operation, String entityType, String entityId, String json) {
        if (json != null && json.length() > VttEnvironmentCommandPayload.MAX_JSON_LENGTH) {
            VTT.LOGGER.warn("Skipped oversized VTT environment command for {}", entityId);
            return;
        }
        if (activeSceneId == null || activeSceneId.isBlank()) return;
        String sequenceKey = entityKey(entityType, entityId);
        long clientSequence = nextSequences.merge(sequenceKey, 1L, Long::sum);
        latestSentSequences.put(sequenceKey, clientSequence);
        pendingEntityTypes.add(entityType);
        environmentConfirmationPending = true;
        lastCommandSentAt = System.currentTimeMillis();
        PacketDistributor.sendToServer(new VttEnvironmentCommandPayload(
                authorityRevision, clientSequence, operation, activeSceneId,
                entityType, entityId, json == null ? "" : json));
    }

    public static void sendBackgroundTransform(VTTSession session) {
        if (session == null || !session.hasNetworkSnapshot()
                || !session.isLocalMaster() || session.getActiveScene() == null) return;
        activeSceneId = session.getActiveScene().getId();
        authorityRevision = session.getNetworkAuthorityRevision();
        send(VttEnvironmentCommandPayload.UPSERT,
                VttEnvironmentCommandPayload.BACKGROUND_CONFIG,
                "background",
                GSON.toJson(session.getActiveScene().getBackgroundTransform()));
    }

    public static void setMapPreviewActive(boolean active) {
        mapPreviewActive = active;
    }

    public static void flushSceneMaps(VTTSession session) {
        if (session == null || !session.hasNetworkSnapshot()
                || !session.isLocalMaster() || session.getActiveScene() == null) return;
        activeSceneId = session.getActiveScene().getId();
        authorityRevision = session.getNetworkAuthorityRevision();
        Map<String, String> maps = jsonById(
                session.getActiveScene().getMaps(), VttSceneMap::getId);
        syncMap(VttEnvironmentCommandPayload.MAP, maps, lastMaps);
    }

    public static void sendInitialCameraView(VTTSession session) {
        if (session == null || !session.hasNetworkSnapshot()
                || !session.isLocalMaster() || session.getActiveScene() == null) return;
        activeSceneId = session.getActiveScene().getId();
        authorityRevision = session.getNetworkAuthorityRevision();
        VttSceneCameraView view = session.getActiveScene().getInitialCameraView();
        send(view == null
                        ? VttEnvironmentCommandPayload.DELETE
                        : VttEnvironmentCommandPayload.UPSERT,
                VttEnvironmentCommandPayload.CAMERA_CONFIG,
                "initial_camera",
                view == null ? "" : GSON.toJson(view));
    }

    public static void sendLightingColor(VTTSession session) {
        if (session == null || !session.hasNetworkSnapshot()
                || !session.isLocalMaster() || session.getActiveScene() == null) return;
        activeSceneId = session.getActiveScene().getId();
        authorityRevision = session.getNetworkAuthorityRevision();
        send(VttEnvironmentCommandPayload.UPSERT,
                VttEnvironmentCommandPayload.LIGHTING_COLOR,
                "darkness_color",
                GSON.toJson(new DarknessColorConfig(
                        session.getActiveScene().getLighting().getDarknessColorRgb())));
    }

    public static void sendAttachmentBinding(
            VTTSession session, String attachmentId, VttAttachmentBinding binding
    ) {
        if (session == null || !session.hasNetworkSnapshot()
                || !session.isLocalMaster() || session.getActiveScene() == null
                || attachmentId == null || attachmentId.isBlank()) return;
        activeSceneId = session.getActiveScene().getId();
        authorityRevision = session.getNetworkAuthorityRevision();
        send(binding == null ? VttEnvironmentCommandPayload.DELETE
                        : VttEnvironmentCommandPayload.UPSERT,
                VttEnvironmentCommandPayload.ATTACHMENT_BINDING,
                attachmentId, binding == null ? "" : GSON.toJson(binding));
    }

    public static void sendLight(VTTSession session, VttLight light) {
        if (session == null || light == null || !session.hasNetworkSnapshot()
                || !session.isLocalMaster() || session.getActiveScene() == null) return;
        activeSceneId = session.getActiveScene().getId();
        authorityRevision = session.getNetworkAuthorityRevision();
        send(VttEnvironmentCommandPayload.UPSERT, VttEnvironmentCommandPayload.LIGHT,
                light.getId(), GSON.toJson(light));
    }

    public static void sendTokenStateOverrides(
            VTTSession session, String tokenId, VttTokenStateAppearance globalAppearance,
            Map<String, VttTokenStateAppearance> stateAppearances
    ) {
        if (session == null || !session.hasNetworkSnapshot()
                || !session.isLocalMaster() || session.getActiveScene() == null
                || tokenId == null || tokenId.isBlank()) return;
        activeSceneId = session.getActiveScene().getId();
        authorityRevision = session.getNetworkAuthorityRevision();
        send(VttEnvironmentCommandPayload.UPSERT,
                VttEnvironmentCommandPayload.TOKEN_STATE_OVERRIDE, tokenId,
                GSON.toJson(new TokenStateOverrideSnapshot(
                        globalAppearance, stateAppearances == null ? Map.of() : stateAppearances)));
    }

    private static String entityKey(String entityType, String entityId) {
        return entityType + "\u0000" + entityId;
    }

    private record TokenStateOverrideSnapshot(
            VttTokenStateAppearance globalAppearance,
            Map<String, VttTokenStateAppearance> stateAppearances
    ) {}

    private static void finishEnvironmentConfirmationIfReady(VTTSession session) {
        if (!environmentConfirmationPending) return;
        long idleTime = System.currentTimeMillis() - lastCommandSentAt;
        if (latestSentSequences.isEmpty() && idleTime >= CONFIRMATION_IDLE_MS) {
            if (!environmentCorrectionReceived) {
                VttClientEditorNotice.show(confirmationMessage(pendingEntityTypes));
            }
            environmentConfirmationPending = false;
            environmentCorrectionReceived = false;
            pendingEntityTypes.clear();
            return;
        }
        if (!latestSentSequences.isEmpty() && idleTime >= CONFIRMATION_TIMEOUT_MS) {
            latestSentSequences.clear();
            pendingEntityTypes.clear();
            environmentConfirmationPending = false;
            environmentCorrectionReceived = false;
            VttClientEditorNotice.show("Environment confirmation timed out; resynchronizing");
            session.requestEnvironmentResync();
        }
    }

    private static String confirmationMessage(Set<String> entityTypes) {
        if (entityTypes.size() != 1) return "Environment changes confirmed";
        return switch (entityTypes.iterator().next()) {
            case VttEnvironmentCommandPayload.MAP -> "Scene map changes confirmed";
            case VttEnvironmentCommandPayload.WALL -> "Wall changes confirmed";
            case VttEnvironmentCommandPayload.DOOR -> "Door changes confirmed";
            case VttEnvironmentCommandPayload.FOG_HIDDEN,
                 VttEnvironmentCommandPayload.FOG_REVEALED,
                 VttEnvironmentCommandPayload.FOG_CONFIG -> "Fog changes confirmed";
            case VttEnvironmentCommandPayload.GRID_CONFIG -> "Grid changes confirmed";
            case VttEnvironmentCommandPayload.LIGHTING_CONFIG ->
                    "Lighting changes confirmed";
            case VttEnvironmentCommandPayload.LIGHTING_COLOR ->
                    "Darkness color applied to players";
            case VttEnvironmentCommandPayload.LIGHT -> "Light changes confirmed";
            case VttEnvironmentCommandPayload.VISION -> "Token vision changes confirmed";
            case VttEnvironmentCommandPayload.BACKGROUND_CONFIG ->
                    "Scene background changes confirmed";
            case VttEnvironmentCommandPayload.CAMERA_CONFIG ->
                    "Initial camera changes confirmed";
            default -> "Environment changes confirmed";
        };
    }

    private static void updateLast(Map<String, String> target,
                                   VttEnvironmentCommandUpdatePayload update, boolean delete) {
        if (delete) target.remove(update.entityId());
        else target.put(update.entityId(), update.entityJson());
    }

    private static void replace(Map<String, String> target, Map<String, String> source) {
        target.clear();
        target.putAll(source);
    }

    private static void applyVision(VTTSession session, VisionState vision) {
        if (vision == null || vision.objectId() == null) return;
        session.getActiveScene().getObjects().stream()
                .filter(object -> object != null && vision.objectId().equals(object.getId()))
                .findFirst().ifPresent(object -> {
                    object.setVisionInnerRadius(vision.innerRadius());
                    object.setVisionOuterRadius(vision.outerRadius());
                    object.setVisionEnabled(vision.enabled());
                    object.setVisionOwnLightEnabled(vision.ownLightEnabled());
                    object.getState().setTintColorRgb(vision.tintColorRgb());
                    if (vision.collisionBox() == null || validCollisionBox(vision.collisionBox())) {
                        object.setCollisionBox(vision.collisionBox());
                    }
                });
    }

    private static boolean validCollisionBox(VttSceneCollisionBox box) {
        return box != null && Double.isFinite(box.getOffsetX()) && Double.isFinite(box.getOffsetY())
                && Double.isFinite(box.getWidth()) && Double.isFinite(box.getHeight())
                && box.getWidth() >= 1.0 && box.getHeight() >= 1.0;
    }

    private record FogConfig(boolean enabled, boolean defaultHidden) {}
    private record LightingRaycastConfig(int visionRayCount) {}
    private record DarknessColorConfig(int darknessColorRgb) {}
    private record VisionState(
            String objectId, double innerRadius, double outerRadius, boolean enabled,
            boolean ownLightEnabled, int tintColorRgb, VttSceneCollisionBox collisionBox) {}
}
