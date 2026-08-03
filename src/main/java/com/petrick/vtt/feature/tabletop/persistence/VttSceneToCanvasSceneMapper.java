package com.petrick.vtt.feature.tabletop.persistence;

import com.petrick.vtt.VTT;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.core.transform.Transform2D;
import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttSceneObject;
import com.petrick.vtt.feature.tabletop.VttSceneSize;
import com.petrick.vtt.feature.tabletop.VttSceneState;
import com.petrick.vtt.feature.tabletop.VttSceneTransform;
import com.petrick.vtt.feature.token.TokenDefinition;
import com.petrick.vtt.feature.token.TokenDefinitionRegistry;
import com.petrick.vtt.feature.attachment.AttachmentDefinition;
import com.petrick.vtt.feature.attachment.AttachmentDefinitionRegistry;
import com.petrick.vtt.feature.attachment.AttachmentFactory;
import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnailRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Converte uma VttScene persistida em JSON de volta para o CanvasScene em memória.
 *
 * Nesta primeira versão:
 * - carrega apenas tokens/objetos salvos;
 * - ignora objetos cujo TokenDefinition não existe mais;
 * - respeita layerIndex para restaurar a ordem visual.
 */
public final class VttSceneToCanvasSceneMapper {

    private VttSceneToCanvasSceneMapper() {}

    public static void copySceneObjectsToCanvas(
            VttScene sourceScene,
            CanvasScene targetCanvasScene,
            TokenDefinitionRegistry tokenDefinitionRegistry
    ) {
        copySceneObjectsToCanvas(sourceScene, targetCanvasScene,
                tokenDefinitionRegistry, null, null, null);
    }

    public static void copySceneObjectsToCanvas(
            VttScene sourceScene,
            CanvasScene targetCanvasScene,
            TokenDefinitionRegistry tokenDefinitionRegistry,
            AttachmentDefinitionRegistry attachmentDefinitionRegistry,
            AssetRegistry assetRegistry,
            AssetThumbnailRegistry thumbnails
    ) {
        if (sourceScene == null || targetCanvasScene == null || tokenDefinitionRegistry == null) {
            return;
        }

        List<CanvasObject> canvasObjects = new ArrayList<>();

        List<VttSceneObject> sortedSceneObjects = new ArrayList<>(sourceScene.getObjects());

        sortedSceneObjects.sort(
                Comparator.comparingInt(VttSceneObject::getLayerIndex)
        );

        for (VttSceneObject sceneObject : sortedSceneObjects) {
            CanvasObject canvasObject = convertObject(
                    sceneObject,
                    tokenDefinitionRegistry,
                    attachmentDefinitionRegistry,
                    assetRegistry,
                    thumbnails
            );

            if (canvasObject != null) {
                canvasObjects.add(canvasObject);
            }
        }

        targetCanvasScene.replaceAllObjects(canvasObjects);
    }

    public static CanvasObject convertObject(
            VttSceneObject sceneObject,
            TokenDefinitionRegistry tokenDefinitionRegistry
    ) {
        return convertObject(sceneObject, tokenDefinitionRegistry, null, null, null);
    }

