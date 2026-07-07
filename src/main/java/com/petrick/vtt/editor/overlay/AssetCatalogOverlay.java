package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.editor.catalog.AssetCatalogFilter;
import com.petrick.vtt.editor.catalog.AssetCatalogItem;
import com.petrick.vtt.editor.catalog.AssetCatalogSelection;
import com.petrick.vtt.editor.catalog.AssetCatalogTreeBuilder;
import com.petrick.vtt.editor.catalog.AssetCatalogTreeNode;
import com.petrick.vtt.editor.catalog.AssetCatalogTreeState;
import com.petrick.vtt.editor.catalog.AssetCatalogVisibleRow;
import com.petrick.vtt.feature.asset.AssetRef;
import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.feature.asset.BuiltInTextureAssetRef;
import com.petrick.vtt.feature.asset.library.AssetLibraryEntry;
import com.petrick.vtt.feature.asset.library.AssetLibraryScanResult;
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnail;
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnailRegistry;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Painel que lista assets disponíveis em forma de árvore.
 *
 * Mostra:
 * - assets registrados em memória;
 * - arquivos detectados na biblioteca real;
 * - pastas expansíveis/recolhíveis;
 * - scroll interno;
 * - filtro por tipo;
 * - miniatura real quando disponível.
 */
public final class AssetCatalogOverlay {

    private static final int PANEL_WIDTH = 210;

    private static final int PANEL_HEIGHT = 150;

    private static final int PADDING = 8;

    private static final int LINE_HEIGHT = 10;

    private static final int ROW_HEIGHT = 26;

    private static final int THUMBNAIL_SIZE = 20;

    private static final int POPUP_PREVIEW_SIZE = 48;

    private static final int INDENT_WIDTH = 10;

    private static final int PANEL_BACKGROUND = 0xAA000000;

    private static final int PANEL_BORDER = 0xFFFFAA33;

    private static final int TITLE_COLOR = 0xFFFFFFFF;

    private static final int TEXT_COLOR = 0xFFDDDDDD;

    private static final int MUTED_TEXT_COLOR = 0xFFAAAAAA;

    private static final int HOVER_TEXT_COLOR = 0xFFFFDD88;

    private static final int FOLDER_TEXT_COLOR = 0xFFFFCC66;

    private static final int SELECTED_ROW_BACKGROUND = 0x55FFAA33;

    private static final int HOVERED_ROW_BACKGROUND = 0x33222222;

    private static final int DETAILS_POPUP_WIDTH = 285;

    private static final int SCROLL_STEP = 1;

    private static final int MAX_ITEM_NAME_LENGTH = 33;

    private final AssetCatalogTreeBuilder treeBuilder = new AssetCatalogTreeBuilder();

