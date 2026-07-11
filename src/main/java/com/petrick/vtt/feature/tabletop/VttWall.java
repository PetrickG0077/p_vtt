package com.petrick.vtt.feature.tabletop;

/** Persistent rectangular wall area belonging to a VTT scene. */
public final class VttWall {
    private String id;
    private VttSceneTransform transform;
    private VttSceneSize size;
    private boolean visible = true;
    private boolean blocksVision = true;
    private boolean blocksMovement = true;

    /* Legacy line geometry, read only to migrate the first wall JSON format. */
    private Double startX;
    private Double startY;
    private Double endX;
    private Double endY;
    private Double thickness;

    public VttWall() {
        setId("wall");
    }

    public VttWall(String id, VttSceneTransform transform, VttSceneSize size) {
        setId(id);
        setTransform(transform);
        setSize(size);
    }

    public String getId() { return id; }

    public void setId(String id) {
        if (id == null || id.isBlank()) {
            this.id = "wall";
            return;
        }
        this.id = id.trim().toLowerCase().replaceAll("[^a-z0-9/_-]", "_");
    }

    public VttSceneTransform getTransform() {
        normalizeLegacyGeometry();
        return transform;
    }

    public void setTransform(VttSceneTransform transform) {
        this.transform = transform == null ? new VttSceneTransform() : transform;
    }

    public VttSceneSize getSize() {
        normalizeLegacyGeometry();
        return size;
    }

    public void setSize(VttSceneSize size) {
        this.size = size == null ? new VttSceneSize(64.0, 16.0) : size;
    }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public boolean isBlocksVision() { return blocksVision; }
    public void setBlocksVision(boolean blocksVision) { this.blocksVision = blocksVision; }
    public boolean isBlocksMovement() { return blocksMovement; }
    public void setBlocksMovement(boolean blocksMovement) { this.blocksMovement = blocksMovement; }

    /** Converts the old start/end/thickness representation once after loading. */
    public void normalizeLegacyGeometry() {
        if (transform != null && size != null) {
            clearLegacyGeometry();
            return;
        }

        if (startX != null && startY != null && endX != null && endY != null) {
            double dx = endX - startX;
            double dy = endY - startY;
            double length = Math.max(1.0, Math.sqrt(dx * dx + dy * dy));
            double legacyThickness = thickness != null && thickness > 0.0 ? thickness : 4.0;
            transform = new VttSceneTransform(
                    (startX + endX) / 2.0, (startY + endY) / 2.0,
                    1.0, 1.0, Math.toDegrees(Math.atan2(dy, dx))
            );
            size = new VttSceneSize(length, legacyThickness);
            clearLegacyGeometry();
        }
        if (transform == null) transform = new VttSceneTransform();
        if (size == null) size = new VttSceneSize(64.0, 16.0);
    }

    private void clearLegacyGeometry() {
        startX = null;
        startY = null;
        endX = null;
        endY = null;
        thickness = null;
    }
}
