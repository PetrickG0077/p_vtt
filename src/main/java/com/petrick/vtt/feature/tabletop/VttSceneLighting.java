package com.petrick.vtt.feature.tabletop;

/** Persistent scene-wide settings shared by dynamic vision and future light sources. */
public final class VttSceneLighting {
    public static final int DEFAULT_DARKNESS_COLOR_RGB = 0x08080C;
    public static final int DEFAULT_VISION_RAY_COUNT = 256;
    public static final int MIN_VISION_RAY_COUNT = 8;
    public static final int MAX_VISION_RAY_COUNT = DEFAULT_VISION_RAY_COUNT * 2;
    public static final int VISION_RAY_COUNT_STEP = 8;
    public static final int DEFAULT_VISION_PIXEL_SIZE = 4;
    public static final int MIN_VISION_PIXEL_SIZE = 1;
    public static final int MAX_VISION_PIXEL_SIZE = 16;

    private int darknessColorRgb = DEFAULT_DARKNESS_COLOR_RGB;
    private int visionRayCount = DEFAULT_VISION_RAY_COUNT;
    private int visionPixelSize = DEFAULT_VISION_PIXEL_SIZE;

    public int getDarknessColorRgb() { return darknessColorRgb; }

    public void setDarknessColorRgb(int darknessColorRgb) {
        this.darknessColorRgb = darknessColorRgb & 0x00FFFFFF;
    }

    public int getVisionRayCount() { return visionRayCount; }

    public void setVisionRayCount(int visionRayCount) {
        int clamped = Math.max(MIN_VISION_RAY_COUNT,
                Math.min(MAX_VISION_RAY_COUNT, visionRayCount));
        this.visionRayCount = Math.max(MIN_VISION_RAY_COUNT,
                Math.round((float) clamped / VISION_RAY_COUNT_STEP) * VISION_RAY_COUNT_STEP);
    }

    public int getVisionPixelSize() { return visionPixelSize; }

    public void setVisionPixelSize(int visionPixelSize) {
        this.visionPixelSize = Math.max(MIN_VISION_PIXEL_SIZE,
                Math.min(MAX_VISION_PIXEL_SIZE, visionPixelSize));
    }

    public void normalize() {
        setDarknessColorRgb(darknessColorRgb);
        setVisionRayCount(visionRayCount);
        setVisionPixelSize(visionPixelSize);
    }
}
