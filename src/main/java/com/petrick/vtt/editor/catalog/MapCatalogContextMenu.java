package com.petrick.vtt.editor.catalog;

import com.petrick.vtt.feature.map.MapDefinition;

/** Stable state for the Map Catalog right-click menu. */
public final class MapCatalogContextMenu {
    private boolean open;
    private int x;
    private int y;
    private MapDefinition mapDefinition;

    public boolean isOpen() { return open; }
    public int getX() { return x; }
    public int getY() { return y; }
    public MapDefinition getMapDefinition() { return mapDefinition; }

    public void open(int x, int y, MapDefinition mapDefinition) {
        if (mapDefinition == null) {
            close();
            return;
        }
        this.open = true;
        this.x = x;
        this.y = y;
        this.mapDefinition = mapDefinition;
    }

    public void close() {
        open = false;
        x = 0;
        y = 0;
        mapDefinition = null;
    }
}
