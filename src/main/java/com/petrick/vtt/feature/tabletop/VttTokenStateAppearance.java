package com.petrick.vtt.feature.tabletop;

/** Scale, rotation and tint that can be global or overridden by a token state. */
public final class VttTokenStateAppearance {
    private double scaleX = 1.0;
    private double scaleY = 1.0;
    private double rotationDegrees;
    private int tintColorRgb = 0xFFFFFF;

    public VttTokenStateAppearance() {}

    public VttTokenStateAppearance(double scaleX, double scaleY,
                                   double rotationDegrees, int tintColorRgb) {
        setScaleX(scaleX);
        setScaleY(scaleY);
        setRotationDegrees(rotationDegrees);
        setTintColorRgb(tintColorRgb);
    }

    public double getScaleX() { return scaleX; }
    public void setScaleX(double value) { scaleX = validScale(value); }
    public double getScaleY() { return scaleY; }
    public void setScaleY(double value) { scaleY = validScale(value); }
    public double getRotationDegrees() { return rotationDegrees; }
    public void setRotationDegrees(double value) {
        rotationDegrees = Double.isFinite(value) ? value : 0.0;
    }
    public int getTintColorRgb() { return tintColorRgb & 0x00FFFFFF; }
    public void setTintColorRgb(int value) { tintColorRgb = value & 0x00FFFFFF; }

    public VttTokenStateAppearance copy() {
        return new VttTokenStateAppearance(scaleX, scaleY, rotationDegrees, tintColorRgb);
    }

    private static double validScale(double value) {
        return Double.isFinite(value) && value >= 0.0001 ? value : 1.0;
    }
}
