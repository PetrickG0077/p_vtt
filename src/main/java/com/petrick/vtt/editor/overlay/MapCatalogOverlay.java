package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.editor.catalog.MapCatalogSelection;
import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.feature.asset.BuiltInTextureAssetRef;
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnail;
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnailRegistry;
import com.petrick.vtt.feature.map.MapDefinition;
import com.petrick.vtt.feature.map.MapDefinitionRegistry;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;

/** Compact list of reusable maps, positioned above the bottom editor HUD. */
public final class MapCatalogOverlay {
    private static final int WIDTH = 210;
    private static final int PADDING = 8;
    private static final int ROW_HEIGHT = 34;
    private static final int THUMBNAIL_SIZE = 28;
    private static final int MAX_VISIBLE = 2;
    private static final int OFFSET_FROM_BOTTOM = 40;
    private static final int BORDER = 0xFF66CCFF;

    public void render(
            VRenderContext context,
            Font font,
            MapDefinitionRegistry registry,
            MapCatalogSelection selection,
            AssetRegistry assetRegistry,
            AssetThumbnailRegistry thumbnails,
            int scrollOffset
    ) {
        List<MapDefinition> maps = sorted(registry);
        int first = clampScrollOffset(registry, scrollOffset);
        int visible = Math.min(MAX_VISIBLE, Math.max(0, maps.size() - first));
        int height = panelHeight(maps.size());
        int x = panelX(context.screenWidth());
        int y = panelY(context.screenHeight(), height);

        context.graphics().fill(x, y, x + WIDTH, y + height, 0xDD000000);
        border(context, x, y, WIDTH, height, BORDER);
        context.graphics().drawString(font, "Map Catalog", x + PADDING, y + PADDING,
                0xFFFFFFFF, false);
        context.graphics().drawString(font, "Maps: " + maps.size(), x + PADDING, y + 20,
                0xFFBBBBBB, false);

        int rowY = firstRowY(y);
        if (maps.isEmpty()) {
            context.graphics().drawString(font, "No maps created", x + PADDING, rowY + 8,
                    0xFF888888, false);
            return;
        }
        for (int index = 0; index < visible; index++) {
            MapDefinition definition = maps.get(first + index);
            boolean selected = selection != null && selection.isSelected(definition.id());
            boolean hovered = rowContains(x, rowY, context.mouseX(), context.mouseY());
            if (selected || hovered) {
                context.graphics().fill(x + 4, rowY, x + WIDTH - 4, rowY + ROW_HEIGHT,
                        selected ? 0x553399FF : 0x33222222);
            }
            renderThumbnail(context, definition, assetRegistry, thumbnails,
                    x + PADDING, rowY + 3);
            context.graphics().drawString(font, ellipsize(definition.displayName(), 23),
                    x + PADDING + THUMBNAIL_SIZE + 7, rowY + 7,
                    selected ? 0xFFFFFFFF : 0xFFDDDDDD, false);
            context.graphics().drawString(font,
                    definition.imageWidth() + " x " + definition.imageHeight(),
                    x + PADDING + THUMBNAIL_SIZE + 7, rowY + 19,
                    0xFF999999, false);
            rowY += ROW_HEIGHT;
        }
        if (maps.size() > MAX_VISIBLE) {
            context.graphics().drawString(font,
                    (first + 1) + "-" + (first + visible) + " / " + maps.size(),
                    x + PADDING, rowY + 2, 0xFF999999, false);
            EditorScrollbar.render(context, x + WIDTH - 9, firstRowY(y), 4,
                    MAX_VISIBLE * ROW_HEIGHT, maps.size(), MAX_VISIBLE, first, BORDER);
        }
    }

    public MapDefinition findMapAt(
            MapDefinitionRegistry registry,
            int screenWidth,
            int screenHeight,
            double mouseX,
            double mouseY,
            int scrollOffset
    ) {
        List<MapDefinition> maps = sorted(registry);
        int height = panelHeight(maps.size());
        int x = panelX(screenWidth);
        int y = panelY(screenHeight, height);
        int first = clampScrollOffset(registry, scrollOffset);
        int visible = Math.min(MAX_VISIBLE, Math.max(0, maps.size() - first));
        int rowY = firstRowY(y);
        for (int index = 0; index < visible; index++) {
            if (rowContains(x, rowY + index * ROW_HEIGHT, mouseX, mouseY)) {
                return maps.get(first + index);
            }
        }
        return null;
    }

    public void renderDragPreview(
            VRenderContext context, Font font, MapDefinition definition,
            double mouseX, double mouseY
    ) {
        if (definition == null) return;
        String label = "Place map: " + definition.displayName();
        int x = (int) Math.round(mouseX) + 12;
        int y = (int) Math.round(mouseY) + 12;
        int width = font.width(label) + 12;
        context.graphics().fill(x, y, x + width, y + 18, 0xEE08080C);
        border(context, x, y, width, 18, BORDER);
        context.graphics().drawString(font, label, x + 6, y + 5, 0xFFFFFFFF, false);
    }

