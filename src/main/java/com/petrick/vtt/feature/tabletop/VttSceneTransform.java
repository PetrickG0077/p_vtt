package com.petrick.vtt.feature.tabletop;

/**
 * Bloco de transformação salvo no JSON da cena.
 */
public final class VttSceneTransform {

    private double x;

    private double y;

    private double scaleX = 1.0;

    private double scaleY = 1.0;

    private double rotationDegrees;

    public VttSceneTransform() {}

    public VttSceneTransform(
            double x,
            double y,
            double scaleX,
            double scaleY,
            double rotationDegrees
    ) {
        this.x = x;
        this.y = y;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.rotationDegrees = rotationDegrees;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getScaleX() {
        return scaleX;
    }

    public void setScaleX(double scaleX) {
        this.scaleX = scaleX;
    }

    public double getScaleY() {
        return scaleY;
    }

    public void setScaleY(double scaleY) {
        this.scaleY = scaleY;
    }

    public double getRotationDegrees() {
        return rotationDegrees;
    }

    public void setRotationDegrees(double rotationDegrees) {
        this.rotationDegrees = rotationDegrees;
    }
}