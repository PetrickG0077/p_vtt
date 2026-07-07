package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.editor.catalog.AssetCatalogItem;
import com.petrick.vtt.editor.catalog.AssetCatalogSelection;
import com.petrick.vtt.feature.asset.AssetRef;
import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.feature.asset.BuiltInTextureAssetRef;
import com.petrick.vtt.feature.asset.library.AssetLibraryEntry;
import com.petrick.vtt.feature.asset.library.AssetLibraryFileType;
import com.petrick.vtt.feature.asset.library.AssetLibraryScanResult;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Painel que lista assets disponíveis.
 *
 * Mostra:
 * - assets registrados em memória;
 * - arquivos detectados na biblioteca real.
 *
 * Por enquanto arquivos da biblioteca usam placeholder.
 * Textura real de arquivos externos fica para um passo futuro.
 */
public final class AssetCatalogOverlay {

    private static final int PANEL_WIDTH = 210;

    private static final int PANEL_HEIGHT = 150;

    private static final int PADDING = 8;

    private static final int LINE_HEIGHT = 10;

    private static final int ASSET_ROW_HEIGHT = 26;

    private static final int THUMBNAIL_SIZE = 20;

    private static final int POPUP_PREVIEW_SIZE = 48;

    private static final int PANEL_BACKGROUND = 0xAA000000;

    private static final int PANEL_BORDER = 0xFFFFAA33;

    private static final int TITLE_COLOR = 0xFFFFFFFF;

    private static final int TEXT_COLOR = 0xFFDDDDDD;

    private static final int MUTED_TEXT_COLOR = 0xFFAAAAAA;

    private static final int HOVER_TEXT_COLOR = 0xFFFFDD88;

    private static final int SELECTED_ROW_BACKGROUND = 0x55FFAA33;

    private static final int HOVERED_ROW_BACKGROUND = 0x33222222;

    private static final int DETAILS_POPUP_WIDTH = 285;

    private static final int SCROLL_STEP = 1;

    public void render(
            VRenderContext context,
            Font font,
            AssetRegistry assetRegistry,
            AssetLibraryScanResult libraryScanResult,
            AssetCatalogSelection selection,
            int scrollOffset
    ) {
        List<AssetCatalogItem> items = getCatalogItems(assetRegistry, libraryScanResult);

        Optional<AssetCatalogItem> hoveredItem = findItemAt(
                assetRegistry,
                libraryScanResult,
                context.screenHeight(),
                context.mouseX(),
                context.mouseY(),
                scrollOffset
        );

        AssetCatalogItem detailsItem = resolveDetailsItem(
                items,
                hoveredItem,
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
                "Items: " + items.size(),
                textX,
                textY,
                TEXT_COLOR
        );
        textY += LINE_HEIGHT + 4;

        if (items.isEmpty()) {
            drawLine(context, font, "No assets found", textX, textY, MUTED_TEXT_COLOR);
            return;
        }

        int maxVisibleItems = calculateMaxVisibleItems();

        int firstVisibleIndex = clampFirstVisibleIndex(
                scrollOffset,
                items.size(),
                maxVisibleItems
        );

        int visibleItems = Math.min(
                maxVisibleItems,
                items.size() - firstVisibleIndex
        );

        for (int i = 0; i < visibleItems; i++) {
            AssetCatalogItem item = items.get(firstVisibleIndex + i);

            boolean hovered = hoveredItem
                    .map(assetCatalogItem -> assetCatalogItem.id().equals(item.id()))
                    .orElse(false);

            boolean selected = selection != null && selection.isSelected(item.id());

            renderItemRow(
                    context,
                    font,
                    item,
                    textX,
                    textY,
                    hovered,
                    selected
            );

            textY += ASSET_ROW_HEIGHT;
        }

        renderScrollInfo(
                context,
                font,
                items.size(),
                firstVisibleIndex,
                maxVisibleItems,
                textX,
                y + PANEL_HEIGHT - PADDING - LINE_HEIGHT
        );

        if (detailsItem != null) {
            int popupX;
            int popupY;

            if (hoveredItem.isPresent()) {
                popupX = (int) Math.round(context.mouseX()) + 14;
                popupY = (int) Math.round(context.mouseY())
                        - calculateDetailsPopupHeight(detailsItem)
                        - 14;
            } else {
                popupX = x + PANEL_WIDTH + 8;
                popupY = resolvePopupYForItem(
                        context,
                        items,
                        detailsItem,
                        firstVisibleIndex,
                        maxVisibleItems
                );
            }

            renderItemDetailsPopup(
                    context,
                    font,
                    detailsItem,
                    popupX,
                    popupY
            );
        }
    }

