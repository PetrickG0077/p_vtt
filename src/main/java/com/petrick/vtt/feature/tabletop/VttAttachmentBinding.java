package com.petrick.vtt.feature.tabletop;

/** Per-scene relationship between a placed attachment and a placed token. */
public final class VttAttachmentBinding {
    private String targetObjectId;
    private boolean followPosition = true;
    private boolean followRotation = true;
    private boolean followScale = true;
    private double offsetX;
    private double offsetY;
    private double rotationOffsetDegrees;
    private double scaleMultiplierX = 1.0;
    private double scaleMultiplierY = 1.0;

    public VttAttachmentBinding() {}

    public String getTargetObjectId() { return targetObjectId; }
    public void setTargetObjectId(String targetObjectId) {
        this.targetObjectId = targetObjectId == null || targetObjectId.isBlank()
                ? null : targetObjectId.trim();
    }
    public boolean isFollowPosition() { return followPosition; }
    public void setFollowPosition(boolean followPosition) { this.followPosition = followPosition; }
    public boolean isFollowRotation() { return followRotation; }
    public void setFollowRotation(boolean followRotation) { this.followRotation = followRotation; }
    public boolean isFollowScale() { return followScale; }
    public void setFollowScale(boolean followScale) { this.followScale = followScale; }
    public double getOffsetX() { return offsetX; }
    public void setOffsetX(double offsetX) { this.offsetX = finite(offsetX, 0.0); }
    public double getOffsetY() { return offsetY; }
    public void setOffsetY(double offsetY) { this.offsetY = finite(offsetY, 0.0); }
    public double getRotationOffsetDegrees() { return rotationOffsetDegrees; }
    public void setRotationOffsetDegrees(double value) {
        this.rotationOffsetDegrees = finite(value, 0.0);
    }
    public double getScaleMultiplierX() { return scaleMultiplierX; }
    public void setScaleMultiplierX(double value) {
        this.scaleMultiplierX = nonZero(value);
    }
    public double getScaleMultiplierY() { return scaleMultiplierY; }
    public void setScaleMultiplierY(double value) {
        this.scaleMultiplierY = nonZero(value);
    }

    public boolean isBound() { return targetObjectId != null; }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double nonZero(double value) {
        return Double.isFinite(value) && Math.abs(value) > 0.0001 ? value : 1.0;
    }
}
