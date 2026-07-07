package com.petrick.vtt.editor.placement;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.feature.selection.SelectionManager;
import com.petrick.vtt.feature.token.TokenDefinition;
import com.petrick.vtt.feature.token.TokenFactory;

/**
 * Serviço responsável por colocar tokens no canvas.
 *
 * Ele recebe uma TokenDefinition, cria uma instância CanvasObject,
 * adiciona na cena e seleciona o objeto criado.
 *
 * Isso deixa a VTTScreen mais limpa.
 */
public final class TokenPlacementService {

    private final CanvasScene scene;

    private final SelectionManager selectionManager;

    public TokenPlacementService(
            CanvasScene scene,
            SelectionManager selectionManager
    ) {
        if (scene == null) {
            throw new IllegalArgumentException("CanvasScene cannot be null");
        }

        if (selectionManager == null) {
            throw new IllegalArgumentException("SelectionManager cannot be null");
        }

        this.scene = scene;
        this.selectionManager = selectionManager;
    }

    public CanvasObject placeToken(
            TokenDefinition definition,
            Vec2d worldPosition
    ) {
        if (definition == null) {
            throw new IllegalArgumentException("TokenDefinition cannot be null");
        }

        if (worldPosition == null) {
            throw new IllegalArgumentException("World position cannot be null");
        }

        String objectId = scene.createUniqueObjectId("token");

        CanvasObject token = TokenFactory.createCanvasObject(
                definition,
                objectId,
                worldPosition
        );

        scene.addObject(token);

        selectionManager.selectOnly(objectId);

        return token;
    }
}