    private void renderItemRow(
            VRenderContext context,
            Font font,
            AssetCatalogItem item,
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
                    y + ASSET_ROW_HEIGHT,
                    rowBackground
            );
        }

        int thumbnailX = x;
        int thumbnailY = y + 3;

        renderItemThumbnail(
                context,
                item,
                thumbnailX,
                thumbnailY,
                THUMBNAIL_SIZE,
                THUMBNAIL_SIZE
        );

        int textX = x + THUMBNAIL_SIZE + 8;

        drawLine(
                context,
                font,
                item.displayName(),
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
            renderLibraryFilePlaceholder(
                    context,
                    libraryFile.entry(),
                    x,
                    y,
                    width,
                    height
            );
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

    public Optional<AssetCatalogItem> findItemAt(
            AssetRegistry assetRegistry,
            AssetLibraryScanResult libraryScanResult,
            int screenHeight,
            double mouseX,
            double mouseY,
            int scrollOffset
    ) {
        List<AssetCatalogItem> items = getCatalogItems(assetRegistry, libraryScanResult);

        if (items.isEmpty()) {
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

        int firstItemY = panelY + PADDING + LINE_HEIGHT + 4 + LINE_HEIGHT + 4;

        int maxVisibleItems = calculateMaxVisibleItems();

        int firstVisibleIndex = clampFirstVisibleIndex(
                scrollOffset,
                items.size(),
                maxVisibleItems
        );

        int visibleItems = Math.min(
                maxVisibleItems,
                items.size() - firstVisibleIndex
        );

        for (int i = 0; i < visibleItems; i++) {
            int rowTop = firstItemY + i * ASSET_ROW_HEIGHT;
            int rowBottom = rowTop + ASSET_ROW_HEIGHT;

            if (mouseY >= rowTop && mouseY <= rowBottom) {
                return Optional.of(items.get(firstVisibleIndex + i));
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
            int currentScrollOffset,
            double scrollY
    ) {
        int direction = scrollY < 0 ? 1 : -1;

        int newOffset = currentScrollOffset + direction * SCROLL_STEP;

        return clampScrollOffset(
                assetRegistry,
                libraryScanResult,
                newOffset
        );
    }

    public int clampScrollOffset(
            AssetRegistry assetRegistry,
            AssetLibraryScanResult libraryScanResult,
            int scrollOffset
    ) {
        int itemCount = getCatalogItems(assetRegistry, libraryScanResult).size();

        int maxVisibleItems = calculateMaxVisibleItems();

        int maxScrollOffset = Math.max(0, itemCount - maxVisibleItems);

        return Math.max(0, Math.min(scrollOffset, maxScrollOffset));
    }

    private void renderItemDetailsPopup(
            VRenderContext context,
            Font font,
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
            AssetLibraryEntry entry,
            int x,
            int y
    ) {
        drawLine(context, font, "Path: " + entry.relativePath(), x, y, TEXT_COLOR);
        y += LINE_HEIGHT;

        drawLine(context, font, "File: " + entry.fileName(), x, y, TEXT_COLOR);
        y += LINE_HEIGHT;

        drawLine(context, font, "Ext: " + entry.extension(), x, y, TEXT_COLOR);
    }

    private int calculateDetailsPopupHeight(AssetCatalogItem item) {
        int previewHeight = POPUP_PREVIEW_SIZE + 6;

        int lines = 4;

        if (item instanceof AssetCatalogItem.RegisteredAsset) {
            lines += 2;
        } else if (item instanceof AssetCatalogItem.LibraryFile) {
            lines += 3;
        }

        return PADDING * 2 + previewHeight + lines * LINE_HEIGHT;
    }

    private int resolvePopupYForItem(
            VRenderContext context,
            List<AssetCatalogItem> items,
            AssetCatalogItem detailsItem,
            int firstVisibleIndex,
            int maxVisibleItems
    ) {
        int panelY = getPanelY(context, PANEL_HEIGHT);

        int firstItemY = panelY + PADDING + LINE_HEIGHT + 4 + LINE_HEIGHT + 4;

        int visibleItems = Math.min(
                maxVisibleItems,
                items.size() - firstVisibleIndex
        );

        for (int i = 0; i < visibleItems; i++) {
            AssetCatalogItem item = items.get(firstVisibleIndex + i);

            if (item.id().equals(detailsItem.id())) {
                return firstItemY + i * ASSET_ROW_HEIGHT;
            }
        }

        return panelY;
    }

    private AssetCatalogItem resolveDetailsItem(
            List<AssetCatalogItem> items,
            Optional<AssetCatalogItem> hoveredItem,
            AssetCatalogSelection selection
    ) {
        if (hoveredItem.isPresent()) {
            return hoveredItem.get();
        }

        if (selection == null || !selection.hasSelection()) {
            return null;
        }

        return items.stream()
                .filter(item -> item.id().equals(selection.getSelectedItemId()))
                .findFirst()
                .orElse(null);
    }

    private List<AssetCatalogItem> getCatalogItems(
            AssetRegistry assetRegistry,
            AssetLibraryScanResult libraryScanResult
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

        return items;
    }

    private int calculateMaxVisibleItems() {
        int headerHeight = PADDING + LINE_HEIGHT + 4 + LINE_HEIGHT + 4;

        int footerHeight = LINE_HEIGHT + PADDING;

        int availableHeight = PANEL_HEIGHT - headerHeight - footerHeight;

        return Math.max(1, availableHeight / ASSET_ROW_HEIGHT);
    }

    private int clampFirstVisibleIndex(
            int scrollOffset,
            int itemCount,
            int maxVisibleItems
    ) {
        int maxScrollOffset = Math.max(0, itemCount - maxVisibleItems);

        return Math.max(0, Math.min(scrollOffset, maxScrollOffset));
    }

    private void renderScrollInfo(
            VRenderContext context,
            Font font,
            int totalItems,
            int firstVisibleIndex,
            int maxVisibleItems,
            int x,
            int y
    ) {
        if (totalItems <= maxVisibleItems) {
            drawLine(context, font, "Scroll: none", x, y, MUTED_TEXT_COLOR);
            return;
        }

        int lastVisibleIndex = Math.min(
                totalItems,
                firstVisibleIndex + maxVisibleItems
        );

        drawLine(
                context,
                font,
                "Showing "
                        + (firstVisibleIndex + 1)
                        + "-"
                        + lastVisibleIndex
                        + " / "
                        + totalItems,
                x,
                y,
                MUTED_TEXT_COLOR
        );
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