    public void render(
            VRenderContext context,
            Font font,
            AssetRegistry assetRegistry,
            AssetLibraryScanResult libraryScanResult,
            AssetThumbnailRegistry thumbnailRegistry,
            AssetCatalogSelection selection,
            AssetCatalogFilter filter,
            AssetCatalogTreeState treeState,
            int scrollOffset
    ) {
        List<AssetCatalogItem> items = getCatalogItems(
                assetRegistry,
                libraryScanResult,
                filter
        );

        AssetCatalogTreeNode.Folder root = treeBuilder.build(items);

        List<AssetCatalogVisibleRow> rows = treeBuilder.flattenVisibleRows(
                root,
                treeState
        );

        Optional<AssetCatalogVisibleRow> hoveredRow = findRowAt(
                assetRegistry,
                libraryScanResult,
                filter,
                treeState,
                context.screenHeight(),
                context.mouseX(),
                context.mouseY(),
                scrollOffset
        );

        AssetCatalogItem detailsItem = resolveDetailsItem(
                rows,
                hoveredRow,
                selection
        );

        int x = getPanelX();
        int y = getPanelY(context, PANEL_HEIGHT);

        renderPanelBackground(context, x, y, PANEL_WIDTH, PANEL_HEIGHT);

        int textX = x + PADDING;
        int textY = y + PADDING;

        drawLine(context, font, "Asset Catalog", textX, textY, TITLE_COLOR);
        textY += LINE_HEIGHT + 4;

        drawLine(
                context,
                font,
                "Items: " + items.size() + " | " + filter.displayName(),
                textX,
                textY,
                TEXT_COLOR
        );
        textY += LINE_HEIGHT + 4;

        if (rows.isEmpty()) {
            drawLine(context, font, "No assets found", textX, textY, MUTED_TEXT_COLOR);
            return;
        }

        int maxVisibleRows = calculateMaxVisibleRows();

        int firstVisibleIndex = clampFirstVisibleIndex(
                scrollOffset,
                rows.size(),
                maxVisibleRows
        );

        int visibleRows = Math.min(
                maxVisibleRows,
                rows.size() - firstVisibleIndex
        );

        for (int i = 0; i < visibleRows; i++) {
            AssetCatalogVisibleRow row = rows.get(firstVisibleIndex + i);

            boolean hovered = hoveredRow
                    .map(visibleRow -> visibleRow.node().id().equals(row.node().id()))
                    .orElse(false);

            boolean selected = row.isItem()
                    && selection != null
                    && selection.isSelected(row.item().catalogItem().id());

            renderTreeRow(
                    context,
                    font,
                    thumbnailRegistry,
                    treeState,
                    row,
                    textX,
                    textY,
                    hovered,
                    selected
            );

            textY += ROW_HEIGHT;
        }

        renderScrollInfo(
                context,
                font,
                rows.size(),
                firstVisibleIndex,
                maxVisibleRows,
                textX,
                y + PANEL_HEIGHT - PADDING - LINE_HEIGHT
        );

        if (detailsItem != null) {
            int popupX;
            int popupY;

            if (hoveredRow.isPresent() && hoveredRow.get().isItem()) {
                popupX = (int) Math.round(context.mouseX()) + 14;
                popupY = (int) Math.round(context.mouseY())
                        - calculateDetailsPopupHeight(detailsItem)
                        - 14;
            } else {
                popupX = x + PANEL_WIDTH + 8;
                popupY = resolvePopupYForItem(
                        context,
                        rows,
                        detailsItem,
                        firstVisibleIndex,
                        maxVisibleRows
                );
            }

            renderItemDetailsPopup(
                    context,
                    font,
                    thumbnailRegistry,
                    detailsItem,
                    popupX,
                    popupY
            );
        }
    }

    private void renderTreeRow(
            VRenderContext context,
            Font font,
            AssetThumbnailRegistry thumbnailRegistry,
            AssetCatalogTreeState treeState,
            AssetCatalogVisibleRow row,
            int x,
            int y,
            boolean hovered,
            boolean selected
    ) {
        if (selected || hovered) {
            int rowBackground = selected
                    ? SELECTED_ROW_BACKGROUND
                    : HOVERED_ROW_BACKGROUND;

            context.graphics().fill(
                    x - 3,
                    y,
                    x + PANEL_WIDTH - PADDING * 2,
                    y + ROW_HEIGHT,
                    rowBackground
            );
        }

        int indentX = x + row.depth() * INDENT_WIDTH;

        if (row.isFolder()) {
            renderFolderRow(
                    context,
                    font,
                    treeState,
                    row.folder(),
                    indentX,
                    y,
                    hovered
            );
            return;
        }

        renderItemRow(
                context,
                font,
                thumbnailRegistry,
                row.item(),
                indentX,
                y,
                hovered,
                selected,
                row.depth()
        );
    }

    private void renderFolderRow(
            VRenderContext context,
            Font font,
            AssetCatalogTreeState treeState,
            AssetCatalogTreeNode.Folder folder,
            int x,
            int y,
            boolean hovered
    ) {
        String arrow = treeState.isExpanded(folder.id()) ? "▾" : "▸";

        String text = arrow + " " + folder.displayName();

        drawLine(
                context,
                font,
                truncateText(text, MAX_ITEM_NAME_LENGTH),
                x,
                y + 8,
                hovered ? HOVER_TEXT_COLOR : FOLDER_TEXT_COLOR
        );
    }

