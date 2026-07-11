package com.petrick.vtt.feature.tabletop;

/** Persistent wall segment belonging to a VTT scene. */
public final class VttWall {
    private String id;
    private double startX;
    private double startY;
    private double endX;
    private double endY;
    private double thickness = 4.0;
    private boolean visible = true;
    private boolean blocksVision = true;
    private boolean blocksMovement = true;

    public VttWall() {
        this("wall", 0.0, 0.0, 64.0, 0.0);
    }

    public VttWall(String id, double startX, double startY, double endX, double endY) {
        setId(id);
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
    }

    public String getId() { return id; }

    public void setId(String id) {
        if (id == null || id.isBlank()) {
            this.id = "wall";
            return;
        }
        this.id = id.trim().toLowerCase().replaceAll("[^a-z0-9/_-]", "_");
    }

    public double getStartX() { return startX; }
    public void setStartX(double startX) { this.startX = startX; }
    public double getStartY() { return startY; }
    public void setStartY(double startY) { this.startY = startY; }
    public double getEndX() { return endX; }
    public void setEndX(double endX) { this.endX = endX; }
    public double getEndY() { return endY; }
    public void setEndY(double endY) { this.endY = endY; }
    public double getThickness() { return thickness; }

    public void setThickness(double thickness) {
        this.thickness = thickness > 0.0 ? thickness : 4.0;
    }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public boolean isBlocksVision() { return blocksVision; }
    public void setBlocksVision(boolean blocksVision) { this.blocksVision = blocksVision; }
    public boolean isBlocksMovement() { return blocksMovement; }
    public void setBlocksMovement(boolean blocksMovement) { this.blocksMovement = blocksMovement; }
}
