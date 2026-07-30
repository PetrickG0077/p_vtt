package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.VTT;
import com.petrick.vtt.editor.catalog.CatalogFolderBrowser;
import com.petrick.vtt.editor.catalog.MapCatalogSelection;
import com.petrick.vtt.editor.hud.EditorHudTheme;
import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.feature.asset.BuiltInTextureAssetRef;
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnail;
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnailRegistry;
import com.petrick.vtt.feature.map.MapDefinition;
import com.petrick.vtt.feature.map.MapDefinitionRegistry;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Compact list of reusable maps, positioned above the bottom editor HUD. */
public final class MapCatalogOverlay {
    private static final int WIDTH = 210;
    private static final int PADDING = 8;
    private static final int ROW_HEIGHT = 34;
    private static final int THUMBNAIL_SIZE = 28;
    private static final int MAX_VISIBLE = 2;
    private static final int OFFSET_FROM_BOTTOM = 40;
    private static final ResourceLocation FOLDER_ICON =
            ResourceLocation.fromNamespaceAndPath(
                    VTT.MOD_ID, "textures/gui/editor_hud/folder.png");
    private final CatalogFolderBrowser<MapDefinition> folderBrowser =
            new CatalogFolderBrowser<>();

    public void render(
            VRenderContext context,
            Font font,
            MapDefinitionRegistry registry,
            MapCatalogSelection selection,
            AssetRegistry assetRegistry,
            AssetThumbnailRegistry thumbnails,
            int scrollOffset
    ) {
        List<CatalogFolderBrowser.Row<MapDefinition>> rows = visibleRows(registry);
        int first = clampOffset(rows.size(), scrollOffset);
        int visible = Math.min(MAX_VISIBLE, Math.max(0, rows.size() - first));
        int height = panelHeight(rows.size());
        int x = panelX(context.screenWidth());
        int y = panelY(context.screenHeight(), height);

        context.graphics().fill(x, y, x + WIDTH, y + height, 0xDD000000);
        border(context, x, y, WIDTH, height, EditorHudTheme.outline());
        context.graphics().drawString(font, folderBrowser.breadcrumb("Map Catalog"),
                x + PADDING, y + PADDING,
                0xFFFFFFFF, false);
        context.graphics().drawString(font, "Maps: " + (registry == null ? 0 : registry.size()),
                x + PADDING, y + 20,
                0xFFBBBBBB, false);

        int rowY = firstRowY(y);
        if (rows.isEmpty()) {
            context.graphics().drawString(font, "No maps created", x + PADDING, rowY + 8,
                    0xFF888888, false);
            return;
        }
        for (int index = 0; index < visible; index++) {
            CatalogFolderBrowser.Row<MapDefinition> row = rows.get(first + index);
            if (row.folder()) {
                context.graphics().fill(x + 4, rowY, x + WIDTH - 4,
                        rowY + ROW_HEIGHT, EditorHudTheme.folderBackground());
                context.graphics().blit(
                        FOLDER_ICON, x + PADDING, rowY + 9,
                        16, 16, 0.0F, 0.0F, 32, 32, 32, 32);
                context.graphics().drawString(
                        font, row.displayName(),
                        x + PADDING + 22, rowY + 12, 0xFFFFFFFF, false);
                rowY += ROW_HEIGHT;
                continue;
            }
            MapDefinition definition = row.item();
            boolean selected = selection != null && selection.isSelected(definition.id());
            boolean hovered = rowContains(x, rowY, context.mouseX(), context.mouseY());
            if (selected || hovered) {
                context.graphics().fill(x + 4, rowY, x + WIDTH - 4, rowY + ROW_HEIGHT,
                        selected
                                ? EditorHudTheme.selectionWithAlpha(0x55)
                                : 0x33222222);
            }
            renderThumbnail(context, definition, assetRegistry, thumbnails,
                    x + PADDING, rowY + 3);
            context.graphics().drawString(font, ellipsize(definition.displayName(), 23),
                    x + PADDING + THUMBNAIL_SIZE + 7, rowY + 7,
                    selected ? 0xFFFFFFFF : 0xFFDDDDDD, false);
            context.graphics().drawString(font,
                    definition.imageWidth() + " x " + definition.imageHeight()
                            + " \u00b7 " + (definition.textureMode()
                            == com.petrick.vtt.feature.map.MapTextureMode.REPEAT
                            ? "Repeat" : "Stretch"),
                    x + PADDING + THUMBNAIL_SIZE + 7, rowY + 19,
                    0xFF999999, false);
            rowY += ROW_HEIGHT;
        }
        if (rows.size() > MAX_VISIBLE) {
            context.graphics().drawString(font,
                    (first + 1) + "-" + (first + visible) + " / " + rows.size(),
                    x + PADDING, rowY + 2, 0xFF999999, false);
            EditorScrollbar.render(context, x + WIDTH - 9, firstRowY(y), 4,
                    MAX_VISIBLE * ROW_HEIGHT, rows.size(), MAX_VISIBLE, first,
                    EditorHudTheme.outline());
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
        List<CatalogFolderBrowser.Row<MapDefinition>> rows = visibleRows(registry);
        int height = panelHeight(rows.size());
        int x = panelX(screenWidth);
        int y = panelY(screenHeight, height);
        int first = clampOffset(rows.size(), scrollOffset);
        int visible = Math.min(MAX_VISIBLE, Math.max(0, rows.size() - first));
        int rowY = firstRowY(y);
        for (int index = 0; index < visible; index++) {
            if (rowContains(x, rowY + index * ROW_HEIGHT, mouseX, mouseY)) {
                return rows.get(first + index).item();
            }
        }
        return null;
    }

    public boolean openFolderAt(
            MapDefinitionRegistry registry,
            int screenWidth,
            int screenHeight,
            double mouseX,
            double mouseY,
            int scrollOffset
    ) {
        List<CatalogFolderBrowser.Row<MapDefinition>> rows = visibleRows(registry);
        int x = panelX(screenWidth);
        int y = panelY(screenHeight, panelHeight(rows.size()));
        int first = clampOffset(rows.size(), scrollOffset);
        int visible = Math.min(MAX_VISIBLE, Math.max(0, rows.size() - first));
        int rowY = firstRowY(y);
        for (int index = 0; index < visible; index++) {
            if (rowContains(x, rowY + index * ROW_HEIGHT, mouseX, mouseY)) {
                return folderBrowser.open(rows.get(first + index));
            }
        }
        return false;
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
        border(context, x, y, width, 18, EditorHudTheme.outline());
        context.graphics().drawString(font, label, x + 6, y + 5, 0xFFFFFFFF, false);
    }

    public boolean contains(
            MapDefinitionRegistry registry,
            int screenWidth,
            int screenHeight,
            double mouseX,
            double mouseY
    ) {
        int height = panelHeight(visibleRows(registry).size());
        int x = panelX(screenWidth);
        int y = panelY(screenHeight, height);
        return mouseX >= x && mouseX <= x + WIDTH && mouseY >= y && mouseY <= y + height;
    }

    public int clampScrollOffset(MapDefinitionRegistry registry, int value) {
        int count = visibleRows(registry).size();
        return Math.max(0, Math.min(value, Math.max(0, count - MAX_VISIBLE)));
    }

    public boolean isScrollbarAt(
            MapDefinitionRegistry registry,
            int screenWidth,
            int screenHeight,
            double mouseX,
            double mouseY
    ) {
        int count = visibleRows(registry).size();
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
        int count = visibleRows(registry).size();
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

    private List<CatalogFolderBrowser.Row<MapDefinition>> visibleRows(
            MapDefinitionRegistry registry
    ) {
        if (registry == null) return List.of();
        return folderBrowser.rows(
                registry.getAll(),
                definition -> registry.folderOf(definition.id()),
                MapDefinition::displayName);
    }

    private int clampOffset(int count, int value) {
        return Math.max(0, Math.min(value, Math.max(0, count - MAX_VISIBLE)));
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