    private void renderItemRow(
            VRenderContext context,
            Font font,
            AssetThumbnailRegistry thumbnailRegistry,
            AssetCatalogTreeNode.Item itemNode,
            int x,
            int y,
            boolean hovered,
            boolean selected,
            int depth
    ) {
        AssetCatalogItem item = itemNode.catalogItem();

        int thumbnailX = x;
        int thumbnailY = y + 3;

        renderItemThumbnail(
                context,
                thumbnailRegistry,
                item,
                thumbnailX,
                thumbnailY,
                THUMBNAIL_SIZE,
                THUMBNAIL_SIZE
        );

        int textX = x + THUMBNAIL_SIZE + 8;

        int maxNameLength = Math.max(
                8,
                MAX_ITEM_NAME_LENGTH - depth * 2
        );

        drawLine(
                context,
                font,
                truncateText(itemNode.displayName(), maxNameLength),
                textX,
                y + 3,
                selected ? TITLE_COLOR : hovered ? HOVER_TEXT_COLOR : TEXT_COLOR
        );

        drawLine(
                context,
                font,
                item.typeName(),
                textX,
                y + 15,
                MUTED_TEXT_COLOR
        );
    }

    private void renderItemThumbnail(
            VRenderContext context,
            AssetThumbnailRegistry thumbnailRegistry,
            AssetCatalogItem item,
            int x,
            int y,
            int width,
            int height
    ) {
        if (item instanceof AssetCatalogItem.RegisteredAsset registeredAsset
                && registeredAsset.assetRef() instanceof BuiltInTextureAssetRef builtInTexture) {
            context.graphics().blit(
                    builtInTexture.texture(),
                    x,
                    y,
                    width,
                    height,
                    0.0F,
                    0.0F,
                    builtInTexture.textureWidth(),
                    builtInTexture.textureHeight(),
                    builtInTexture.textureWidth(),
                    builtInTexture.textureHeight()
            );
        } else if (item instanceof AssetCatalogItem.LibraryFile libraryFile) {
            AssetLibraryEntry entry = libraryFile.entry();

            AssetThumbnail thumbnail = thumbnailRegistry
                    .findById(entry.id())
                    .orElse(null);

            if (thumbnail != null) {
                context.graphics().blit(
                        thumbnail.texture(),
                        x,
                        y,
                        width,
                        height,
                        0.0F,
                        0.0F,
                        thumbnail.width(),
                        thumbnail.height(),
                        thumbnail.width(),
                        thumbnail.height()
                );
            } else {
                renderLibraryFilePlaceholder(
                        context,
                        entry,
                        x,
                        y,
                        width,
                        height
                );
            }
        } else {
            context.graphics().fill(
                    x,
                    y,
                    x + width,
                    y + height,
                    0xFFFF00FF
            );
        }

        context.graphics().hLine(x, x + width, y, PANEL_BORDER);
        context.graphics().hLine(x, x + width, y + height, PANEL_BORDER);
        context.graphics().vLine(x, y, y + height, PANEL_BORDER);
        context.graphics().vLine(x + width, y, y + height, PANEL_BORDER);
    }

    private void renderLibraryFilePlaceholder(
            VRenderContext context,
            AssetLibraryEntry entry,
            int x,
            int y,
            int width,
            int height
    ) {
        int color = switch (entry.fileType()) {
            case IMAGE -> 0xFF3F6EA8;
            case ANIMATED_IMAGE -> 0xFF3FA86A;
            case DOCUMENT -> 0xFF666666;
            case UNKNOWN -> 0xFF883F88;
        };

        context.graphics().fill(
                x,
                y,
                x + width,
                y + height,
                color
        );
    }

    public Optional<AssetCatalogVisibleRow> findRowAt(
            AssetRegistry assetRegistry,
            AssetLibraryScanResult libraryScanResult,
            AssetCatalogFilter filter,
            AssetCatalogTreeState treeState,
            int screenHeight,
            double mouseX,
            double mouseY,
            int scrollOffset
    ) {
        List<AssetCatalogItem> items = getCatalogItems(
                assetRegistry,
                libraryScanResult,
                filter
        );

        AssetCatalogTreeNode.Folder root = treeBuilder.build(items);

        List<AssetCatalogVisibleRow> rows = treeBuilder.flattenVisibleRows(
                root,
                treeState
        );

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        int panelX = getPanelX();
        int panelY = getPanelY(screenHeight, PANEL_HEIGHT);

        if (mouseX < panelX || mouseX > panelX + PANEL_WIDTH) {
            return Optional.empty();
        }

        if (mouseY < panelY || mouseY > panelY + PANEL_HEIGHT) {
            return Optional.empty();
        }

        int firstRowY = panelY + PADDING + LINE_HEIGHT + 4 + LINE_HEIGHT + 4;

        int maxVisibleRows = calculateMaxVisibleRows();

        int firstVisibleIndex = clampFirstVisibleIndex(
                scrollOffset,
                rows.size(),
                maxVisibleRows
        );

        int visibleRows = Math.min(
                maxVisibleRows,
                rows.size() - firstVisibleIndex
        );

        for (int i = 0; i < visibleRows; i++) {
            int rowTop = firstRowY + i * ROW_HEIGHT;
            int rowBottom = rowTop + ROW_HEIGHT;

            if (mouseY >= rowTop && mouseY <= rowBottom) {
                return Optional.of(rows.get(firstVisibleIndex + i));
            }
        }

        return Optional.empty();
    }

