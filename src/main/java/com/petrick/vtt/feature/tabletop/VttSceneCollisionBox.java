package com.petrick.vtt.feature.tabletop;

/**
 * Caixa de colisao local de um token.
 *
 * As medidas usam as unidades-base do token, antes da escala de seu transform.
 * O deslocamento tambem e local e acompanha a rotacao do token.
 */
public final class VttSceneCollisionBox {
    private double offsetX;
    private double offsetY;
    private double width;
    private double height;

    public VttSceneCollisionBox() {}

    public VttSceneCollisionBox(double offsetX, double offsetY, double width, double height) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.width = Math.max(1.0, width);
        this.height = Math.max(1.0, height);
    }

    public double getOffsetX() { return offsetX; }

    public void setOffsetX(double offsetX) { this.offsetX = offsetX; }

    public double getOffsetY() { return offsetY; }

    public void setOffsetY(double offsetY) { this.offsetY = offsetY; }

    public double getWidth() { return width; }

    public void setWidth(double width) { this.width = Math.max(1.0, width); }

    public double getHeight() { return height; }

    public void setHeight(double height) { this.height = Math.max(1.0, height); }
}
