package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.feature.canvas.CanvasObjectState;
import com.petrick.vtt.feature.token.TokenDefinition;
import com.petrick.vtt.feature.token.TokenDefinitionRegistry;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Painel debug que lista tokens prontos disponíveis.
 *
 * Diferente do AssetCatalog:
 * - AssetCatalog mostra imagens/assets brutos.
 * - TokenCatalog mostra tokens reutilizáveis com tamanho, nome e estados.
 */
public final class TokenCatalogOverlay {

    private static final int PANEL_WIDTH = 220;

    private static final int PADDING = 8;

    private static final int LINE_HEIGHT = 10;

    private static final int PANEL_X = 240;

    private static final int PANEL_Y_OFFSET_FROM_BOTTOM = 10;

    private static final int MAX_VISIBLE_TOKENS = 8;

    private static final int MAX_VISIBLE_STATES = 5;

    private static final int PANEL_BACKGROUND = 0xAA000000;

    private static final int PANEL_BORDER = 0xFFAA66FF;

    private static final int TITLE_COLOR = 0xFFFFFFFF;

    private static final int TEXT_COLOR = 0xFFDDDDDD;

    private static final int MUTED_TEXT_COLOR = 0xFFAAAAAA;

    private static final int HOVER_TEXT_COLOR = 0xFFFFDD88;

    private static final int DRAG_PREVIEW_BACKGROUND = 0xCC000000;

    private static final int DRAG_PREVIEW_BORDER = 0xFFAA66FF;

    public void render(
            VRenderContext context,
            Font font,
            TokenDefinitionRegistry tokenDefinitionRegistry
    ) {
        List<TokenDefinition> definitions = getSortedDefinitions(tokenDefinitionRegistry);

        Optional<TokenDefinition> hoveredDefinition = findTokenDefinitionAt(
                tokenDefinitionRegistry,
                context.screenHeight(),
                context.mouseX(),
                context.mouseY()
        );

        int panelHeight = calculatePanelHeight(definitions.size(), hoveredDefinition.orElse(null));

        int x = PANEL_X;
        int y = getPanelY(context.screenHeight(), panelHeight);

        renderPanelBackground(context, x, y, PANEL_WIDTH, panelHeight);

        int textX = x + PADDING;
        int textY = y + PADDING;

        drawLine(context, font, "Token Catalog", textX, textY, TITLE_COLOR);
        textY += LINE_HEIGHT + 4;

        drawLine(
                context,
                font,
                "Tokens: " + definitions.size(),
                textX,
                textY,
                TEXT_COLOR
        );
        textY += LINE_HEIGHT + 4;

        if (definitions.isEmpty()) {
            drawLine(context, font, "No token definitions", textX, textY, MUTED_TEXT_COLOR);
            return;
        }

        int visibleDefinitions = Math.min(definitions.size(), MAX_VISIBLE_TOKENS);

        for (int i = 0; i < visibleDefinitions; i++) {
            TokenDefinition definition = definitions.get(i);

            boolean hovered = hoveredDefinition
                    .map(tokenDefinition -> tokenDefinition.id().equals(definition.id()))
                    .orElse(false);

            String text = "- " + definition.displayName()
                    + " {"
                    + definition.states().size()
                    + " states}";

            drawLine(
                    context,
                    font,
                    text,
                    textX,
                    textY,
                    hovered ? HOVER_TEXT_COLOR : TEXT_COLOR
            );

            textY += LINE_HEIGHT;
        }

        if (definitions.size() > MAX_VISIBLE_TOKENS) {
            int remaining = definitions.size() - MAX_VISIBLE_TOKENS;

            drawLine(
                    context,
                    font,
                    "... +" + remaining + " more",
                    textX,
                    textY,
                    MUTED_TEXT_COLOR
            );

            textY += LINE_HEIGHT;
        }

        if (hoveredDefinition.isPresent()) {
            textY += 6;
            renderTokenDetails(context, font, hoveredDefinition.get(), textX, textY);
        }
    }

    public void renderDragPreview(
            VRenderContext context,
            Font font,
            TokenDefinition definition,
            double mouseX,
            double mouseY
    ) {
        String text = "Create token: " + definition.displayName();

        int x = (int) Math.round(mouseX) + 12;
        int y = (int) Math.round(mouseY) + 12;

        int width = font.width(text) + PADDING * 2;
        int height = PADDING * 2 + LINE_HEIGHT;

        context.graphics().fill(
                x,
                y,
                x + width,
                y + height,
                DRAG_PREVIEW_BACKGROUND
        );

        context.graphics().hLine(x, x + width, y, DRAG_PREVIEW_BORDER);
        context.graphics().hLine(x, x + width, y + height, DRAG_PREVIEW_BORDER);
        context.graphics().vLine(x, y, y + height, DRAG_PREVIEW_BORDER);
        context.graphics().vLine(x + width, y, y + height, DRAG_PREVIEW_BORDER);

        drawLine(
                context,
                font,
                text,
                x + PADDING,
                y + PADDING,
                TEXT_COLOR
        );
    }

