package com.petrick.vtt.feature.tabletop;

import java.util.LinkedHashMap;
import java.util.Map;

/** Per-scene relationship between a placed attachment and a placed token. */
public final class VttAttachmentBinding {
    private String targetObjectId;
    private boolean followPosition = true;
    private boolean followRotation = true;
    private boolean followScale = true;
    /** Preserves an attachment's own orientation while the parent token flips. */
    private boolean flipOffset;
    /** Optional parent token state in which this attachment exists. Null means global. */
    private String parentStateId;
    /** CUSTOM preserves legacy free offsets; other values track a token bound. */
    private VttAttachmentAnchor anchor = VttAttachmentAnchor.CUSTOM;
    private double offsetX;
    private double offsetY;
    private double rotationOffsetDegrees;
    private double scaleMultiplierX = 1.0;
    private double scaleMultiplierY = 1.0;
    /** Per-axis scale inheritance; legacy JSON defaults to both enabled. */
    private Boolean inheritScaleX;
    private Boolean inheritScaleY;
    /** Prevent recapturing the corresponding local offset while editing. */
    private boolean lockOffsetX;
    private boolean lockOffsetY;
    private double minimumScale = 0.05;
    private double maximumScale = 16.0;
    /** Empty means independent/manual attachment state selection. */
    private Map<String, String> parentStateMappings = new LinkedHashMap<>();
    private String fallbackAttachmentStateId;

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
    public boolean isFlipOffset() { return flipOffset; }
    public void setFlipOffset(boolean flipOffset) { this.flipOffset = flipOffset; }
    public String getParentStateId() { return parentStateId; }
    public void setParentStateId(String value) {
        parentStateId = value == null || value.isBlank() ? null : value.trim();
    }
    public VttAttachmentAnchor getAnchor() {
        return anchor == null ? VttAttachmentAnchor.CUSTOM : anchor;
    }
    public void setAnchor(VttAttachmentAnchor value) {
        anchor = value == null ? VttAttachmentAnchor.CUSTOM : value;
    }
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
    public boolean isInheritScaleX() { return inheritScaleX == null || inheritScaleX; }
    public void setInheritScaleX(boolean value) { inheritScaleX = value; }
    public boolean isInheritScaleY() { return inheritScaleY == null || inheritScaleY; }
    public void setInheritScaleY(boolean value) { inheritScaleY = value; }
    public boolean isLockOffsetX() { return lockOffsetX; }
    public void setLockOffsetX(boolean value) { lockOffsetX = value; }
    public boolean isLockOffsetY() { return lockOffsetY; }
    public void setLockOffsetY(boolean value) { lockOffsetY = value; }
    public double getMinimumScale() {
        return validPositive(minimumScale, 0.05);
    }
    public void setMinimumScale(double value) {
        double normalized = Double.isFinite(value) ? Math.max(0.01, value) : 0.05;
        minimumScale = Math.min(normalized, getMaximumScale());
    }
    public double getMaximumScale() {
        return Math.max(getMinimumScaleRaw(), validPositive(maximumScale, 16.0));
    }
    public void setMaximumScale(double value) {
        double normalized = Double.isFinite(value) ? Math.max(0.01, value) : 16.0;
        maximumScale = Math.max(getMinimumScale(), normalized);
    }

    public double constrainScale(double value) {
        double sign = value < 0.0 ? -1.0 : 1.0;
        double magnitude = Math.max(getMinimumScale(), Math.min(getMaximumScale(), Math.abs(value)));
        return sign * magnitude;
    }
    public Map<String, String> getParentStateMappings() {
        if (parentStateMappings == null) parentStateMappings = new LinkedHashMap<>();
        return parentStateMappings;
    }
    public void setParentStateMappings(Map<String, String> mappings) {
        parentStateMappings = new LinkedHashMap<>();
        if (mappings != null) mappings.forEach((parentState, attachmentState) -> {
            if (parentState != null && !parentState.isBlank()
                    && attachmentState != null && !attachmentState.isBlank()) {
                parentStateMappings.put(parentState.trim(), attachmentState.trim());
            }
        });
    }
    public boolean isStateMappingEnabled() { return !getParentStateMappings().isEmpty(); }
    public String getFallbackAttachmentStateId() { return fallbackAttachmentStateId; }
    public void setFallbackAttachmentStateId(String value) {
        fallbackAttachmentStateId = value == null || value.isBlank() ? null : value.trim();
    }

    public boolean isBound() { return targetObjectId != null; }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double nonZero(double value) {
        return Double.isFinite(value) && Math.abs(value) > 0.0001 ? value : 1.0;
    }

    private double getMinimumScaleRaw() {
        return validPositive(minimumScale, 0.05);
    }

    private static double validPositive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }
}
