package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.VTT;
import com.petrick.vtt.editor.catalog.AttachmentCatalogSelection;
import com.petrick.vtt.editor.catalog.CatalogFolderBrowser;
import com.petrick.vtt.editor.hud.EditorHudTheme;
import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.feature.asset.BuiltInTextureAssetRef;
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnail;
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnailRegistry;
import com.petrick.vtt.feature.attachment.AttachmentDefinition;
import com.petrick.vtt.feature.attachment.AttachmentDefinitionRegistry;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Compact attachment library. Placement is intentionally added with scene instances later. */
public final class AttachmentCatalogOverlay {
    private static final int WIDTH = 210;
    private static final int PADDING = 8;
    private static final int ROW_HEIGHT = 34;
    private static final int PREVIEW_SIZE = 28;
    private static final int MAX_VISIBLE = 2;
    private static final int OFFSET_FROM_BOTTOM = 40;
    private static final ResourceLocation FOLDER_ICON = ResourceLocation.fromNamespaceAndPath(
            VTT.MOD_ID, "textures/gui/editor_hud/folder.png");
    private final CatalogFolderBrowser<AttachmentDefinition> folders =
            new CatalogFolderBrowser<>();

    public void render(VRenderContext context, Font font,
                       AttachmentDefinitionRegistry registry,
                       AttachmentCatalogSelection selection,
                       AssetRegistry assets, AssetThumbnailRegistry thumbnails,
                       int scrollOffset) {
        List<CatalogFolderBrowser.Row<AttachmentDefinition>> rows = rows(registry);
        int first = clamp(registry, scrollOffset);
        int visible = Math.min(MAX_VISIBLE, Math.max(0, rows.size() - first));
        int height = 38 + Math.max(1, visible) * ROW_HEIGHT
                + (rows.size() > MAX_VISIBLE ? 12 : 0);
        int x = (context.screenWidth() - WIDTH) / 2;
        int y = context.screenHeight() - height - OFFSET_FROM_BOTTOM;
        context.graphics().fill(x, y, x + WIDTH, y + height, 0xDD000000);
        border(context, x, y, WIDTH, height, EditorHudTheme.outline());
        context.graphics().drawString(font, folders.breadcrumb("Attachment Catalog"),
                x + PADDING, y + PADDING, 0xFFFFFFFF, false);
        context.graphics().drawString(font, "Attachments: " + registry.size(),
                x + PADDING, y + 20, 0xFFBBBBBB, false);
        int rowY = y + 34;
        if (rows.isEmpty()) {
            context.graphics().drawString(font, "No attachments created",
                    x + PADDING, rowY + 8, 0xFF888888, false);
            return;
        }
        for (int index = 0; index < visible; index++) {
            var row = rows.get(first + index);
            boolean hovered = containsRow(x, rowY, context.mouseX(), context.mouseY());
            if (row.folder()) {
                context.graphics().fill(x + 4, rowY, x + WIDTH - 4,
                        rowY + ROW_HEIGHT, EditorHudTheme.folderBackground());
                context.graphics().blit(FOLDER_ICON, x + PADDING, rowY + 9,
                        16, 16, 0, 0, 32, 32, 32, 32);
                context.graphics().drawString(font, row.displayName(),
                        x + PADDING + 22, rowY + 12, 0xFFFFFFFF, false);
            } else {
                AttachmentDefinition definition = row.item();
                boolean selected = selection.isSelected(definition.id());
                if (selected || hovered) context.graphics().fill(x + 4, rowY,
                        x + WIDTH - 4, rowY + ROW_HEIGHT,
                        selected ? EditorHudTheme.selectionWithAlpha(0x55) : 0x33222222);
                renderPreview(context, definition, assets, thumbnails,
                        x + PADDING, rowY + 3);
                context.graphics().drawString(font, trim(definition.displayName(), 23),
                        x + PADDING + PREVIEW_SIZE + 7, rowY + 7,
                        selected ? 0xFFFFFFFF : 0xFFDDDDDD, false);
                context.graphics().drawString(font,
                        formatSize(definition.defaultWidth()) + " x "
                                + formatSize(definition.defaultHeight()),
                        x + PADDING + PREVIEW_SIZE + 7, rowY + 19,
                        0xFF999999, false);
            }
            rowY += ROW_HEIGHT;
        }
        if (rows.size() > MAX_VISIBLE) {
            context.graphics().drawString(font,
                    (first + 1) + "-" + (first + visible) + " / " + rows.size(),
                    x + PADDING, rowY + 2, 0xFF999999, false);
            EditorScrollbar.render(context, x + WIDTH - 9, y + 34, 4,
                    MAX_VISIBLE * ROW_HEIGHT, rows.size(), MAX_VISIBLE, first,
                    EditorHudTheme.outline());
        }
    }