    public static CanvasObject convertObject(
            VttSceneObject sceneObject,
            TokenDefinitionRegistry tokenDefinitionRegistry,
            AttachmentDefinitionRegistry attachmentDefinitionRegistry,
            AssetRegistry assetRegistry,
            AssetThumbnailRegistry thumbnails
    ) {
        if (sceneObject == null) {
            return null;
        }

        String attachmentDefinitionId = sceneObject.getSourceAttachmentDefinitionId();
        if (attachmentDefinitionId != null && !attachmentDefinitionId.isBlank()) {
            AttachmentDefinition definition = attachmentDefinitionRegistry == null
                    ? null : attachmentDefinitionRegistry.findById(attachmentDefinitionId)
                    .orElse(null);
            if (definition == null || assetRegistry == null || thumbnails == null) {
                VTT.LOGGER.warn(
                        "Skipped VTT scene attachment because its definition was not found: object={}, attachmentDefinition={}",
                        sceneObject.getId(), attachmentDefinitionId);
                return null;
            }
            VttSceneTransform attachmentTransform = sceneObject.getTransform();
            CanvasObject attachment = AttachmentFactory.createCanvasObject(
                    definition, resolveObjectId(sceneObject),
                    attachmentTransform == null ? new Vec2d(0.0, 0.0)
                            : new Vec2d(attachmentTransform.getX(),
                            attachmentTransform.getY()),
                    assetRegistry, thumbnails);
            return new CanvasObject(
                    attachment.id(),
                    sceneObject.getDisplayName() == null || sceneObject.getDisplayName().isBlank()
                            ? definition.displayName() : sceneObject.getDisplayName(),
                    null,
                    createTransform(sceneObject.getTransform()),
                    createAttachmentSize(sceneObject.getSize(), definition),
                    attachment.states(), attachment.activeStateId(),
                    resolveVisible(sceneObject.getState()),
                    resolveFlippedHorizontally(sceneObject.getState()),
                    definition.id());
        }

        String tokenDefinitionId = sceneObject.getSourceTokenDefinitionId();

        if (tokenDefinitionId == null || tokenDefinitionId.isBlank()) {
            VTT.LOGGER.warn(
                    "Skipped VTT scene object without token definition: {}",
                    sceneObject.getId()
            );
            return null;
        }

        TokenDefinition definition = tokenDefinitionRegistry.findById(tokenDefinitionId)
                .orElse(null);

        if (definition == null) {
            VTT.LOGGER.warn(
                    "Skipped VTT scene object because token definition was not found: object={}, tokenDefinition={}",
                    sceneObject.getId(),
                    tokenDefinitionId
            );
            return null;
        }

        VttSceneTransform transform = sceneObject.getTransform();
        VttSceneSize size = sceneObject.getSize();
        VttSceneState state = sceneObject.getState();

        String activeStateId = resolveActiveStateId(
                state,
                definition
        );

        return new CanvasObject(
                resolveObjectId(sceneObject),
                resolveDisplayName(sceneObject, definition),
                definition.id(),
                createTransform(transform),
                createSize(size, definition),
                definition.states(),
                activeStateId,
                resolveVisible(state),
                resolveFlippedHorizontally(state)
        );
    }

    private static String resolveObjectId(VttSceneObject sceneObject) {
        if (sceneObject.getId() == null || sceneObject.getId().isBlank()) {
            return "loaded_object_" + System.nanoTime();
        }

        return sceneObject.getId();
    }

    private static String resolveDisplayName(
            VttSceneObject sceneObject,
            TokenDefinition definition
    ) {
        if (sceneObject.getDisplayName() == null || sceneObject.getDisplayName().isBlank()) {
            return definition.displayName();
        }

        return sceneObject.getDisplayName();
    }

    private static Transform2D createTransform(VttSceneTransform transform) {
        if (transform == null) {
            return Transform2D.identity();
        }

        return new Transform2D(
                new Vec2d(
                        transform.getX(),
                        transform.getY()
                ),
                transform.getRotationDegrees(),
                new Vec2d(
                        normalizeScale(transform.getScaleX()),
                        normalizeScale(transform.getScaleY())
                )
        );
    }

    private static Vec2d createSize(
            VttSceneSize size,
            TokenDefinition definition
    ) {
        if (size == null) {
            return definition.defaultSize();
        }

        double width = size.getWidth() > 0.0
                ? size.getWidth()
                : definition.defaultSize().x();

        double height = size.getHeight() > 0.0
                ? size.getHeight()
                : definition.defaultSize().y();

        return new Vec2d(width, height);
    }

    private static Vec2d createAttachmentSize(
            VttSceneSize size, AttachmentDefinition definition
    ) {
        if (size == null) {
            return new Vec2d(definition.defaultWidth(), definition.defaultHeight());
        }
        return new Vec2d(
                size.getWidth() > 0.0 ? size.getWidth() : definition.defaultWidth(),
                size.getHeight() > 0.0 ? size.getHeight() : definition.defaultHeight());
    }

    private static String resolveActiveStateId(
            VttSceneState state,
            TokenDefinition definition
    ) {
        if (state == null) {
            return definition.defaultStateId();
        }

        String activeStateId = state.getActiveStateId();

        if (activeStateId == null || activeStateId.isBlank()) {
            return definition.defaultStateId();
        }

        if (!definition.states().containsKey(activeStateId)) {
            return definition.defaultStateId();
        }

        return activeStateId;
    }

    private static boolean resolveVisible(VttSceneState state) {
        if (state == null) {
            return true;
        }

        return state.isVisible();
    }

    private static boolean resolveFlippedHorizontally(VttSceneState state) {
        if (state == null) {
            return false;
        }

        return state.isFlippedHorizontally();
    }

    private static double normalizeScale(double value) {
        if (Math.abs(value) <= 0.0001) {
            return 1.0;
        }

        return value;
    }
}
