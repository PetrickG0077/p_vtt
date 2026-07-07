package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.editor.catalog.AssetCatalogSelection;
import com.petrick.vtt.feature.asset.AssetRef;
import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.feature.asset.BuiltInTextureAssetRef;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Painel debug que lista os assets disponíveis no AssetRegistry.
 *
 * O Asset Catalog é apenas visualização/inspeção:
 * - mostra assets registrados;
 * - permite selecionar asset;
 * - mostra popup de detalhes;
 * - mostra miniaturas;
 * - não cria tokens;
 * - não arrasta assets para o canvas.
 */
public final class AssetCatalogOverlay {

    private static final int PANEL_WIDTH = 240;

    private static final int PADDING = 8;

    private static final int LINE_HEIGHT = 10;

    private static final int ASSET_ROW_HEIGHT = 30;

    private static final int THUMBNAIL_SIZE = 24;

    private static final int POPUP_PREVIEW_SIZE = 48;

    private static final int PANEL_BACKGROUND = 0xAA000000;

    private static final int PANEL_BORDER = 0xFFFFAA33;

    private static final int TITLE_COLOR = 0xFFFFFFFF;

    private static final int TEXT_COLOR = 0xFFDDDDDD;

    private static final int MUTED_TEXT_COLOR = 0xFFAAAAAA;

    private static final int HOVER_TEXT_COLOR = 0xFFFFDD88;

    private static final int SELECTED_ROW_BACKGROUND = 0x55FFAA33;

    private static final int HOVERED_ROW_BACKGROUND = 0x33222222;

    private static final int DETAILS_POPUP_WIDTH = 260;

    private static final int MAX_VISIBLE_ASSETS = 8;

    public void render(
            VRenderContext context,
            Font font,
            AssetRegistry assetRegistry,
            AssetCatalogSelection selection
    ) {
        List<AssetRef> assets = getSortedAssets(assetRegistry);

        Optional<AssetRef> hoveredAsset = findAssetAt(
                assetRegistry,
                context.screenHeight(),
                context.mouseX(),
                context.mouseY()
        );

        AssetRef detailsAsset = resolveDetailsAsset(
                assetRegistry,
                hoveredAsset,
                selection
        );

        int panelHeight = calculatePanelHeight(assets.size());

        int x = getPanelX();
        int y = getPanelY(context, panelHeight);

        renderPanelBackground(context, x, y, PANEL_WIDTH, panelHeight);

        int textX = x + PADDING;
        int textY = y + PADDING;

        drawLine(context, font, "Asset Catalog", textX, textY, TITLE_COLOR);
        textY += LINE_HEIGHT + 4;

        drawLine(
                context,
                font,
                "Assets: " + assets.size(),
                textX,
                textY,
                TEXT_COLOR
        );
        textY += LINE_HEIGHT + 4;

        if (assets.isEmpty()) {
            drawLine(context, font, "No assets registered", textX, textY, MUTED_TEXT_COLOR);
            return;
        }

        int visibleAssets = Math.min(assets.size(), MAX_VISIBLE_ASSETS);

        for (int i = 0; i < visibleAssets; i++) {
            AssetRef asset = assets.get(i);

            boolean hovered = hoveredAsset
                    .map(assetRef -> assetRef.id().equals(asset.id()))
                    .orElse(false);

            boolean selected = selection != null && selection.isSelected(asset.id());

            renderAssetRow(
                    context,
                    font,
                    asset,
                    textX,
                    textY,
                    hovered,
                    selected
            );

            textY += ASSET_ROW_HEIGHT;
        }

        if (assets.size() > MAX_VISIBLE_ASSETS) {
            int remaining = assets.size() - MAX_VISIBLE_ASSETS;

            drawLine(
                    context,
                    font,
                    "... +" + remaining + " more",
                    textX,
                    textY,
                    MUTED_TEXT_COLOR
            );
        }

        if (detailsAsset != null) {
            int popupX;
            int popupY;

            if (hoveredAsset.isPresent()) {
                popupX = (int) Math.round(context.mouseX()) + 14;
                popupY = (int) Math.round(context.mouseY())
                        - calculateDetailsPopupHeight(detailsAsset)
                        - 14;
            } else {
                popupX = x + PANEL_WIDTH + 8;
                popupY = resolvePopupYForAsset(
                        context,
                        assets,
                        detailsAsset
                );
            }

            renderAssetDetailsPopup(
                    context,
                    font,
                    detailsAsset,
                    popupX,
                    popupY
            );
        }
    }

    private void renderAssetRow(
            VRenderContext context,
            Font font,
            AssetRef asset,
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

        renderAssetThumbnail(
                context,
                asset,
                thumbnailX,
                thumbnailY,
                THUMBNAIL_SIZE,
                THUMBNAIL_SIZE
        );

        int textX = x + THUMBNAIL_SIZE + 8;

        drawLine(
                context,
                font,
                asset.id(),
                textX,
                y + 4,
                selected ? TITLE_COLOR : hovered ? HOVER_TEXT_COLOR : TEXT_COLOR
        );

        drawLine(
                context,
                font,
                getAssetTypeName(asset),
                textX,
                y + 16,
                MUTED_TEXT_COLOR
        );
    }

