package com.petrick.vtt.feature.tabletop.persistence;

import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttSceneObject;
import com.petrick.vtt.feature.tabletop.VttSceneSize;
import com.petrick.vtt.feature.tabletop.VttSceneState;
import com.petrick.vtt.feature.tabletop.VttSceneTransform;
import com.petrick.vtt.feature.tabletop.VttSceneCollisionBox;

import java.util.HashMap;
import java.util.Map;

/**
 * Converte o estado atual do CanvasScene para o modelo persistente VttScene.
 *
 * Nesta primeira versão salvamos apenas objetos/tokens colocados na mesa.
 *
 * Futuramente este mapper também poderá salvar:
 * - background/mapa;
 * - walls;
 * - doors;
 * - fog of war;
 * - lights;
 * - permissões.
 */
public final class CanvasSceneToVttSceneMapper {

    private CanvasSceneToVttSceneMapper() {}

    public static void copyCanvasObjectsToScene(
            CanvasScene canvasScene,
            VttScene targetScene
    ) {
        if (canvasScene == null || targetScene == null) {
            return;
        }

        Map<String, Double> existingVisionRanges = new HashMap<>();
        Map<String, Double> existingVisionInnerRadii = new HashMap<>();
        Map<String, Boolean> existingVisionEnabled = new HashMap<>();
        Map<String, Boolean> existingVisionOwnLightEnabled = new HashMap<>();
        Map<String, Integer> existingTintColors = new HashMap<>();
        Map<String, String> existingOwnerIds = new HashMap<>();
        Map<String, VttSceneCollisionBox> existingCollisionBoxes = new HashMap<>();
        for (VttSceneObject existingObject : targetScene.getObjects()) {
            if (existingObject != null && existingObject.getId() != null) {
                existingVisionRanges.put(existingObject.getId(), existingObject.getVisionRange());
                existingVisionInnerRadii.put(existingObject.getId(), existingObject.getVisionInnerRadius());
                existingVisionEnabled.put(existingObject.getId(), existingObject.isVisionEnabled());
                existingVisionOwnLightEnabled.put(existingObject.getId(),
                        existingObject.isVisionOwnLightEnabled());
                existingTintColors.put(existingObject.getId(), existingObject.getState() == null
                        ? 0xFFFFFF : existingObject.getState().getTintColorRgb());
                if (existingObject.getOwnerId() != null) {
                    existingOwnerIds.put(existingObject.getId(), existingObject.getOwnerId());
                }
                if (existingObject.getCollisionBox() != null) {
                    existingCollisionBoxes.put(existingObject.getId(), existingObject.getCollisionBox());
                }
            }
        }

        targetScene.clearObjects();

        int layerIndex = 0;

        for (CanvasObject canvasObject : canvasScene.getObjects()) {
            String metadataSourceId = resolveMetadataSourceId(
                    canvasObject.id(), existingVisionRanges);
            VttSceneObject sceneObject = convertObject(
                    canvasObject,
                    layerIndex,
                    existingVisionRanges.getOrDefault(metadataSourceId, 0.0),
                    existingVisionInnerRadii.getOrDefault(metadataSourceId, 256.0),
                    existingVisionEnabled.getOrDefault(metadataSourceId, true),
                    existingVisionOwnLightEnabled.getOrDefault(metadataSourceId, true),
                    existingTintColors.getOrDefault(metadataSourceId, 0xFFFFFF),
                    existingOwnerIds.get(metadataSourceId)
            );
            VttSceneCollisionBox collisionBox = existingCollisionBoxes.get(metadataSourceId);
            if (collisionBox != null) {
                sceneObject.setCollisionBox(new VttSceneCollisionBox(collisionBox.getOffsetX(),
                        collisionBox.getOffsetY(), collisionBox.getWidth(), collisionBox.getHeight()));
            }

            targetScene.addObject(sceneObject);

            layerIndex++;
        }
    }

    private static String resolveMetadataSourceId(
            String objectId,
            Map<String, Double> existingObjects
    ) {
        if (existingObjects.containsKey(objectId)) return objectId;
        return existingObjects.keySet().stream()
                .filter(existingId -> objectId.startsWith(existingId + "_copy"))
                .max(java.util.Comparator.comparingInt(String::length))
                .orElse(objectId);
    }

    private static VttSceneObject convertObject(
            CanvasObject canvasObject,
            int layerIndex,
            double visionRange,
            double visionInnerRadius,
            boolean visionEnabled,
            boolean visionOwnLightEnabled,
            int tintColorRgb,
            String ownerId
    ) {
        VttSceneObject sceneObject = new VttSceneObject();

        sceneObject.setId(canvasObject.id());
        sceneObject.setDisplayName(canvasObject.displayName());
        sceneObject.setSourceTokenDefinitionId(canvasObject.sourceTokenDefinitionId());
        sceneObject.setLayerIndex(layerIndex);
        sceneObject.setVisionRange(visionRange);
        sceneObject.setVisionInnerRadius(visionInnerRadius);
        sceneObject.setVisionEnabled(visionEnabled);
        sceneObject.setVisionOwnLightEnabled(visionOwnLightEnabled);
        sceneObject.setOwnerId(ownerId);

        sceneObject.setTransform(new VttSceneTransform(
                canvasObject.transform().position().x(),
                canvasObject.transform().position().y(),
                canvasObject.transform().scale().x(),
                canvasObject.transform().scale().y(),
                canvasObject.transform().rotationDegrees()
        ));

        sceneObject.setSize(new VttSceneSize(
                canvasObject.size().x(),
                canvasObject.size().y()
        ));

        VttSceneState state = new VttSceneState(
                canvasObject.activeStateId(),
                canvasObject.visible(),
                canvasObject.flippedHorizontally()
        );
        state.setTintColorRgb(tintColorRgb);
        sceneObject.setState(state);

        return sceneObject;
    }
}
