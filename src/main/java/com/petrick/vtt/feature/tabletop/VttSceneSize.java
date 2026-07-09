package com.petrick.vtt.feature.tabletop;

/**
 * Bloco de tamanho salvo no JSON da cena.
 */
public final class VttSceneSize {

    private double width = 96.0;

    private double height = 96.0;

    public VttSceneSize() {}

    public VttSceneSize(double width, double height) {
        this.width = width;
        this.height = height;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }
}