    private void renderAssetThumbnail(
            VRenderContext context,
            AssetRef asset,
            int x,
            int y,
            int width,
            int height
    ) {
        if (asset instanceof BuiltInTextureAssetRef builtInTexture) {
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

    public Optional<AssetRef> findAssetAt(
            AssetRegistry assetRegistry,
            int screenHeight,
            double mouseX,
            double mouseY
    ) {
        List<AssetRef> assets = getSortedAssets(assetRegistry);

        if (assets.isEmpty()) {
            return Optional.empty();
        }

        int panelHeight = calculatePanelHeight(assets.size());

        int panelX = getPanelX();
        int panelY = getPanelY(screenHeight, panelHeight);

        if (mouseX < panelX || mouseX > panelX + PANEL_WIDTH) {
            return Optional.empty();
        }

        if (mouseY < panelY || mouseY > panelY + panelHeight) {
            return Optional.empty();
        }

        int firstAssetY = panelY + PADDING + LINE_HEIGHT + 4 + LINE_HEIGHT + 4;

        int visibleAssets = Math.min(assets.size(), MAX_VISIBLE_ASSETS);

        for (int i = 0; i < visibleAssets; i++) {
            int rowTop = firstAssetY + i * ASSET_ROW_HEIGHT;
            int rowBottom = rowTop + ASSET_ROW_HEIGHT;

            if (mouseY >= rowTop && mouseY <= rowBottom) {
                return Optional.of(assets.get(i));
            }
        }

        return Optional.empty();
    }

    public boolean containsPoint(
            AssetRegistry assetRegistry,
            int screenHeight,
            double mouseX,
            double mouseY
    ) {
        int panelHeight = calculatePanelHeight(
                assetRegistry.getAll().size()
        );

        int panelX = getPanelX();
        int panelY = getPanelY(screenHeight, panelHeight);

        return mouseX >= panelX
                && mouseX <= panelX + PANEL_WIDTH
                && mouseY >= panelY
                && mouseY <= panelY + panelHeight;
    }

    private void renderAssetDetailsPopup(
            VRenderContext context,
            Font font,
            AssetRef asset,
            int x,
            int y
    ) {
        int popupHeight = calculateDetailsPopupHeight(asset);

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

        renderAssetThumbnail(
                context,
                asset,
                textX,
                textY,
                POPUP_PREVIEW_SIZE,
                POPUP_PREVIEW_SIZE
        );

        textY += POPUP_PREVIEW_SIZE + 6;

        drawLine(context, font, "Asset Details:", textX, textY, TITLE_COLOR);
        textY += LINE_HEIGHT;

        drawLine(context, font, "ID: " + asset.id(), textX, textY, MUTED_TEXT_COLOR);
        textY += LINE_HEIGHT;

        if (asset instanceof BuiltInTextureAssetRef builtInTexture) {
            drawLine(context, font, "Type: Built-in Texture", textX, textY, TEXT_COLOR);
            textY += LINE_HEIGHT;

            drawLine(
                    context,
                    font,
                    "Texture: " + builtInTexture.texture(),
                    textX,
                    textY,
                    TEXT_COLOR
            );
            textY += LINE_HEIGHT;

            drawLine(
                    context,
                    font,
                    "Size: "
                            + builtInTexture.textureWidth()
                            + "x"
                            + builtInTexture.textureHeight(),
                    textX,
                    textY,
                    TEXT_COLOR
            );
        } else {
            drawLine(context, font, "Type: Unknown AssetRef", textX, textY, TEXT_COLOR);
        }
    }

    private int calculateDetailsPopupHeight(AssetRef asset) {
        int previewHeight = POPUP_PREVIEW_SIZE + 6;

        if (asset instanceof BuiltInTextureAssetRef) {
            return PADDING * 2 + previewHeight + 5 * LINE_HEIGHT;
        }

        return PADDING * 2 + previewHeight + 3 * LINE_HEIGHT;
    }

    private int resolvePopupYForAsset(
            VRenderContext context,
            List<AssetRef> assets,
            AssetRef detailsAsset
    ) {
        int panelHeight = calculatePanelHeight(assets.size());
        int panelY = getPanelY(context, panelHeight);

        int firstAssetY = panelY + PADDING + LINE_HEIGHT + 4 + LINE_HEIGHT + 4;

        int visibleAssets = Math.min(assets.size(), MAX_VISIBLE_ASSETS);

        for (int i = 0; i < visibleAssets; i++) {
            AssetRef asset = assets.get(i);

            if (asset.id().equals(detailsAsset.id())) {
                return firstAssetY + i * ASSET_ROW_HEIGHT;
            }
        }

        return panelY;
    }

    private AssetRef resolveDetailsAsset(
            AssetRegistry assetRegistry,
            Optional<AssetRef> hoveredAsset,
            AssetCatalogSelection selection
    ) {
        if (hoveredAsset.isPresent()) {
            return hoveredAsset.get();
        }

        if (selection == null || !selection.hasSelection()) {
            return null;
        }

        return assetRegistry
                .findById(selection.getSelectedAssetId())
                .orElse(null);
    }

    private String getAssetTypeName(AssetRef asset) {
        if (asset instanceof BuiltInTextureAssetRef) {
            return "Built-in Texture";
        }

        return "Unknown Asset";
    }

    private List<AssetRef> getSortedAssets(AssetRegistry assetRegistry) {
        List<AssetRef> assets = new ArrayList<>(assetRegistry.getAll());
        assets.sort(Comparator.comparing(AssetRef::id));
        return assets;
    }

    private int calculatePanelHeight(int assetCount) {
        int visibleAssets = Math.min(assetCount, MAX_VISIBLE_ASSETS);

        int headerHeight = PADDING + LINE_HEIGHT + 4 + LINE_HEIGHT + 4;

        int assetListHeight = visibleAssets * ASSET_ROW_HEIGHT;

        int extraHeight = 0;

        if (assetCount > MAX_VISIBLE_ASSETS) {
            extraHeight += LINE_HEIGHT;
        }

        return headerHeight + assetListHeight + extraHeight + PADDING + 8;
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