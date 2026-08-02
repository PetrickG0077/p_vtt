package com.petrick.vtt.feature.tabletop;

/** Persistent scene light. Spot-specific direction/cone fields will be added later. */
public final class VttLight {
    public static final double DEFAULT_INNER_RADIUS = 192.0;
    public static final double DEFAULT_OUTER_RADIUS = 320.0;
    public static final int DEFAULT_COLOR_RGB = 0xFFF1A8;

    private String id;
    private VttLightType type = VttLightType.POINT;
    private double x;
    private double y;
    private double innerRadius = DEFAULT_INNER_RADIUS;
    private double outerRadius = DEFAULT_OUTER_RADIUS;
    private int colorRgb = DEFAULT_COLOR_RGB;
    private boolean enabled = true;

    public VttLight() { this("light_1", 0.0, 0.0); }

    public VttLight(String id, double x, double y) {
        this.id = normalizeId(id);
        this.x = x;
        this.y = y;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = normalizeId(id); }
    public VttLightType getType() { return type == null ? VttLightType.POINT : type; }
    public void setType(VttLightType type) { this.type = type == null ? VttLightType.POINT : type; }
    public double getX() { return x; }
    public void setX(double x) { if (Double.isFinite(x)) this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { if (Double.isFinite(y)) this.y = y; }
    public double getInnerRadius() { return innerRadius; }
    public void setInnerRadius(double value) {
        if (Double.isFinite(value)) innerRadius = Math.max(0.0, Math.min(value, outerRadius));
    }
    public double getOuterRadius() { return outerRadius; }
    public void setOuterRadius(double value) {
        if (Double.isFinite(value)) outerRadius = Math.max(1.0, value);
        innerRadius = Math.min(innerRadius, outerRadius);
    }
    public int getColorRgb() { return colorRgb; }
    public void setColorRgb(int colorRgb) { this.colorRgb = colorRgb & 0x00FFFFFF; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public void normalize() {
        id = normalizeId(id);
        setType(type);
        setOuterRadius(outerRadius);
        setInnerRadius(innerRadius);
        setColorRgb(colorRgb);
    }

    private String normalizeId(String value) {
        if (value == null || value.isBlank()) return "light_1";
        return value.trim().toLowerCase().replaceAll("[^a-z0-9/_-]", "_");
    }
}
