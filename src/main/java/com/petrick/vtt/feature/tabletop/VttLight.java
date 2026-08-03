package com.petrick.vtt.feature.tabletop;

/** Persistent scene light with point and directional spot variants. */
public final class VttLight {
    public static final double DEFAULT_INNER_RADIUS = 192.0;
    public static final double DEFAULT_OUTER_RADIUS = 320.0;
    public static final int DEFAULT_COLOR_RGB = 0xFFF1A8;
    public static final double MIN_INTENSITY = 0.1;
    public static final double MAX_INTENSITY = 2.0;
    public static final double DEFAULT_INTENSITY = 1.0;
    public static final double DEFAULT_DIRECTION_DEGREES = 0.0;
    public static final double DEFAULT_CONE_ANGLE_DEGREES = 60.0;
    public static final double DEFAULT_INNER_CONE_ANGLE_DEGREES = 40.0;
    public static final double MIN_CONE_ANGLE_DEGREES = 5.0;
    public static final double MAX_CONE_ANGLE_DEGREES = 179.0;

    private String id;
    private VttLightType type = VttLightType.POINT;
    private double x;
    private double y;
    private double innerRadius = DEFAULT_INNER_RADIUS;
    private double outerRadius = DEFAULT_OUTER_RADIUS;
    private int colorRgb = DEFAULT_COLOR_RGB;
    private double intensity = DEFAULT_INTENSITY;
    /** Local/world direction for SPOT lights; ready to become attachment-local later. */
    private double directionDegrees = DEFAULT_DIRECTION_DEGREES;
    private double coneAngleDegrees = DEFAULT_CONE_ANGLE_DEGREES;
    /** Full-strength angle; the light fades toward the outer cone after this boundary. */
    private double innerConeAngleDegrees = DEFAULT_INNER_CONE_ANGLE_DEGREES;
    /** False means reveal-only: the light removes darkness without tinting the scene. */
    private boolean tintEnabled = true;
    private boolean enabled = true;
    /** Optional placed attachment that owns this light's local transform. */
    private String attachedToObjectId;
    private double attachmentOffsetX;
    private double attachmentOffsetY;
    private double attachmentDirectionOffsetDegrees;

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
    public double getIntensity() { return intensity; }
    public void setIntensity(double intensity) {
        if (Double.isFinite(intensity)) {
            this.intensity = Math.max(MIN_INTENSITY, Math.min(MAX_INTENSITY, intensity));
        }
    }
    public boolean isTintEnabled() { return tintEnabled; }
    public void setTintEnabled(boolean tintEnabled) { this.tintEnabled = tintEnabled; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public double getDirectionDegrees() { return directionDegrees; }
    public void setDirectionDegrees(double directionDegrees) {
        if (!Double.isFinite(directionDegrees)) return;
        this.directionDegrees = ((directionDegrees % 360.0) + 360.0) % 360.0;
    }
    public double getConeAngleDegrees() { return coneAngleDegrees; }
    public void setConeAngleDegrees(double coneAngleDegrees) {
        if (Double.isFinite(coneAngleDegrees)) {
            this.coneAngleDegrees = Math.max(MIN_CONE_ANGLE_DEGREES,
                    Math.min(MAX_CONE_ANGLE_DEGREES, coneAngleDegrees));
            innerConeAngleDegrees = Math.min(innerConeAngleDegrees, this.coneAngleDegrees);
        }
    }
    public double getInnerConeAngleDegrees() { return innerConeAngleDegrees; }
    public void setInnerConeAngleDegrees(double innerConeAngleDegrees) {
        if (Double.isFinite(innerConeAngleDegrees)) {
            this.innerConeAngleDegrees = Math.max(0.1,
                    Math.min(getConeAngleDegrees(), innerConeAngleDegrees));
        }
    }
    public String getAttachedToObjectId() { return attachedToObjectId; }
    public void setAttachedToObjectId(String value) {
        attachedToObjectId = value == null || value.isBlank() ? null : value.trim();
    }
    public double getAttachmentOffsetX() { return attachmentOffsetX; }
    public void setAttachmentOffsetX(double value) {
        if (Double.isFinite(value)) attachmentOffsetX = value;
    }
    public double getAttachmentOffsetY() { return attachmentOffsetY; }
    public void setAttachmentOffsetY(double value) {
        if (Double.isFinite(value)) attachmentOffsetY = value;
    }
    public double getAttachmentDirectionOffsetDegrees() {
        return attachmentDirectionOffsetDegrees;
    }
    public void setAttachmentDirectionOffsetDegrees(double value) {
        if (Double.isFinite(value)) attachmentDirectionOffsetDegrees = value;
    }
    public boolean isAttached() { return attachedToObjectId != null; }

    public void normalize() {
        id = normalizeId(id);
        setType(type);
        setOuterRadius(outerRadius);
        setInnerRadius(innerRadius);
        setColorRgb(colorRgb);
        setIntensity(intensity);
        setDirectionDegrees(directionDegrees);
        setConeAngleDegrees(coneAngleDegrees);
        if (innerConeAngleDegrees <= 0.0) {
            innerConeAngleDegrees = Math.min(DEFAULT_INNER_CONE_ANGLE_DEGREES,
                    coneAngleDegrees);
        }
        setInnerConeAngleDegrees(innerConeAngleDegrees);
        setAttachedToObjectId(attachedToObjectId);
    }

    private String normalizeId(String value) {
        if (value == null || value.isBlank()) return "light_1";
        return value.trim().toLowerCase().replaceAll("[^a-z0-9/_-]", "_");
    }
}