    public boolean containsPoint(
            int screenHeight,
            double mouseX,
            double mouseY
    ) {
        int panelX = getPanelX();
        int panelY = getPanelY(screenHeight, PANEL_HEIGHT);

        return mouseX >= panelX
                && mouseX <= panelX + PANEL_WIDTH
                && mouseY >= panelY
                && mouseY <= panelY + PANEL_HEIGHT;
    }

    public int scroll(
            AssetRegistry assetRegistry,
            AssetLibraryScanResult libraryScanResult,
            AssetCatalogFilter filter,
            AssetCatalogTreeState treeState,
            int currentScrollOffset,
            double scrollY
    ) {
        int direction = scrollY < 0 ? 1 : -1;

        int newOffset = currentScrollOffset + direction * SCROLL_STEP;

        return clampScrollOffset(
                assetRegistry,
                libraryScanResult,
                filter,
                treeState,
                newOffset
        );
    }

    public int clampScrollOffset(
            AssetRegistry assetRegistry,
            AssetLibraryScanResult libraryScanResult,
            AssetCatalogFilter filter,
            AssetCatalogTreeState treeState,
            int scrollOffset
    ) {
        int rowCount = getVisibleRows(
                assetRegistry,
                libraryScanResult,
                filter,
                treeState
        ).size();

        int maxVisibleRows = calculateMaxVisibleRows();

        int maxScrollOffset = Math.max(0, rowCount - maxVisibleRows);

        return Math.max(0, Math.min(scrollOffset, maxScrollOffset));
    }

    private List<AssetCatalogVisibleRow> getVisibleRows(
            AssetRegistry assetRegistry,
            AssetLibraryScanResult libraryScanResult,
            AssetCatalogFilter filter,
            AssetCatalogTreeState treeState
    ) {
        List<AssetCatalogItem> items = getCatalogItems(
                assetRegistry,
                libraryScanResult,
                filter
        );

        AssetCatalogTreeNode.Folder root = treeBuilder.build(items);

        return treeBuilder.flattenVisibleRows(
                root,
                treeState
        );
    }

    private void renderItemDetailsPopup(
            VRenderContext context,
            Font font,
            AssetThumbnailRegistry thumbnailRegistry,
            AssetCatalogItem item,
            int x,
            int y
    ) {
        int popupHeight = calculateDetailsPopupHeight(item);

        int clampedX = clampPopupX(x, DETAILS_POPUP_WIDTH, context.screenWidth());
        int clampedY = clampPopupY(y, popupHeight, context.screenHeight());

        renderPanelBackground(
                context,
                clampedX,
                clampedY,
                DETAILS_POPUP_WIDTH,
                popupHeight
        );

        int textX = clampedX + PADDING;
        int textY = clampedY + PADDING;

        renderItemThumbnail(
                context,
                thumbnailRegistry,
                item,
                textX,
                textY,
                POPUP_PREVIEW_SIZE,
                POPUP_PREVIEW_SIZE
        );

        textY += POPUP_PREVIEW_SIZE + 6;

        drawLine(context, font, "Asset Details:", textX, textY, TITLE_COLOR);
        textY += LINE_HEIGHT;

        drawLine(context, font, "ID: " + item.id(), textX, textY, MUTED_TEXT_COLOR);
        textY += LINE_HEIGHT;

        drawLine(context, font, "Type: " + item.typeName(), textX, textY, TEXT_COLOR);
        textY += LINE_HEIGHT;

        if (item instanceof AssetCatalogItem.RegisteredAsset registeredAsset) {
            renderRegisteredAssetDetails(
                    context,
                    font,
                    registeredAsset.assetRef(),
                    textX,
                    textY
            );
        } else if (item instanceof AssetCatalogItem.LibraryFile libraryFile) {
            renderLibraryFileDetails(
                    context,
                    font,
                    thumbnailRegistry,
                    libraryFile.entry(),
                    textX,
                    textY
            );
        }
    }

