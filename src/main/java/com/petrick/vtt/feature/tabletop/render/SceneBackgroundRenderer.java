package com.petrick.vtt.feature.tabletop.render;

import com.petrick.vtt.VTT;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.asset.AssetRef;
import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.feature.asset.BuiltInTextureAssetRef;
import com.petrick.vtt.feature.asset.LibraryTextureAssetRef;
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnail;
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnailRegistry;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttSceneBackgroundTransform;
import com.petrick.vtt.feature.tabletop.VttSceneMap;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/** Renders the scene map independently from selectable canvas objects. */
public final class SceneBackgroundRenderer {
    private final AssetRegistry assetRegistry;
    private final AssetThumbnailRegistry thumbnailRegistry;
    private final Set<String> resolvedIds = new HashSet<>();
    private final Set<String> missingIds = new HashSet<>();

    public SceneBackgroundRenderer(AssetRegistry assetRegistry, AssetThumbnailRegistry thumbnailRegistry) {
        this.assetRegistry = assetRegistry;
        this.thumbnailRegistry = thumbnailRegistry;
    }

    public void render(VRenderContext context, VttScene scene) {
        if (scene == null) return;
        if (scene.getBackgroundAssetId() != null) {
            renderTexture(context, scene.getBackgroundAssetId(),
                    scene.getBackgroundTransform());
        }
        scene.getMaps().stream()
                .filter(map -> map != null && map.isVisible() && map.getAssetId() != null)
                .sorted(Comparator.comparingInt(VttSceneMap::getLayerIndex))
                .forEach(map -> renderTexture(context, map.getAssetId(), map.getTransform()));
    }

    private void renderTexture(
            VRenderContext context,
            String assetId,
            VttSceneBackgroundTransform transform
    ) {
        BackgroundTexture texture = resolveTexture(assetId);
        if (texture == null) {
            logMissingOnce(assetId);
            return;
        }

        logResolvedOnce(assetId, texture);

        Vec2d center = context.renderState().worldToScreen(
                new Vec2d(transform.getX(), transform.getY()));
        double zoom = context.renderState().getCamera().getZoom();
        int width = Math.max(1, (int) Math.round(
                texture.width() * transform.getScaleX() * zoom));
        int height = Math.max(1, (int) Math.round(
                texture.height() * transform.getScaleY() * zoom));
        int left = (int) Math.round(center.x() - width / 2.0);
        int top = (int) Math.round(center.y() - height / 2.0);

        context.graphics().blit(texture.location(), left, top, width, height,
                0.0F, 0.0F, texture.width(), texture.height(), texture.width(), texture.height());
    }

    private BackgroundTexture resolveTexture(String backgroundAssetId) {
        if (backgroundAssetId.startsWith("library:")) {
            String thumbnailId = backgroundAssetId.substring("library:".length());
            return thumbnailRegistry.findById(thumbnailId).map(this::fromThumbnail).orElse(null);
        }
        String assetId = backgroundAssetId.startsWith("registered:")
                ? backgroundAssetId.substring("registered:".length()) : backgroundAssetId;
        return assetRegistry.findById(assetId).map(this::fromAsset).orElse(null);
    }

    private BackgroundTexture fromThumbnail(AssetThumbnail thumbnail) {
        return new BackgroundTexture(thumbnail.texture(), thumbnail.width(), thumbnail.height());
    }

    private BackgroundTexture fromAsset(AssetRef asset) {
        if (asset instanceof BuiltInTextureAssetRef builtIn) {
            return new BackgroundTexture(builtIn.texture(), builtIn.textureWidth(), builtIn.textureHeight());
        }
        if (asset instanceof LibraryTextureAssetRef library) {
            return new BackgroundTexture(library.texture(), library.textureWidth(), library.textureHeight());
        }
        return null;
    }

    private void logResolvedOnce(String backgroundId, BackgroundTexture texture) {
        if (!resolvedIds.add(backgroundId)) return;
        missingIds.remove(backgroundId);
        VTT.LOGGER.info("[VTT Background] Rendering {} with texture {} ({}x{})",
                backgroundId, texture.location(), texture.width(), texture.height());
    }

    private void logMissingOnce(String backgroundId) {
        if (!missingIds.add(backgroundId)) return;
        resolvedIds.remove(backgroundId);
        VTT.LOGGER.warn("[VTT Background] Could not resolve texture for {}", backgroundId);
    }

    private record BackgroundTexture(ResourceLocation location, int width, int height) {}
}
