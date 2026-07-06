package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.feature.asset.AssetRef;
import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Painel debug que lista os assets disponíveis no AssetRegistry.
 *
 * Por enquanto é apenas visual.
 * Futuramente poderá permitir clicar em um asset para criar tokens no canvas.
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

    public void render(
            VRenderContext context,
            Font font,
            AssetRegistry assetRegistry
    ) {
        List<AssetRef> assets = new ArrayList<>(assetRegistry.getAll());

        assets.sort(Comparator.comparing(AssetRef::id));

        int maxVisibleAssets = 8;
        int visibleAssets = Math.min(assets.size(), maxVisibleAssets);

        int lines = 3 + visibleAssets;

        if (assets.size() > maxVisibleAssets) {
            lines++;
        }

        int panelHeight = PADDING * 2 + lines * LINE_HEIGHT + 8;

        int x = 10;
        int y = context.screenHeight() - panelHeight - 10;

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

        if (assets.size() > maxVisibleAssets) {
            int remaining = assets.size() - maxVisibleAssets;

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