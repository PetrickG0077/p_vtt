package com.petrick.vtt.feature.tabletop;

/** Camera position and zoom applied whenever this scene becomes active. */
public final class VttSceneCameraView {
    private static final double MAX_POSITION = 10_000_000.0;
    private static final double MIN_ZOOM = 0.1;
    private static final double MAX_ZOOM = 8.0;

    private double x;
    private double y;
    private double zoom = 1.0;

    public VttSceneCameraView() {
    }

    public VttSceneCameraView(double x, double y, double zoom) {
        this.x = x;
        this.y = y;
        this.zoom = zoom;
        normalize();
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZoom() {
        return zoom;
    }

    public void normalize() {
        x = finitePosition(x);
        y = finitePosition(y);
        zoom = !Double.isFinite(zoom)
                ? 1.0 : Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
    }

    public VttSceneCameraView copy() {
        return new VttSceneCameraView(x, y, zoom);
    }

    private static double finitePosition(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(-MAX_POSITION, Math.min(MAX_POSITION, value));
    }
}
