package com.petrick.vtt.editor.hud;

import com.petrick.vtt.feature.asset.AssetRef;
import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.feature.asset.BuiltInTextureAssetRef;
import com.petrick.vtt.feature.asset.LibraryTextureAssetRef;
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnail;
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnailRegistry;
import com.petrick.vtt.feature.map.MapTextureMode;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttSceneBackgroundTransform;
import com.petrick.vtt.feature.tabletop.VttSceneMap;
import com.petrick.vtt.feature.tabletop.VttTabletop;
import com.petrick.vtt.feature.tabletop.persistence.TabletopStorage;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Renders compact scene previews from their map layers.
 *
 * The live scene is never copied. Inactive scenes are loaded once and the cache
 * is cleared when the active scene changes, which also refreshes the scene that
 * has just been saved and left.
 */
public final class SceneThumbnailRenderer {
    private static final int PADDING = 2;
    private static final int MAX_REPEAT_TILES = 16_384;

    private final TabletopStorage storage;
    private final Map<String, Optional<VttScene>> inactiveSceneCache = new HashMap<>();
    private String observedActiveSceneId;

    public SceneThumbnailRenderer(TabletopStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    public boolean render(
            VRenderContext context,
            int x,
            int y,
            int width,
            int height,
            VttTabletop tabletop,
            VttScene activeScene,
            String sceneId,
            AssetRegistry assets,
            AssetThumbnailRegistry thumbnails
    ) {
        if (tabletop == null || sceneId == null || width <= 0 || height <= 0) {
            return false;
        }
        refreshCacheForActiveScene(activeScene);
        inactiveSceneCache.keySet().removeIf(id -> !tabletop.getSceneIds().contains(id));
        VttScene scene = resolveScene(tabletop, activeScene, sceneId);
        if (scene == null) return false;

        List<Layer> layers = resolveLayers(scene, assets, thumbnails);
        if (layers.isEmpty()) return false;

        Extents extents = extents(layers);
        if (extents == null) return false;
        double availableWidth = Math.max(1, width - PADDING * 2);
        double availableHeight = Math.max(1, height - PADDING * 2);
        double scale = Math.min(
                availableWidth / extents.width(),
                availableHeight / extents.height());
        if (!Double.isFinite(scale) || scale <= 0.0) return false;

        context.graphics().fill(x, y, x + width, y + height, 0xFF101014);
        double centerX = extents.minX() + extents.width() / 2.0;
        double centerY = extents.minY() + extents.height() / 2.0;
        for (Layer layer : layers) {
            renderLayer(
                    context, layer, x, y, width, height,
                    centerX, centerY, scale);
        }
        return true;
    }

    private void refreshCacheForActiveScene(VttScene activeScene) {
        String activeId = activeScene == null ? null : activeScene.getId();
        if (Objects.equals(observedActiveSceneId, activeId)) return;
        observedActiveSceneId = activeId;
        inactiveSceneCache.clear();
    }

    private VttScene resolveScene(
            VttTabletop tabletop,
            VttScene activeScene,
            String sceneId
    ) {
        if (activeScene != null && sceneId.equals(activeScene.getId())) return activeScene;
        return inactiveSceneCache.computeIfAbsent(
                sceneId,
                id -> Optional.ofNullable(storage.loadScene(tabletop.getId(), id))
        ).orElse(null);
    }

    private List<Layer> resolveLayers(
            VttScene scene,
            AssetRegistry assets,
            AssetThumbnailRegistry thumbnails
    ) {
        List<Layer> layers = new ArrayList<>();
        if (scene.getBackgroundAssetId() != null) {
            Texture texture = texture(
                    scene.getBackgroundAssetId(), assets, thumbnails);
            if (texture != null) {
                layers.add(new Layer(
                        texture, scene.getBackgroundTransform(), MapTextureMode.STRETCH));
            }
        }
        scene.getMaps().stream()
                .filter(map -> map != null && map.isVisible() && map.getAssetId() != null)
                .sorted(Comparator.comparingInt(VttSceneMap::getLayerIndex))
                .forEach(map -> {
                    Texture texture = texture(map.getAssetId(), assets, thumbnails);
                    if (texture != null) {
                        layers.add(new Layer(
                                texture, map.getTransform(), map.getTextureMode()));
                    }
                });
        return layers;
    }

    private Extents extents(List<Layer> layers) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (Layer layer : layers) {
            double width = layer.texture().width() * layer.transform().getScaleX();
            double height = layer.texture().height() * layer.transform().getScaleY();
            minX = Math.min(minX, layer.transform().getX() - width / 2.0);
            minY = Math.min(minY, layer.transform().getY() - height / 2.0);
            maxX = Math.max(maxX, layer.transform().getX() + width / 2.0);
            maxY = Math.max(maxY, layer.transform().getY() + height / 2.0);
        }
        if (!Double.isFinite(minX) || !Double.isFinite(minY)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY)) return null;
        return new Extents(
                minX, minY,
                Math.max(1.0, maxX - minX),
                Math.max(1.0, maxY - minY));
    }

    private void renderLayer(
            VRenderContext context,
            Layer layer,
            int previewX,
            int previewY,
            int previewWidth,
            int previewHeight,
            double contentCenterX,
            double contentCenterY,
            double scale
    ) {
        int width = Math.max(1, (int) Math.round(
                layer.texture().width() * layer.transform().getScaleX() * scale));
        int height = Math.max(1, (int) Math.round(
                layer.texture().height() * layer.transform().getScaleY() * scale));
        int centerX = (int) Math.round(
                previewX + previewWidth / 2.0
                        + (layer.transform().getX() - contentCenterX) * scale);
        int centerY = (int) Math.round(
                previewY + previewHeight / 2.0
                        + (layer.transform().getY() - contentCenterY) * scale);
        int left = centerX - width / 2;
        int top = centerY - height / 2;
        int clipLeft = Math.max(previewX, left);
        int clipTop = Math.max(previewY, top);
        int clipRight = Math.min(previewX + previewWidth, left + width);
        int clipBottom = Math.min(previewY + previewHeight, top + height);
        if (clipLeft >= clipRight || clipTop >= clipBottom) return;

        context.graphics().enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        try {
            if (MapTextureMode.normalize(layer.textureMode()) == MapTextureMode.REPEAT) {
                renderRepeated(context, layer.texture(), left, top, scale, clipRight, clipBottom);
            } else {
                context.graphics().blit(
                        layer.texture().location(), left, top, width, height,
                        0.0F, 0.0F,
                        layer.texture().width(), layer.texture().height(),
                        layer.texture().width(), layer.texture().height());
            }
        } finally {
            context.graphics().disableScissor();
        }
    }

    private void renderRepeated(
            VRenderContext context,
            Texture texture,
            int left,
            int top,
            double scale,
            int clipRight,
            int clipBottom
    ) {
        int tileWidth = Math.max(1, (int) Math.round(texture.width() * scale));
        int tileHeight = Math.max(1, (int) Math.round(texture.height() * scale));
        int renderedTiles = 0;
        for (int tileY = top;
             tileY < clipBottom && renderedTiles < MAX_REPEAT_TILES;
             tileY += tileHeight) {
            for (int tileX = left;
                 tileX < clipRight && renderedTiles < MAX_REPEAT_TILES;
                 tileX += tileWidth) {
                context.graphics().blit(
                        texture.location(), tileX, tileY, tileWidth, tileHeight,
                        0.0F, 0.0F, texture.width(), texture.height(),
                        texture.width(), texture.height());
                renderedTiles++;
            }
        }
    }

    private Texture texture(
            String assetId,
            AssetRegistry assets,
            AssetThumbnailRegistry thumbnails
    ) {
        if (assetId == null) return null;
        if (assetId.startsWith("library:")) {
            String id = assetId.substring("library:".length());
            AssetThumbnail thumbnail = thumbnails.findById(id)
                    .or(() -> thumbnails.findById(assetId)).orElse(null);
            if (thumbnail != null) {
                return new Texture(
                        thumbnail.texture(), thumbnail.width(), thumbnail.height());
            }
        }
        String id = assetId.startsWith("registered:")
                ? assetId.substring("registered:".length()) : assetId;
        AssetRef asset = assets.findById(id).or(() -> assets.findById(assetId)).orElse(null);
        if (asset instanceof BuiltInTextureAssetRef builtIn) {
            return new Texture(
                    builtIn.texture(), builtIn.textureWidth(), builtIn.textureHeight());
        }
        if (asset instanceof LibraryTextureAssetRef library) {
            return new Texture(
                    library.texture(), library.textureWidth(), library.textureHeight());
        }
        return null;
    }

    private record Texture(ResourceLocation location, int width, int height) {
    }

    private record Layer(
            Texture texture,
            VttSceneBackgroundTransform transform,
            MapTextureMode textureMode
    ) {
    }

    private record Extents(double minX, double minY, double width, double height) {
    }
}
