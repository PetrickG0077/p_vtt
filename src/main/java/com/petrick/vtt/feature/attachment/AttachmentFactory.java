package com.petrick.vtt.feature.attachment;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.core.transform.Transform2D;
import com.petrick.vtt.feature.asset.AssetRef;
import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.feature.asset.BuiltInTextureAssetRef;
import com.petrick.vtt.feature.asset.LibraryTextureAssetRef;
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnail;
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnailRegistry;
import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasObjectState;
import com.petrick.vtt.feature.canvas.visual.CanvasVisual;
import com.petrick.vtt.feature.canvas.visual.ColorVisual;
import com.petrick.vtt.feature.canvas.visual.TextureVisual;

import java.util.Map;

/** Creates placed canvas instances from attachment definitions. */
public final class AttachmentFactory {
    private AttachmentFactory() {}

    public static CanvasObject createCanvasObject(
            AttachmentDefinition definition,
            String objectId,
            Vec2d position,
            AssetRegistry assets,
            AssetThumbnailRegistry thumbnails
    ) {
        CanvasVisual visual = resolveVisual(definition, assets, thumbnails);
        Map<String, CanvasObjectState> states = Map.of(
                CanvasObject.DEFAULT_STATE_ID,
                new CanvasObjectState(CanvasObject.DEFAULT_STATE_ID, "Default", visual));
        return new CanvasObject(
                objectId, definition.displayName(), null,
                new Transform2D(position, 0.0, new Vec2d(1.0, 1.0)),
                new Vec2d(definition.defaultWidth(), definition.defaultHeight()),
                states, CanvasObject.DEFAULT_STATE_ID, true, false, definition.id());
    }

    private static CanvasVisual resolveVisual(
            AttachmentDefinition definition,
            AssetRegistry assets,
            AssetThumbnailRegistry thumbnails
    ) {
        if (!definition.hasImage()) return new ColorVisual(0x00000000);
        AssetRef asset = resolveAsset(definition.assetId(), assets, thumbnails);
        return asset == null ? new ColorVisual(0x00000000) : new TextureVisual(asset);
    }

    private static AssetRef resolveAsset(
            String assetId, AssetRegistry assets, AssetThumbnailRegistry thumbnails
    ) {
        String normalized = assetId;
        if (assetId.startsWith("registered:")) {
            normalized = assetId.substring("registered:".length());
            if (assets.findById(normalized).orElse(null) instanceof BuiltInTextureAssetRef builtIn) {
                return builtIn;
            }
        }
        if (assets.findById(normalized).orElse(null) instanceof BuiltInTextureAssetRef builtIn) {
            return builtIn;
        }
        AssetThumbnail thumbnail = thumbnails.findById(assetId).orElse(null);
        if (thumbnail == null && assetId.startsWith("library:")) {
            normalized = assetId.substring("library:".length());
            thumbnail = thumbnails.findById(normalized).orElse(null);
        }
        if (thumbnail == null) return null;
        return new LibraryTextureAssetRef(
                assetId, thumbnail.texture(), thumbnail.width(), thumbnail.height(), normalized);
    }
}
