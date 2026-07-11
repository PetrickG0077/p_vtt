package com.petrick.vtt.feature.tabletop;

/** Persistent rectangular door area, optionally attached to a wall. */
public final class VttDoor {
    private String id;
    private String wallId;
    private VttSceneTransform transform;
    private VttSceneSize size;
    private boolean open;
    private boolean locked;
    private boolean visible = true;
    private boolean blocksVisionWhenClosed = true;
    private boolean blocksMovementWhenClosed = true;

    public VttDoor() {
        this("door", null, new VttSceneTransform(), new VttSceneSize(64.0, 12.0));
    }

    public VttDoor(
            String id,
            String wallId,
            VttSceneTransform transform,
            VttSceneSize size
    ) {
        setId(id);
        setWallId(wallId);
        setTransform(transform);
        setSize(size);
    }

    public String getId() { return id; }

    public void setId(String id) {
        if (id == null || id.isBlank()) {
            this.id = "door";
            return;
        }
        this.id = id.trim().toLowerCase().replaceAll("[^a-z0-9/_-]", "_");
    }

    public String getWallId() { return wallId; }

    public void setWallId(String wallId) {
        this.wallId = wallId == null || wallId.isBlank() ? null : wallId.trim();
    }

    public VttSceneTransform getTransform() {
        if (transform == null) transform = new VttSceneTransform();
        return transform;
    }

    public void setTransform(VttSceneTransform transform) {
        this.transform = transform == null ? new VttSceneTransform() : transform;
    }

    public VttSceneSize getSize() {
        if (size == null) size = new VttSceneSize(64.0, 12.0);
        return size;
    }

    public void setSize(VttSceneSize size) {
        this.size = size == null ? new VttSceneSize(64.0, 12.0) : size;
    }

    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public boolean isBlocksVisionWhenClosed() { return blocksVisionWhenClosed; }
    public void setBlocksVisionWhenClosed(boolean value) { blocksVisionWhenClosed = value; }
    public boolean isBlocksMovementWhenClosed() { return blocksMovementWhenClosed; }
    public void setBlocksMovementWhenClosed(boolean value) { blocksMovementWhenClosed = value; }

    public boolean blocksVision() {
        return !open && blocksVisionWhenClosed;
    }

    public boolean blocksMovement() {
        return !open && blocksMovementWhenClosed;
    }
}
