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
import com.petrick.vtt.feature.tabletop.VttWall;
import com.petrick.vtt.network.payload.VttEnvironmentCommandPayload;
import com.petrick.vtt.network.payload.VttEnvironmentCommandUpdatePayload;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Detects and applies granular server-authoritative environment mutations. */
public final class VttClientEnvironmentCommandSync {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<String, String> lastWalls = new LinkedHashMap<>();
    private static final Map<String, String> lastDoors = new LinkedHashMap<>();
    private static final Map<String, String> lastHiddenFog = new LinkedHashMap<>();
    private static final Map<String, String> lastRevealedFog = new LinkedHashMap<>();
    private static final Map<String, String> lastVision = new LinkedHashMap<>();
    private static final Map<String, Long> nextSequences = new LinkedHashMap<>();
    private static final Map<String, Long> latestRevisions = new LinkedHashMap<>();
    private static long snapshotVersion = -1L;
    private static long authorityRevision = -1L;
    private static String lastFogConfigJson;
    private static String lastGridConfigJson;
    private static String observedGridConfigJson;
    private static int stableGridConfigTicks;
    private static String activeSceneId;

    private VttClientEnvironmentCommandSync() {}

    public static void tick(VTTSession session) {
        if (session == null || !session.hasNetworkSnapshot() || !session.isLocalMaster()) return;
        activeSceneId = session.getActiveScene().getId();
        Map<String, String> walls = jsonById(session.getActiveScene().getWalls(), VttWall::getId);
        Map<String, String> doors = jsonById(session.getActiveScene().getDoors(), VttDoor::getId);
        Map<String, String> hiddenFog = jsonById(
                session.getActiveScene().getFogOfWar().getHiddenAreas(), VttFogArea::getId);
        Map<String, String> revealedFog = jsonById(
                session.getActiveScene().getFogOfWar().getRevealedAreas(), VttFogArea::getId);
        Map<String, String> vision = visionById(session);
        String fogConfigJson = fogConfigJson(session);
        String gridConfigJson = GSON.toJson(session.getActiveScene().getGrid());

        if (snapshotVersion != session.getNetworkSnapshotVersion()) {
            snapshotVersion = session.getNetworkSnapshotVersion();
            if (authorityRevision != session.getNetworkAuthorityRevision()) {
                authorityRevision = session.getNetworkAuthorityRevision();
                nextSequences.clear();
                latestRevisions.clear();
            }
            replace(lastWalls, walls);
            replace(lastDoors, doors);
            replace(lastHiddenFog, hiddenFog);
            replace(lastRevealedFog, revealedFog);
            replace(lastVision, vision);
            lastFogConfigJson = fogConfigJson;
            lastGridConfigJson = gridConfigJson;
            observedGridConfigJson = gridConfigJson;
            stableGridConfigTicks = 0;
            return;
        }

        syncMap(VttEnvironmentCommandPayload.WALL, walls, lastWalls);
        syncMap(VttEnvironmentCommandPayload.DOOR, doors, lastDoors);
        syncMap(VttEnvironmentCommandPayload.FOG_HIDDEN, hiddenFog, lastHiddenFog);
        syncMap(VttEnvironmentCommandPayload.FOG_REVEALED, revealedFog, lastRevealedFog);
        syncMap(VttEnvironmentCommandPayload.VISION, vision, lastVision);
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
    }

    public static void accept(VTTSession session, VttEnvironmentCommandUpdatePayload update) {
        if (session == null || update == null || !session.hasNetworkSnapshot()) return;
        if (update.authorityRevision() != session.getNetworkAuthorityRevision()
                || !update.sceneId().equals(session.getActiveScene().getId())) return;
        String revisionKey = entityKey(update.entityType(), update.entityId());
        if (update.entityRevision() <= latestRevisions.getOrDefault(revisionKey, -1L)) return;
        latestRevisions.put(revisionKey, update.entityRevision());
        if (update.originPlayerId().equals(session.getLocalPlayerId())) return;
        try {
            boolean delete = VttEnvironmentCommandPayload.DELETE.equals(update.operation());
            switch (update.entityType()) {
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
                default -> VTT.LOGGER.warn("Ignored unknown VTT environment entity: {}", update.entityType());
            }
        } catch (RuntimeException exception) {
            VTT.LOGGER.error("Failed to apply VTT environment command", exception);
        }
    }

    public static void reset() {
        snapshotVersion = -1L;
        lastWalls.clear();
        lastDoors.clear();
        lastHiddenFog.clear();
        lastRevealedFog.clear();
        lastVision.clear();
        nextSequences.clear();
        latestRevisions.clear();
        lastFogConfigJson = null;
        lastGridConfigJson = null;
        observedGridConfigJson = null;
        stableGridConfigTicks = 0;
        activeSceneId = null;
        authorityRevision = -1L;
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
                        object.isVisionEnabled(), object.getCollisionBox()))));
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

    private static String entityKey(String entityType, String entityId) {
        return entityType + "\u0000" + entityId;
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
    private record VisionState(String objectId, double innerRadius, double outerRadius, boolean enabled,
                               VttSceneCollisionBox collisionBox) {}
}
