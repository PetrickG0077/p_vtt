package com.petrick.vtt.feature.tabletop;

/** Persistent transform for the scene background, independent from canvas objects. */
public final class VttSceneBackgroundTransform {
    private static final double MIN_SCALE = 0.01;
    private static final double MAX_SCALE = 1_000.0;
    private static final double MAX_POSITION = 10_000_000.0;

    private double x;
    private double y;
    private double scaleX = 1.0;
    private double scaleY = 1.0;

    public VttSceneBackgroundTransform() {
    }

    public VttSceneBackgroundTransform(double x, double y, double scaleX, double scaleY) {
        this.x = x;
        this.y = y;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        normalize();
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = finitePosition(x);
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = finitePosition(y);
    }

    public double getScaleX() {
        return scaleX;
    }

    public void setScaleX(double scaleX) {
        this.scaleX = finiteScale(scaleX);
    }

    public double getScaleY() {
        return scaleY;
    }

    public void setScaleY(double scaleY) {
        this.scaleY = finiteScale(scaleY);
    }

    public void reset() {
        x = 0.0;
        y = 0.0;
        scaleX = 1.0;
        scaleY = 1.0;
    }

    public void normalize() {
        x = finitePosition(x);
        y = finitePosition(y);
        scaleX = finiteScale(scaleX);
        scaleY = finiteScale(scaleY);
    }

    public VttSceneBackgroundTransform copy() {
        return new VttSceneBackgroundTransform(x, y, scaleX, scaleY);
    }

    private static double finitePosition(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(-MAX_POSITION, Math.min(MAX_POSITION, value));
    }

    private static double finiteScale(double value) {
        if (!Double.isFinite(value)) return 1.0;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, Math.abs(value)));
    }
}