    public Optional<TokenDefinition> findTokenDefinitionAt(
            TokenDefinitionRegistry tokenDefinitionRegistry,
            int screenHeight,
            double mouseX,
            double mouseY
    ) {
        List<TokenDefinition> definitions = getSortedDefinitions(tokenDefinitionRegistry);

        if (definitions.isEmpty()) {
            return Optional.empty();
        }

        int panelHeight = calculatePanelHeight(definitions.size(), null);

        int panelX = PANEL_X;
        int panelY = getPanelY(screenHeight, panelHeight);

        if (mouseX < panelX || mouseX > panelX + PANEL_WIDTH) {
            return Optional.empty();
        }

        if (mouseY < panelY || mouseY > panelY + panelHeight) {
            return Optional.empty();
        }

        int firstTokenY = panelY + PADDING + LINE_HEIGHT + 4 + LINE_HEIGHT + 4;

        int visibleDefinitions = Math.min(definitions.size(), MAX_VISIBLE_TOKENS);

        for (int i = 0; i < visibleDefinitions; i++) {
            int rowTop = firstTokenY + i * LINE_HEIGHT;
            int rowBottom = rowTop + LINE_HEIGHT;

            if (mouseY >= rowTop && mouseY <= rowBottom) {
                return Optional.of(definitions.get(i));
            }
        }

        return Optional.empty();
    }

    private void renderTokenDetails(
            VRenderContext context,
            Font font,
            TokenDefinition definition,
            int x,
            int y
    ) {
        drawLine(context, font, "Details:", x, y, TITLE_COLOR);
        y += LINE_HEIGHT;

        drawLine(context, font, "ID: " + definition.id(), x, y, MUTED_TEXT_COLOR);
        y += LINE_HEIGHT;

        drawLine(
                context,
                font,
                "Size: "
                        + formatNumber(definition.defaultSize().x())
                        + "x"
                        + formatNumber(definition.defaultSize().y()),
                x,
                y,
                TEXT_COLOR
        );
        y += LINE_HEIGHT;

        drawLine(
                context,
                font,
                "Default State: " + definition.defaultStateId(),
                x,
                y,
                TEXT_COLOR
        );
        y += LINE_HEIGHT;

        drawLine(context, font, "States:", x, y, MUTED_TEXT_COLOR);
        y += LINE_HEIGHT;

        int index = 0;

        for (CanvasObjectState state : definition.states().values()) {
            if (index >= MAX_VISIBLE_STATES) {
                int remaining = definition.states().size() - MAX_VISIBLE_STATES;

                drawLine(
                        context,
                        font,
                        "... +" + remaining + " more",
                        x,
                        y,
                        MUTED_TEXT_COLOR
                );

                return;
            }

            String prefix = state.id().equals(definition.defaultStateId()) ? "> " : "  ";

            drawLine(
                    context,
                    font,
                    prefix + state.id() + " - " + state.displayName(),
                    x,
                    y,
                    state.id().equals(definition.defaultStateId()) ? HOVER_TEXT_COLOR : TEXT_COLOR
            );

            y += LINE_HEIGHT;
            index++;
        }
    }

    private List<TokenDefinition> getSortedDefinitions(TokenDefinitionRegistry tokenDefinitionRegistry) {
        List<TokenDefinition> definitions = new ArrayList<>(tokenDefinitionRegistry.getAll());

        definitions.sort(Comparator.comparing(TokenDefinition::displayName));

        return definitions;
    }

    private int calculatePanelHeight(int tokenCount, TokenDefinition hoveredDefinition) {
        int visibleDefinitions = Math.min(tokenCount, MAX_VISIBLE_TOKENS);

        int lines = 3 + visibleDefinitions;

        if (tokenCount > MAX_VISIBLE_TOKENS) {
            lines++;
        }

        if (hoveredDefinition != null) {
            int visibleStates = Math.min(hoveredDefinition.states().size(), MAX_VISIBLE_STATES);

            lines += 6 + visibleStates;

            if (hoveredDefinition.states().size() > MAX_VISIBLE_STATES) {
                lines++;
            }
        }

        return PADDING * 2 + lines * LINE_HEIGHT + 8;
    }

    private int getPanelY(int screenHeight, int panelHeight) {
        return screenHeight - panelHeight - PANEL_Y_OFFSET_FROM_BOTTOM;
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

    private String formatNumber(double value) {
        if (Math.floor(value) == value) {
            return Integer.toString((int) value);
        }

        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}