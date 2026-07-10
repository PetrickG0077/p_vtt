package com.petrick.vtt.feature.tabletop.persistence;

import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttSceneObject;
import com.petrick.vtt.feature.tabletop.VttSceneSize;
import com.petrick.vtt.feature.tabletop.VttSceneState;
import com.petrick.vtt.feature.tabletop.VttSceneTransform;

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

        targetScene.clearObjects();

        int layerIndex = 0;

        for (CanvasObject canvasObject : canvasScene.getObjects()) {
            VttSceneObject sceneObject = convertObject(
                    canvasObject,
                    layerIndex
            );

            targetScene.addObject(sceneObject);

            layerIndex++;
        }
    }

    private static VttSceneObject convertObject(
            CanvasObject canvasObject,
            int layerIndex
    ) {
        VttSceneObject sceneObject = new VttSceneObject();

        sceneObject.setId(canvasObject.id());
        sceneObject.setDisplayName(canvasObject.displayName());
        sceneObject.setSourceTokenDefinitionId(canvasObject.sourceTokenDefinitionId());
        sceneObject.setLayerIndex(layerIndex);

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

        sceneObject.setState(new VttSceneState(
                canvasObject.activeStateId(),
                canvasObject.visible(),
                canvasObject.flippedHorizontally()
        ));

        return sceneObject;
    }
}