package com.petrick.vtt.feature.viewport;

import com.petrick.vtt.core.math.Rectd;
import com.petrick.vtt.core.math.Vec2d;

/**
 * Representa a área visível do canvas do VTT dentro da interface.
 *
 * Por enquanto, o viewport normalmente ocupará a tela inteira.
 * No futuro, ele poderá ocupar apenas parte da tela caso existam
 * barras laterais, painéis, janelas ou múltiplas áreas de renderização.
 */
public final class Viewport {

    private Rectd bounds;

    public Viewport(double x, double y, double width, double height) {
        this.bounds = new Rectd(x, y, width, height);
    }

    public static Viewport fullScreen(double width, double height) {
        return new Viewport(0, 0, width, height);
    }

    public Rectd getBounds() {
        return bounds;
    }

    public void setBounds(Rectd bounds) {
        this.bounds = bounds;
    }

    public void resize(double width, double height) {
        this.bounds = new Rectd(bounds.x(), bounds.y(), width, height);
    }

    public void moveTo(double x, double y) {
        this.bounds = new Rectd(x, y, bounds.width(), bounds.height());
    }

    public double getX() {
        return bounds.x();
    }

    public double getY() {
        return bounds.y();
    }

    public double getWidth() {
        return bounds.width();
    }

    public double getHeight() {
        return bounds.height();
    }

    public Vec2d getCenter() {
        return bounds.center();
    }

    public boolean contains(Vec2d screenPosition) {
        return bounds.contains(screenPosition);
    }

}