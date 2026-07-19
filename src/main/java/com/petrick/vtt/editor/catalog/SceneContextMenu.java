package com.petrick.vtt.editor.catalog;

/** Fixed-position context menu state for one scene row. */
public final class SceneContextMenu {
    private boolean open;
    private int x;
    private int y;
    private String sceneId;

    public boolean isOpen() { return open; }
    public int getX() { return x; }
    public int getY() { return y; }
    public String getSceneId() { return sceneId; }

    public void open(int x, int y, String sceneId) {
        if (sceneId == null || sceneId.isBlank()) {
            close();
            return;
        }
        this.open = true;
        this.x = x;
        this.y = y;
        this.sceneId = sceneId;
    }

    public void close() {
        open = false;
        x = 0;
        y = 0;
        sceneId = null;
    }
}