    public boolean contains(
            MapDefinitionRegistry registry,
            int screenWidth,
            int screenHeight,
            double mouseX,
            double mouseY
    ) {
        int height = panelHeight(registry == null ? 0 : registry.size());
        int x = panelX(screenWidth);
        int y = panelY(screenHeight, height);
        return mouseX >= x && mouseX <= x + WIDTH && mouseY >= y && mouseY <= y + height;
    }

    public int clampScrollOffset(MapDefinitionRegistry registry, int value) {
        int count = registry == null ? 0 : registry.size();
        return Math.max(0, Math.min(value, Math.max(0, count - MAX_VISIBLE)));
    }

    public boolean isScrollbarAt(
            MapDefinitionRegistry registry,
            int screenWidth,
            int screenHeight,
            double mouseX,
            double mouseY
    ) {
        int count = registry == null ? 0 : registry.size();
        if (count <= MAX_VISIBLE) return false;
        int height = panelHeight(count);
        int x = panelX(screenWidth);
        int y = panelY(screenHeight, height);
        return EditorScrollbar.contains(
                mouseX, mouseY, x + WIDTH - 12, firstRowY(y),
                10, MAX_VISIBLE * ROW_HEIGHT);
    }

    public int scrollOffsetFromMouse(
            MapDefinitionRegistry registry,
            int screenHeight,
            double mouseY
    ) {
        int count = registry == null ? 0 : registry.size();
        int y = panelY(screenHeight, panelHeight(count));
        return EditorScrollbar.offsetForMouse(
                mouseY, firstRowY(y), MAX_VISIBLE * ROW_HEIGHT,
                count, MAX_VISIBLE);
    }

    private void renderThumbnail(
            VRenderContext context,
            MapDefinition definition,
            AssetRegistry assetRegistry,
            AssetThumbnailRegistry thumbnails,
            int x,
            int y
    ) {
        ResourceLocation texture = null;
        int sourceWidth = definition.imageWidth();
        int sourceHeight = definition.imageHeight();
        AssetThumbnail thumbnail = thumbnails.findById(definition.assetId()).orElse(null);
        if (thumbnail == null && definition.assetId().startsWith("library:")) {
            thumbnail = thumbnails.findById(definition.assetId().substring("library:".length()))
                    .orElse(null);
        }
        if (thumbnail != null) {
            texture = thumbnail.texture();
            sourceWidth = thumbnail.width();
            sourceHeight = thumbnail.height();
        } else if (definition.assetId().startsWith("registered:")) {
            String assetId = definition.assetId().substring("registered:".length());
            if (assetRegistry.findById(assetId).orElse(null) instanceof BuiltInTextureAssetRef builtIn) {
                texture = builtIn.texture();
                sourceWidth = builtIn.textureWidth();
                sourceHeight = builtIn.textureHeight();
            }
        }
        context.graphics().fill(x, y, x + THUMBNAIL_SIZE, y + THUMBNAIL_SIZE, 0xFF18181E);
        if (texture != null) {
            context.graphics().blit(texture, x, y, THUMBNAIL_SIZE, THUMBNAIL_SIZE,
                    0.0F, 0.0F, sourceWidth, sourceHeight, sourceWidth, sourceHeight);
        }
        border(context, x, y, THUMBNAIL_SIZE, THUMBNAIL_SIZE, 0xFFDDDDDD);
    }

    private List<MapDefinition> sorted(MapDefinitionRegistry registry) {
        if (registry == null) return List.of();
        return registry.getAll().stream()
                .sorted(Comparator.comparing(MapDefinition::displayName,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private int panelHeight(int count) {
        return 38 + Math.max(1, Math.min(MAX_VISIBLE, count)) * ROW_HEIGHT
                + (count > MAX_VISIBLE ? 12 : 0);
    }

    private int panelX(int screenWidth) {
        return (screenWidth - WIDTH) / 2;
    }

    private int panelY(int screenHeight, int height) {
        return screenHeight - height - OFFSET_FROM_BOTTOM;
    }

    private int firstRowY(int panelY) {
        return panelY + 34;
    }

    private boolean rowContains(int panelX, int rowY, double mouseX, double mouseY) {
        return mouseX >= panelX + 4 && mouseX <= panelX + WIDTH - 4
                && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;
    }

    private void border(VRenderContext context, int x, int y, int width, int height, int color) {
        context.graphics().hLine(x, x + width, y, color);
        context.graphics().hLine(x, x + width, y + height, color);
        context.graphics().vLine(x, y, y + height, color);
        context.graphics().vLine(x + width, y, y + height, color);
    }

    private String ellipsize(String value, int maximumLength) {
        if (value == null) return "";
        return value.length() <= maximumLength
                ? value : value.substring(0, maximumLength - 3) + "...";
    }
}