    private void renderRegisteredAssetDetails(
            VRenderContext context,
            Font font,
            AssetRef asset,
            int x,
            int y
    ) {
        if (asset instanceof BuiltInTextureAssetRef builtInTexture) {
            drawLine(context, font, "Texture: " + builtInTexture.texture(), x, y, TEXT_COLOR);
            y += LINE_HEIGHT;

            drawLine(
                    context,
                    font,
                    "Size: "
                            + builtInTexture.textureWidth()
                            + "x"
                            + builtInTexture.textureHeight(),
                    x,
                    y,
                    TEXT_COLOR
            );
        } else {
            drawLine(context, font, "AssetRef: Unknown", x, y, TEXT_COLOR);
        }
    }

    private void renderLibraryFileDetails(
            VRenderContext context,
            Font font,
            AssetThumbnailRegistry thumbnailRegistry,
            AssetLibraryEntry entry,
            int x,
            int y
    ) {
        drawLine(context, font, "Path: " + entry.relativePath(), x, y, TEXT_COLOR);
        y += LINE_HEIGHT;

        drawLine(context, font, "File: " + entry.fileName(), x, y, TEXT_COLOR);
        y += LINE_HEIGHT;

        drawLine(context, font, "Ext: " + entry.extension(), x, y, TEXT_COLOR);
        y += LINE_HEIGHT;

        boolean thumbnailLoaded = thumbnailRegistry.contains(entry.id());

        drawLine(
                context,
                font,
                "Thumbnail: " + (thumbnailLoaded ? "Loaded" : "Unavailable"),
                x,
                y,
                thumbnailLoaded ? TEXT_COLOR : MUTED_TEXT_COLOR
        );
    }

    private int calculateDetailsPopupHeight(AssetCatalogItem item) {
        int previewHeight = POPUP_PREVIEW_SIZE + 6;

        int lines = 4;

        if (item instanceof AssetCatalogItem.RegisteredAsset) {
            lines += 2;
        } else if (item instanceof AssetCatalogItem.LibraryFile) {
            lines += 4;
        }

        return PADDING * 2 + previewHeight + lines * LINE_HEIGHT;
    }

    private int resolvePopupYForItem(
            VRenderContext context,
            List<AssetCatalogVisibleRow> rows,
            AssetCatalogItem detailsItem,
            int firstVisibleIndex,
            int maxVisibleRows
    ) {
        int panelY = getPanelY(context, PANEL_HEIGHT);

        int firstRowY = panelY + PADDING + LINE_HEIGHT + 4 + LINE_HEIGHT + 4;

        int visibleRows = Math.min(
                maxVisibleRows,
                rows.size() - firstVisibleIndex
        );

        for (int i = 0; i < visibleRows; i++) {
            AssetCatalogVisibleRow row = rows.get(firstVisibleIndex + i);

            if (!row.isItem()) {
                continue;
            }

            if (row.item().catalogItem().id().equals(detailsItem.id())) {
                return firstRowY + i * ROW_HEIGHT;
            }
        }

        return panelY;
    }

    private AssetCatalogItem resolveDetailsItem(
            List<AssetCatalogVisibleRow> rows,
            Optional<AssetCatalogVisibleRow> hoveredRow,
            AssetCatalogSelection selection
    ) {
        if (hoveredRow.isPresent() && hoveredRow.get().isItem()) {
            return hoveredRow.get().item().catalogItem();
        }

        if (selection == null || !selection.hasSelection()) {
            return null;
        }

        return rows.stream()
                .filter(AssetCatalogVisibleRow::isItem)
                .map(row -> row.item().catalogItem())
                .filter(item -> item.id().equals(selection.getSelectedItemId()))
                .findFirst()
                .orElse(null);
    }

