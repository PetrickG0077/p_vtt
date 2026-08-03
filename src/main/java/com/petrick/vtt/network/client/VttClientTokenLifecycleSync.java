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
import com.petrick.vtt.feature.tabletop.VttAttachmentBinding;
import com.petrick.vtt.feature.tabletop.VttTokenStateAppearance;
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

        Map<String, CanvasObject> current = currentLifecycleObjects(session);
        for (CanvasObject object : current.values()) {
            if (KNOWN_IDS.contains(object.id())) continue;
            VttSceneObject sceneObject = toSceneObject(session, object,
                    session.getCanvasScene().getObjectLayerIndex(object.id()));
            VTT.LOGGER.info(
                    "Sending token lifecycle CREATE {} with state appearances {}",
                    object.id(), sceneObject.getStateAppearances().keySet());
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
                    sceneObject, session.getTokenDefinitionRegistry(),
                    session.getAttachmentDefinitionRegistry(), session.getAssetRegistry(),
                    session.getAssetThumbnailRegistry());
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
        KNOWN_IDS.addAll(currentLifecycleObjects(session).keySet());
    }

    private static Map<String, CanvasObject> currentLifecycleObjects(VTTSession session) {
        Map<String, CanvasObject> result = new LinkedHashMap<>();
        for (CanvasObject object : session.getCanvasScene().getObjects()) {
            if (object.hasSourceTokenDefinition() || object.sourceAttachmentDefinitionId() != null) {
                result.put(object.id(), object);
            }
        }
        return result;
    }

    private static VttSceneObject toSceneObject(VTTSession session, CanvasObject object, int layerIndex) {
        VttSceneObject result = new VttSceneObject();
        result.setId(object.id());
        result.setDisplayName(object.displayName());
        result.setSourceTokenDefinitionId(object.sourceTokenDefinitionId());
        result.setSourceAttachmentDefinitionId(object.sourceAttachmentDefinitionId());
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

        // A created token must carry the reusable presets from its catalog
        // definition in the authoritative CREATE request. Do this here as well
        // as in the editor placement flow: lifecycle synchronization is the
        // final serialization boundary before the server accepts the object.
        if (object.hasSourceTokenDefinition()) {
            session.getTokenDefinitionRegistry().findById(object.sourceTokenDefinitionId())
                    .ifPresent(definition -> {
                        result.setOwnerId(definition.defaultOwnerId());
                        result.setGlobalStateAppearance(new VttTokenStateAppearance());
                        Map<String, VttTokenStateAppearance> definitionAppearances =
                                new LinkedHashMap<>();
                        definition.statePresets().forEach((stateId, preset) ->
                                definitionAppearances.put(
                                        stateId, copyAppearance(preset.appearance())));
                        result.setStateAppearances(definitionAppearances);
                        var activePreset = definition.statePresets().get(object.activeStateId());
                        if (activePreset != null) {
                            result.getState().setTintColorRgb(
                                    activePreset.appearance().getTintColorRgb());
                        }
                    });
        }
        if (metadataSource != null) {
            result.setOwnerId(metadataSource.getOwnerId());
            result.setVisionInnerRadius(metadataSource.getVisionInnerRadius());
            result.setVisionOuterRadius(metadataSource.getVisionOuterRadius());
            result.setVisionEnabled(metadataSource.isVisionEnabled());
            result.setVisionOwnLightEnabled(metadataSource.isVisionOwnLightEnabled());
            if (metadataSource.getState() != null) {
                result.getState().setTintColorRgb(metadataSource.getState().getTintColorRgb());
            }
            result.setGlobalStateAppearance(copyAppearance(
                    metadataSource.getGlobalStateAppearance()));
            Map<String, VttTokenStateAppearance> stateAppearances = new LinkedHashMap<>(
                    result.getStateAppearances());
            metadataSource.getStateAppearances().forEach((stateId, appearance) ->
                    stateAppearances.put(stateId, copyAppearance(appearance)));
            result.setStateAppearances(stateAppearances);
            result.setAttachmentBinding(copyBinding(metadataSource.getAttachmentBinding()));
            VttSceneCollisionBox sourceBox = metadataSource.getCollisionBox();
            if (sourceBox != null) {
                result.setCollisionBox(new VttSceneCollisionBox(sourceBox.getOffsetX(),
                        sourceBox.getOffsetY(), sourceBox.getWidth(), sourceBox.getHeight()));
            }
        }
        return result;
    }

    private static VttTokenStateAppearance copyAppearance(VttTokenStateAppearance appearance) {
        return appearance == null ? null : appearance.copy();
    }

    private static VttAttachmentBinding copyBinding(VttAttachmentBinding source) {
        if (source == null || !source.isBound()) return null;
        VttAttachmentBinding copy = new VttAttachmentBinding();
        copy.setTargetObjectId(source.getTargetObjectId());
        copy.setFollowPosition(source.isFollowPosition());
        copy.setFollowRotation(source.isFollowRotation());
        copy.setFollowScale(source.isFollowScale());
        copy.setFlipOffset(source.isFlipOffset());
        copy.setParentStateId(source.getParentStateId());
        copy.setAnchor(source.getAnchor());
        copy.setOffsetX(source.getOffsetX());
        copy.setOffsetY(source.getOffsetY());
        copy.setRotationOffsetDegrees(source.getRotationOffsetDegrees());
        copy.setScaleMultiplierX(source.getScaleMultiplierX());
        copy.setScaleMultiplierY(source.getScaleMultiplierY());
        return copy;
    }
}
