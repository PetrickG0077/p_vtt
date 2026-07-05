package com.petrick.vtt.feature.camera;

import com.petrick.vtt.core.math.MathUtil;
import com.petrick.vtt.core.math.Rectd;
import com.petrick.vtt.core.math.Vec2d;

/**
 * Câmera 2D do VTT.
 *
 * Responsável por converter coordenadas entre:
 * - mundo do VTT
 * - tela/interface do Minecraft
 *
 * Esta classe não depende de Minecraft nem NeoForge.
 */
public final class Camera2D {

    private Vec2d position;

    private double zoom;

    private double minZoom;

    private double maxZoom;

    public Camera2D() {
        this.position = Vec2d.ZERO;
        this.zoom = 1.0;
        this.minZoom = 0.1;
        this.maxZoom = 8.0;
    }

    /**
     * Converte uma posição do mundo do VTT para a tela.
     */
    public Vec2d worldToScreen(Vec2d worldPosition, Rectd viewport) {
        Vec2d viewportCenter = viewport.center();

        double screenX = (worldPosition.x() - position.x()) * zoom + viewportCenter.x();
        double screenY = (worldPosition.y() - position.y()) * zoom + viewportCenter.y();

        return new Vec2d(screenX, screenY);
    }

    /**
     * Converte uma posição da tela para o mundo do VTT.
     */
    public Vec2d screenToWorld(Vec2d screenPosition, Rectd viewport) {
        Vec2d viewportCenter = viewport.center();

        double worldX = (screenPosition.x() - viewportCenter.x()) / zoom + position.x();
        double worldY = (screenPosition.y() - viewportCenter.y()) / zoom + position.y();

        return new Vec2d(worldX, worldY);
    }

    /**
     * Move a câmera no espaço do mundo.
     */
    public void move(Vec2d delta) {
        this.position = this.position.add(delta);
    }

    /**
     * Move a câmera usando deslocamento em pixels da tela.
     */
    public void moveByScreenDelta(Vec2d screenDelta) {
        Vec2d worldDelta = screenDelta.divide(zoom);
        this.position = this.position.subtract(worldDelta);
    }

    /**
     * Aplica zoom mantendo o ponto do mouse preso ao mesmo ponto do mundo.
     */
    public void zoomAtScreenPoint(double zoomFactor, Vec2d screenPoint, Rectd viewport) {
        Vec2d worldBeforeZoom = screenToWorld(screenPoint, viewport);

        setZoom(this.zoom * zoomFactor);

        Vec2d worldAfterZoom = screenToWorld(screenPoint, viewport);

        Vec2d correction = worldBeforeZoom.subtract(worldAfterZoom);

        this.position = this.position.add(correction);
    }

    public Vec2d getPosition() {
        return position;
    }

    public void setPosition(Vec2d position) {
        this.position = position;
    }

    public double getZoom() {
        return zoom;
    }

    public void setZoom(double zoom) {
        this.zoom = MathUtil.clamp(zoom, minZoom, maxZoom);
    }

    public double getMinZoom() {
        return minZoom;
    }

    public void setMinZoom(double minZoom) {
        this.minZoom = minZoom;
        this.zoom = MathUtil.clamp(this.zoom, this.minZoom, this.maxZoom);
    }

    public double getMaxZoom() {
        return maxZoom;
    }

    public void setMaxZoom(double maxZoom) {
        this.maxZoom = maxZoom;
        this.zoom = MathUtil.clamp(this.zoom, this.minZoom, this.maxZoom);
    }

}