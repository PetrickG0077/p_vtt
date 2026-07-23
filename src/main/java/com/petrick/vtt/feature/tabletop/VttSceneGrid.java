package com.petrick.vtt.feature.tabletop;

/** Persistent scene grid appearance and world-scale settings. */
public final class VttSceneGrid {
    public static final int DEFAULT_COLOR_RGB = 0xFFFFFF;
    public static final double DEFAULT_OPACITY = 0.2;
    public static final double DEFAULT_GRID_SIZE = 64.0;
    public static final int DEFAULT_LINE_WIDTH = 1;

    private int colorRgb = DEFAULT_COLOR_RGB;
    private double opacity = DEFAULT_OPACITY;
    private double gridSize = DEFAULT_GRID_SIZE;
    private int lineWidth = DEFAULT_LINE_WIDTH;
    private boolean topLayer;

    public VttSceneGrid() {
    }

    public int getColorRgb() {
        normalize();
        return colorRgb;
    }

    public void setColorRgb(int colorRgb) {
        this.colorRgb = colorRgb & 0x00FFFFFF;
    }

    public double getOpacity() {
        normalize();
        return opacity;
    }

    public void setOpacity(double opacity) {
        this.opacity = Double.isFinite(opacity)
                ? Math.max(0.0, Math.min(1.0, opacity))
                : DEFAULT_OPACITY;
    }

    /** Number of world pixels represented by one meter. */
    public double getGridSize() {
        normalize();
        return gridSize;
    }

    public void setGridSize(double gridSize) {
        this.gridSize = Double.isFinite(gridSize)
                ? Math.max(16.0, Math.min(512.0, gridSize))
                : DEFAULT_GRID_SIZE;
    }

    public int getLineWidth() {
        normalize();
        return lineWidth;
    }

    public void setLineWidth(int lineWidth) {
        this.lineWidth = Math.max(1, Math.min(8, lineWidth));
    }

    public boolean isTopLayer() {
        return topLayer;
    }

    public void setTopLayer(boolean topLayer) {
        this.topLayer = topLayer;
    }

    public void normalize() {
        colorRgb &= 0x00FFFFFF;
        if (!Double.isFinite(opacity)) opacity = DEFAULT_OPACITY;
        opacity = Math.max(0.0, Math.min(1.0, opacity));
        if (!Double.isFinite(gridSize) || gridSize < 16.0 || gridSize > 512.0) {
            gridSize = DEFAULT_GRID_SIZE;
        }
        lineWidth = Math.max(1, Math.min(8, lineWidth));
    }
}
