package com.petrick.vtt.network.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.petrick.vtt.VTT;
import com.petrick.vtt.core.session.VTTSession;
import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.tabletop.VttSceneObject;
import com.petrick.vtt.feature.tabletop.VttSceneSize;
import com.petrick.vtt.feature.tabletop.VttSceneState;
import com.petrick.vtt.feature.tabletop.VttSceneTransform;
import com.petrick.vtt.feature.tabletop.VttSceneCollisionBox;
import com.petrick.vtt.feature.tabletop.persistence.VttSceneToCanvasSceneMapper;
import com.petrick.vtt.network.payload.VttTokenLifecycleRequestPayload;
import com.petrick.vtt.network.payload.VttTokenLifecycleUpdatePayload;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Detects master-authored token creation/duplication/deletion around the existing editor flows. */
public final class VttClientTokenLifecycleSync {
    public static final String CREATE = "CREATE";
    public static final String DELETE = "DELETE";
    private static final Gson GSON = new GsonBuilder().create();
    private static final Set<String> KNOWN_IDS = new LinkedHashSet<>();
    private static long snapshotVersion = -1L;

    private VttClientTokenLifecycleSync() {}

    public static void tick(VTTSession session) {
        if (session == null || !session.hasNetworkSnapshot()) return;
        if (snapshotVersion != session.getNetworkSnapshotVersion()) {
            snapshotVersion = session.getNetworkSnapshotVersion();
            capture(session);
            return;
        }
        if (VttClientSceneHistorySync.isPending()) return;
        if (!session.isLocalMaster()) return;

        Map<String, CanvasObject> current = currentTokens(session);
        for (CanvasObject object : current.values()) {
            if (KNOWN_IDS.contains(object.id())) continue;
            VttSceneObject sceneObject = toSceneObject(session, object,
                    session.getCanvasScene().getObjectLayerIndex(object.id()));
            PacketDistributor.sendToServer(new VttTokenLifecycleRequestPayload(
                    CREATE, object.id(), GSON.toJson(sceneObject)));
        }
        for (String knownId : Set.copyOf(KNOWN_IDS)) {
            if (!current.containsKey(knownId)) {
                PacketDistributor.sendToServer(new VttTokenLifecycleRequestPayload(DELETE, knownId, ""));
            }
        }
        KNOWN_IDS.clear();
        KNOWN_IDS.addAll(current.keySet());
    }

    public static void acceptConfirmed(VTTSession session, VttTokenLifecycleUpdatePayload update) {
        if (session == null || update == null || !session.hasNetworkSnapshot()) return;
        if (CREATE.equals(update.operation())) applyCreate(session, update);
        else if (DELETE.equals(update.operation())) applyDelete(session, update.objectId());
    }

    public static void reset() {
        snapshotVersion = -1L;
        KNOWN_IDS.clear();
    }

    private static void applyCreate(VTTSession session, VttTokenLifecycleUpdatePayload update) {
        try {
            VttSceneObject sceneObject = GSON.fromJson(update.objectJson(), VttSceneObject.class);
            if (sceneObject == null) return;
            boolean ownRenamedRequest = update.originPlayerId().equals(session.getLocalPlayerId())
                    && !update.requestedObjectId().equals(update.objectId());
            if (ownRenamedRequest) {
                session.getCanvasScene().removeObjects(Set.of(update.requestedObjectId()));
                session.getActiveScene().removeObject(update.requestedObjectId());
                KNOWN_IDS.remove(update.requestedObjectId());
            }

            CanvasObject canvasObject = VttSceneToCanvasSceneMapper.convertObject(
                    sceneObject, session.getTokenDefinitionRegistry());
            if (canvasObject == null) return;
            if (session.getCanvasScene().findObjectById(canvasObject.id()) == null) {
                session.getCanvasScene().addObject(canvasObject);
            } else {
                session.getCanvasScene().replaceObject(canvasObject);
            }
            session.getCanvasScene().moveObjectToLayer(canvasObject.id(), sceneObject.getLayerIndex());

            session.getActiveScene().removeObject(sceneObject.getId());
            session.getActiveScene().addObject(sceneObject);
            normalizeSceneLayers(session);
            KNOWN_IDS.add(sceneObject.getId());
        } catch (RuntimeException exception) {
            VTT.LOGGER.error("Failed to apply synchronized token creation", exception);
        }
    }

    private static void applyDelete(VTTSession session, String objectId) {
        if (objectId == null || objectId.isBlank()) return;
        session.getCanvasScene().removeObjects(Set.of(objectId));
        session.getActiveScene().removeObject(objectId);
        session.getActiveScene().removeVisionSourceObjectId(objectId);
        KNOWN_IDS.remove(objectId);
        normalizeSceneLayers(session);
    }

    private static void normalizeSceneLayers(VTTSession session) {
        for (VttSceneObject object : session.getActiveScene().getObjects()) {
            if (object == null) continue;
            int layer = session.getCanvasScene().getObjectLayerIndex(object.getId());
            if (layer >= 0) object.setLayerIndex(layer);
        }
    }

    private static void capture(VTTSession session) {
        KNOWN_IDS.clear();
        KNOWN_IDS.addAll(currentTokens(session).keySet());
    }

    private static Map<String, CanvasObject> currentTokens(VTTSession session) {
        Map<String, CanvasObject> result = new LinkedHashMap<>();
        for (CanvasObject object : session.getCanvasScene().getObjects()) {
            if (object.hasSourceTokenDefinition()) result.put(object.id(), object);
        }
        return result;
    }

    private static VttSceneObject toSceneObject(VTTSession session, CanvasObject object, int layerIndex) {
        VttSceneObject result = new VttSceneObject(object.id(), object.displayName(),
                object.sourceTokenDefinitionId());
        result.setTransform(new VttSceneTransform(object.transform().position().x(),
                object.transform().position().y(), object.transform().scale().x(),
                object.transform().scale().y(), object.transform().rotationDegrees()));
        result.setSize(new VttSceneSize(object.size().x(), object.size().y()));
        result.setState(new VttSceneState(object.activeStateId(), object.visible(),
                object.flippedHorizontally()));
        result.setLayerIndex(layerIndex);
        VttSceneObject exactSource = session.getActiveScene().getObjects().stream()
                .filter(source -> source != null && object.id().equals(source.getId()))
                .findFirst().orElse(null);
        VttSceneObject duplicateSource = session.getActiveScene().getObjects().stream()
                .filter(source -> source != null && source.getId() != null
                        && object.id().startsWith(source.getId() + "_copy"))
                .max(java.util.Comparator.comparingInt(source -> source.getId().length()))
                .orElse(null);
        VttSceneObject metadataSource = exactSource != null ? exactSource : duplicateSource;
        if (metadataSource != null) {
            result.setOwnerId(metadataSource.getOwnerId());
            result.setVisionInnerRadius(metadataSource.getVisionInnerRadius());
            result.setVisionOuterRadius(metadataSource.getVisionOuterRadius());
            result.setVisionEnabled(metadataSource.isVisionEnabled());
            VttSceneCollisionBox sourceBox = metadataSource.getCollisionBox();
            if (sourceBox != null) {
                result.setCollisionBox(new VttSceneCollisionBox(sourceBox.getOffsetX(),
                        sourceBox.getOffsetY(), sourceBox.getWidth(), sourceBox.getHeight()));
            }
        } else {
            session.getTokenDefinitionRegistry().findById(object.sourceTokenDefinitionId())
                    .ifPresent(definition -> result.setOwnerId(definition.defaultOwnerId()));
        }
        return result;
    }
}