    public AttachmentDefinition findAt(AttachmentDefinitionRegistry registry,
                                       int screenWidth, int screenHeight,
                                       double mouseX, double mouseY, int scrollOffset) {
        List<CatalogFolderBrowser.Row<AttachmentDefinition>> rows = rows(registry);
        int visibleRows = Math.min(MAX_VISIBLE, rows.size());
        int height = 38 + Math.max(1, visibleRows) * ROW_HEIGHT
                + (rows.size() > MAX_VISIBLE ? 12 : 0);
        int x = (screenWidth - WIDTH) / 2;
        int y = screenHeight - height - OFFSET_FROM_BOTTOM + 34;
        int first = clamp(registry, scrollOffset);
        for (int index = 0; index < Math.min(MAX_VISIBLE, rows.size() - first); index++) {
            if (containsRow(x, y + index * ROW_HEIGHT, mouseX, mouseY)) {
                return rows.get(first + index).item();
            }
        }
        return null;
    }

    public boolean openFolderAt(AttachmentDefinitionRegistry registry,
                                int screenWidth, int screenHeight,
                                double mouseX, double mouseY, int scrollOffset) {
        List<CatalogFolderBrowser.Row<AttachmentDefinition>> rows = rows(registry);
        int visibleRows = Math.min(MAX_VISIBLE, rows.size());
        int height = 38 + Math.max(1, visibleRows) * ROW_HEIGHT
                + (rows.size() > MAX_VISIBLE ? 12 : 0);
        int x = (screenWidth - WIDTH) / 2;
        int y = screenHeight - height - OFFSET_FROM_BOTTOM + 34;
        int first = clamp(registry, scrollOffset);
        for (int index = 0; index < Math.min(MAX_VISIBLE, rows.size() - first); index++) {
            if (containsRow(x, y + index * ROW_HEIGHT, mouseX, mouseY)) {
                return folders.open(rows.get(first + index));
            }
        }
        return false;
    }

    public boolean contains(AttachmentDefinitionRegistry registry, int screenWidth,
                            int screenHeight, double mouseX, double mouseY) {
        int count = rows(registry).size();
        int height = 38 + Math.max(1, Math.min(MAX_VISIBLE, count)) * ROW_HEIGHT
                + (count > MAX_VISIBLE ? 12 : 0);
        int x = (screenWidth - WIDTH) / 2;
        int y = screenHeight - height - OFFSET_FROM_BOTTOM;
        return mouseX >= x && mouseX <= x + WIDTH && mouseY >= y && mouseY <= y + height;
    }

    public int clamp(AttachmentDefinitionRegistry registry, int offset) {
        return Math.max(0, Math.min(offset,
                Math.max(0, rows(registry).size() - MAX_VISIBLE)));
    }

    private void renderPreview(VRenderContext context, AttachmentDefinition definition,
                               AssetRegistry assets, AssetThumbnailRegistry thumbnails,
                               int x, int y) {
        context.graphics().fill(x, y, x + PREVIEW_SIZE, y + PREVIEW_SIZE,
                definition.hasImage() ? 0xFF18181E : 0xFF2374C6);
        ResourceLocation texture = null;
        int width = 1;
        int height = 1;
        if (definition.hasImage()) {
            AssetThumbnail thumbnail = thumbnails.findById(definition.assetId()).orElse(null);
            if (thumbnail == null && definition.assetId().startsWith("library:")) {
                thumbnail = thumbnails.findById(
                        definition.assetId().substring("library:".length())).orElse(null);
            }
            if (thumbnail != null) {
                texture = thumbnail.texture(); width = thumbnail.width(); height = thumbnail.height();
            } else {
                String id = definition.assetId().startsWith("registered:")
                        ? definition.assetId().substring("registered:".length())
                        : definition.assetId();
                if (assets.findById(id).orElse(null) instanceof BuiltInTextureAssetRef builtIn) {
                    texture = builtIn.texture(); width = builtIn.textureWidth();
                    height = builtIn.textureHeight();
                }
            }
        }
        if (texture != null) {
            double scale = Math.min(PREVIEW_SIZE / (double) width, PREVIEW_SIZE / (double) height);
            int drawWidth = Math.max(1, (int) Math.round(width * scale));
            int drawHeight = Math.max(1, (int) Math.round(height * scale));
            context.graphics().blit(texture, x + (PREVIEW_SIZE - drawWidth) / 2,
                    y + (PREVIEW_SIZE - drawHeight) / 2, drawWidth, drawHeight,
                    0, 0, width, height, width, height);
        }
        border(context, x, y, PREVIEW_SIZE, PREVIEW_SIZE, 0xFFDDDDDD);
    }

    private List<CatalogFolderBrowser.Row<AttachmentDefinition>> rows(
            AttachmentDefinitionRegistry registry) {
        return folders.rows(registry.getAll(),
                definition -> registry.folderOf(definition.id()),
                AttachmentDefinition::displayName);
    }

    private boolean containsRow(int x, int y, double mouseX, double mouseY) {
        return mouseX >= x + 4 && mouseX <= x + WIDTH - 4
                && mouseY >= y && mouseY <= y + ROW_HEIGHT;
    }

    private void border(VRenderContext context, int x, int y, int width, int height, int color) {
        context.graphics().hLine(x, x + width, y, color);
        context.graphics().hLine(x, x + width, y + height, color);
        context.graphics().vLine(x, y, y + height, color);
        context.graphics().vLine(x + width, y, y + height, color);
    }

    private String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    private String formatSize(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value)
                : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
