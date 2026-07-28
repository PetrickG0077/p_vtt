package com.petrick.vtt.feature.tabletop;

import com.petrick.vtt.feature.map.MapTextureMode;

/** A locked map instance placed in a scene and editable only in Edit Scene mode. */
public final class VttSceneMap {
    private String id;
    private String displayName;
    private String sourceMapDefinitionId;
    private String assetId;
    private VttSceneBackgroundTransform transform = new VttSceneBackgroundTransform();
    private int layerIndex;
    private boolean visible = true;
    private MapTextureMode textureMode = MapTextureMode.STRETCH;

    public VttSceneMap() {
    }

    public VttSceneMap(
            String id,
            String displayName,
            String sourceMapDefinitionId,
            String assetId
    ) {
        this(id, displayName, sourceMapDefinitionId, assetId, MapTextureMode.STRETCH);
    }

    public VttSceneMap(
            String id,
            String displayName,
            String sourceMapDefinitionId,
            String assetId,
            MapTextureMode textureMode
    ) {
        this.id = id;
        this.displayName = displayName;
        this.sourceMapDefinitionId = sourceMapDefinitionId;
        this.assetId = assetId;
        this.textureMode = MapTextureMode.normalize(textureMode);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        if (displayName != null && !displayName.isBlank()) {
            this.displayName = displayName.trim();
        }
    }

    public String getSourceMapDefinitionId() {
        return sourceMapDefinitionId;
    }

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        if (assetId != null && !assetId.isBlank()) {
            this.assetId = assetId;
        }
    }

    public VttSceneBackgroundTransform getTransform() {
        if (transform == null) transform = new VttSceneBackgroundTransform();
        transform.normalize();
        return transform;
    }

    public void setTransform(VttSceneBackgroundTransform transform) {
        this.transform = transform == null
                ? new VttSceneBackgroundTransform() : transform.copy();
    }

    public int getLayerIndex() {
        return layerIndex;
    }

    public void setLayerIndex(int layerIndex) {
        this.layerIndex = Math.max(0, layerIndex);
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public MapTextureMode getTextureMode() {
        textureMode = MapTextureMode.normalize(textureMode);
        return textureMode;
    }

    public void setTextureMode(MapTextureMode textureMode) {
        this.textureMode = MapTextureMode.normalize(textureMode);
    }
}
