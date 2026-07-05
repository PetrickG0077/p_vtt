package com.petrick.vtt.core.render;

import com.petrick.vtt.core.math.Rectd;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.camera.Camera2D;
import com.petrick.vtt.feature.viewport.Viewport;

/**
 * Guarda o estado matemático atual da renderização do VTT.
 *
 * Esta classe não desenha nada.
 * Ela apenas conecta:
 *
 * - Camera2D
 * - Viewport
 *
 * Renderers futuros usarão esta classe para converter coordenadas
 * e descobrir qual parte do mundo está visível.
 */
public final class RenderState {

    private final Camera2D camera;

    private final Viewport viewport;

    public RenderState(Camera2D camera, Viewport viewport) {
        this.camera = camera;
        this.viewport = viewport;
    }

    public Camera2D getCamera() {
        return camera;
    }

    public Viewport getViewport() {
        return viewport;
    }

    public Rectd getViewportBounds() {
        return viewport.getBounds();
    }

    /**
     * Converte uma posição do mundo do VTT para a tela.
     */
    public Vec2d worldToScreen(Vec2d worldPosition) {
        return camera.worldToScreen(worldPosition, viewport.getBounds());
    }

    /**
     * Converte uma posição da tela para o mundo do VTT.
     */
    public Vec2d screenToWorld(Vec2d screenPosition) {
        return camera.screenToWorld(screenPosition, viewport.getBounds());
    }

    /**
     * Retorna qual área do mundo do VTT está visível no momento.
     *
     * Isso será muito importante para desenhar apenas o que aparece na tela,
     * como linhas do grid, tokens visíveis e imagens do mapa.
     */
    public Rectd getVisibleWorldBounds() {
        Rectd viewportBounds = viewport.getBounds();

        Vec2d topLeft = screenToWorld(new Vec2d(
                viewportBounds.left(),
                viewportBounds.top()
        ));

        Vec2d bottomRight = screenToWorld(new Vec2d(
                viewportBounds.right(),
                viewportBounds.bottom()
        ));

        return new Rectd(
                topLeft.x(),
                topLeft.y(),
                bottomRight.x() - topLeft.x(),
                bottomRight.y() - topLeft.y()
        );
    }

}