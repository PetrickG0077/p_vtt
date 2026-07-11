package com.petrick.vtt.feature.tabletop;

/** Persistent rectangular area used by fog reveal and hide operations. */
public final class VttFogArea {
    private String id;
    private VttSceneTransform transform;
    private VttSceneSize size;
    private boolean visible = true;

    public VttFogArea() {
        this("fog_area", new VttSceneTransform(), new VttSceneSize(64.0, 64.0));
    }

    public VttFogArea(String id, VttSceneTransform transform, VttSceneSize size) {
        setId(id);
        setTransform(transform);
        setSize(size);
    }

    public String getId() { return id; }

    public void setId(String id) {
        if (id == null || id.isBlank()) {
            this.id = "fog_area";
            return;
        }
        this.id = id.trim().toLowerCase().replaceAll("[^a-z0-9/_-]", "_");
    }

    public VttSceneTransform getTransform() {
        if (transform == null) transform = new VttSceneTransform();
        return transform;
    }

    public void setTransform(VttSceneTransform transform) {
        this.transform = transform == null ? new VttSceneTransform() : transform;
    }

    public VttSceneSize getSize() {
        if (size == null) size = new VttSceneSize(64.0, 64.0);
        return size;
    }

    public void setSize(VttSceneSize size) {
        this.size = size == null ? new VttSceneSize(64.0, 64.0) : size;
    }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
}
