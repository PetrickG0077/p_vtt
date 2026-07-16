package com.petrick.vtt.network.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.petrick.vtt.VTT;
import com.petrick.vtt.core.session.VTTSession;
import com.petrick.vtt.feature.tabletop.VttDoor;
import com.petrick.vtt.feature.tabletop.VttFogOfWar;
import com.petrick.vtt.feature.tabletop.VttWall;
import com.petrick.vtt.network.payload.VttEnvironmentStateRequestPayload;
import com.petrick.vtt.network.payload.VttEnvironmentStateUpdatePayload;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Type;
import java.util.List;

/** Synchronizes master-authored walls, doors, fog and token vision settings. */
public final class VttClientEnvironmentStateSync {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Type DOOR_LIST_TYPE = new TypeToken<List<VttDoor>>() {}.getType();
    private static final Type WALL_LIST_TYPE = new TypeToken<List<VttWall>>() {}.getType();
    private static final Type VISION_LIST_TYPE = new TypeToken<List<VisionState>>() {}.getType();
    private static long snapshotVersion = -1L;
    private static String lastWallsJson;
    private static String lastDoorsJson;
    private static String lastFogJson;
    private static String lastVisionJson;

    private VttClientEnvironmentStateSync() {}

    public static void tick(VTTSession session) {
        if (session == null || !session.hasNetworkSnapshot() || !session.isLocalMaster()) return;
        String wallsJson = GSON.toJson(session.getActiveScene().getWalls());
        String doorsJson = GSON.toJson(session.getActiveScene().getDoors());
        String fogJson = GSON.toJson(session.getActiveScene().getFogOfWar());
        String visionJson = visionJson(session);
        if (snapshotVersion != session.getNetworkSnapshotVersion()) {
            snapshotVersion = session.getNetworkSnapshotVersion();
            lastWallsJson = wallsJson;
            lastDoorsJson = doorsJson;
            lastFogJson = fogJson;
            lastVisionJson = visionJson;
            return;
        }
        if (wallsJson.equals(lastWallsJson) && doorsJson.equals(lastDoorsJson)
                && fogJson.equals(lastFogJson) && visionJson.equals(lastVisionJson)) return;
        lastWallsJson = wallsJson;
        lastDoorsJson = doorsJson;
        lastFogJson = fogJson;
        lastVisionJson = visionJson;
        PacketDistributor.sendToServer(new VttEnvironmentStateRequestPayload(
                wallsJson, doorsJson, fogJson, visionJson));
    }

    public static void acceptConfirmed(VTTSession session, VttEnvironmentStateUpdatePayload update) {
        if (session == null || update == null || !session.hasNetworkSnapshot()) return;
        try {
            List<VttWall> walls = GSON.fromJson(update.wallsJson(), WALL_LIST_TYPE);
            List<VttDoor> doors = GSON.fromJson(update.doorsJson(), DOOR_LIST_TYPE);
            VttFogOfWar fog = GSON.fromJson(update.fogJson(), VttFogOfWar.class);
            List<VisionState> visionStates = GSON.fromJson(update.visionJson(), VISION_LIST_TYPE);
            session.getActiveScene().clearWalls();
            if (walls != null) walls.forEach(session.getActiveScene()::addWall);
            session.getActiveScene().clearDoors();
            if (doors != null) doors.forEach(session.getActiveScene()::addDoor);
            session.getActiveScene().setFogOfWar(fog);
            if (visionStates != null) {
                for (VisionState vision : visionStates) {
                    session.getActiveScene().getObjects().stream()
                            .filter(object -> object != null && vision.objectId().equals(object.getId()))
                            .findFirst().ifPresent(object -> {
                                object.setVisionInnerRadius(vision.innerRadius());
                                object.setVisionOuterRadius(vision.outerRadius());
                            });
                }
            }
            lastWallsJson = GSON.toJson(session.getActiveScene().getWalls());
            lastDoorsJson = GSON.toJson(session.getActiveScene().getDoors());
            lastFogJson = GSON.toJson(session.getActiveScene().getFogOfWar());
            lastVisionJson = visionJson(session);
        } catch (RuntimeException exception) {
            VTT.LOGGER.error("Failed to apply synchronized VTT environment state", exception);
        }
    }

    public static void reset() {
        snapshotVersion = -1L;
        lastWallsJson = null;
        lastDoorsJson = null;
        lastFogJson = null;
        lastVisionJson = null;
    }

    private static String visionJson(VTTSession session) {
        return GSON.toJson(session.getActiveScene().getObjects().stream()
                .filter(object -> object != null && object.getId() != null)
                .map(object -> new VisionState(object.getId(), object.getVisionInnerRadius(),
                        object.getVisionOuterRadius() > 0.0 ? object.getVisionOuterRadius() : 512.0)).toList());
    }

    private record VisionState(String objectId, double innerRadius, double outerRadius) {}
}
