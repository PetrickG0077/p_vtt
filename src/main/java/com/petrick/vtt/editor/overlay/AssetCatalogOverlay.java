package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.feature.asset.AssetRef;
import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Painel debug que lista os assets disponíveis no AssetRegistry.
 *
 * Agora ele também permite detectar clique em um asset
 * para iniciar um drag até o canvas.
 */
public final class AssetCatalogOverlay {

    private static final int PANEL_WIDTH = 220;

    private static final int PADDING = 8;

    private static final int LINE_HEIGHT = 10;

    private static final int PANEL_BACKGROUND = 0xAA000000;

    private static final int PANEL_BORDER = 0xFFFFAA33;

    private static final int TITLE_COLOR = 0xFFFFFFFF;

    private static final int TEXT_COLOR = 0xFFDDDDDD;

    private static final int MUTED_TEXT_COLOR = 0xFFAAAAAA;

    private static final int DRAG_PREVIEW_BACKGROUND = 0xCC000000;

    private static final int DRAG_PREVIEW_BORDER = 0xFFFFAA33;

    private static final int MAX_VISIBLE_ASSETS = 8;

    public void render(
            VRenderContext context,
            Font font,
            AssetRegistry assetRegistry
    ) {
        List<AssetRef> assets = getSortedAssets(assetRegistry);

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

            drawLine(
                    context,
                    font,
                    "- " + asset.id(),
                    textX,
                    textY,
                    TEXT_COLOR
            );

            textY += LINE_HEIGHT;
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
            int rowTop = firstAssetY + i * LINE_HEIGHT;
            int rowBottom = rowTop + LINE_HEIGHT;

            if (mouseY >= rowTop && mouseY <= rowBottom) {
                return Optional.of(assets.get(i));
            }
        }

        return Optional.empty();
    }

    private List<AssetRef> getSortedAssets(AssetRegistry assetRegistry) {
        List<AssetRef> assets = new ArrayList<>(assetRegistry.getAll());
        assets.sort(Comparator.comparing(AssetRef::id));
        return assets;
    }

    private int calculatePanelHeight(int assetCount) {
        int visibleAssets = Math.min(assetCount, MAX_VISIBLE_ASSETS);

        int lines = 3 + visibleAssets;

        if (assetCount > MAX_VISIBLE_ASSETS) {
            lines++;
        }

        return PADDING * 2 + lines * LINE_HEIGHT + 8;
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