    private List<AssetCatalogItem> getCatalogItems(
            AssetRegistry assetRegistry,
            AssetLibraryScanResult libraryScanResult,
            AssetCatalogFilter filter
    ) {
        List<AssetCatalogItem> items = new ArrayList<>();

        List<AssetRef> registeredAssets = new ArrayList<>(assetRegistry.getAll());
        registeredAssets.sort(Comparator.comparing(AssetRef::id));

        for (AssetRef asset : registeredAssets) {
            items.add(new AssetCatalogItem.RegisteredAsset(asset));
        }

        List<AssetLibraryEntry> libraryEntries = new ArrayList<>(libraryScanResult.entries());
        libraryEntries.sort(Comparator.comparing(AssetLibraryEntry::relativePath));

        for (AssetLibraryEntry entry : libraryEntries) {
            items.add(new AssetCatalogItem.LibraryFile(entry));
        }

        if (filter == null) {
            return items;
        }

        return items.stream()
                .filter(filter::accepts)
                .toList();
    }

    private int calculateMaxVisibleRows() {
        int headerHeight = PADDING + LINE_HEIGHT + 4 + LINE_HEIGHT + 4;

        int footerHeight = LINE_HEIGHT + PADDING;

        int availableHeight = PANEL_HEIGHT - headerHeight - footerHeight;

        return Math.max(1, availableHeight / ROW_HEIGHT);
    }

    private int clampFirstVisibleIndex(
            int scrollOffset,
            int rowCount,
            int maxVisibleRows
    ) {
        int maxScrollOffset = Math.max(0, rowCount - maxVisibleRows);

        return Math.max(0, Math.min(scrollOffset, maxScrollOffset));
    }

    private void renderScrollInfo(
            VRenderContext context,
            Font font,
            int totalRows,
            int firstVisibleIndex,
            int maxVisibleRows,
            int x,
            int y
    ) {
        if (totalRows <= maxVisibleRows) {
            drawLine(context, font, "Scroll: none", x, y, MUTED_TEXT_COLOR);
            return;
        }

        int lastVisibleIndex = Math.min(
                totalRows,
                firstVisibleIndex + maxVisibleRows
        );

        drawLine(
                context,
                font,
                "Showing "
                        + (firstVisibleIndex + 1)
                        + "-"
                        + lastVisibleIndex
                        + " / "
                        + totalRows,
                x,
                y,
                MUTED_TEXT_COLOR
        );
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) {
            return "";
        }

        if (maxLength <= 3) {
            return text.length() <= maxLength
                    ? text
                    : text.substring(0, maxLength);
        }

        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength - 3) + "...";
    }

    private int getPanelX() {
        return 10;
    }

    private int getPanelY(VRenderContext context, int panelHeight) {
        return getPanelY(context.screenHeight(), panelHeight);
    }

    private int getPanelY(int screenHeight, int panelHeight) {
        return screenHeight - panelHeight - 10;
    }

    private int clampPopupX(int popupX, int popupWidth, int screenWidth) {
        int minX = 10;
        int maxX = screenWidth - popupWidth - 10;

        if (maxX < minX) {
            return minX;
        }

        return Math.max(minX, Math.min(popupX, maxX));
    }

    private int clampPopupY(int popupY, int popupHeight, int screenHeight) {
        int minY = 10;
        int maxY = screenHeight - popupHeight - 10;

        if (maxY < minY) {
            return minY;
        }

        return Math.max(minY, Math.min(popupY, maxY));
    }

    private void renderPanelBackground(
            VRenderContext context,
            int x,
            int y,
            int width,
            int height
    ) {
        context.graphics().fill(
                x,
                y,
                x + width,
                y + height,
                PANEL_BACKGROUND
        );

        context.graphics().hLine(x, x + width, y, PANEL_BORDER);
        context.graphics().hLine(x, x + width, y + height, PANEL_BORDER);
        context.graphics().vLine(x, y, y + height, PANEL_BORDER);
        context.graphics().vLine(x + width, y, y + height, PANEL_BORDER);
    }

    private void drawLine(
            VRenderContext context,
            Font font,
            String text,
            int x,
            int y,
            int color
    ) {
        context.graphics().drawString(
                font,
                text,
                x,
                y,
                color,
                false
        );
    }
}