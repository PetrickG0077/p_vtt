package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.editor.catalog.TokenCatalogSelection;
import com.petrick.vtt.feature.canvas.CanvasObjectState;
import com.petrick.vtt.feature.canvas.visual.CanvasVisual;
import com.petrick.vtt.feature.canvas.visual.CanvasVisualRenderer;
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
 *
 * Nesta versão:
 * - a lista do catálogo permanece fixa;
 * - os detalhes aparecem em um popup separado;
 * - hover e seleção podem exibir o popup.
 */
public final class TokenCatalogOverlay {

    private static final int PANEL_WIDTH = 240;

    private static final int PADDING = 8;

    private static final int LINE_HEIGHT = 10;

    private static final int TOKEN_ROW_HEIGHT = 30;

    private static final int THUMBNAIL_SIZE = 24;

    private static final int PANEL_X = 240;

    private static final int PANEL_Y_OFFSET_FROM_BOTTOM = 10;

    private static final int MAX_VISIBLE_TOKENS = 8;

    private static final int MAX_VISIBLE_STATES = 5;

    private static final int PANEL_BACKGROUND = 0xAA000000;

    private static final int PANEL_BORDER = 0xFFAA66FF;

    private static final int THUMBNAIL_BORDER = 0xFFDDDDDD;

    private static final int TITLE_COLOR = 0xFFFFFFFF;

    private static final int TEXT_COLOR = 0xFFDDDDDD;

    private static final int MUTED_TEXT_COLOR = 0xFFAAAAAA;

    private static final int HOVER_TEXT_COLOR = 0xFFFFDD88;

    private static final int SELECTED_ROW_BACKGROUND = 0x553399FF;

    private static final int HOVERED_ROW_BACKGROUND = 0x33222222;

    private static final int DRAG_PREVIEW_BACKGROUND = 0xCC000000;

    private static final int DRAG_PREVIEW_BORDER = 0xFFAA66FF;

    private static final int DETAILS_POPUP_WIDTH = 230;

    private static final int DETAILS_POPUP_GAP = 8;

    private final CanvasVisualRenderer visualRenderer;

    public TokenCatalogOverlay() {
        this.visualRenderer = new CanvasVisualRenderer();
    }

    public void render(
            VRenderContext context,
            Font font,
            TokenDefinitionRegistry tokenDefinitionRegistry,
            TokenCatalogSelection selection
    ) {
        List<TokenDefinition> definitions = getSortedDefinitions(tokenDefinitionRegistry);

        Optional<TokenDefinition> hoveredDefinition = findTokenDefinitionAt(
                tokenDefinitionRegistry,
                context.screenHeight(),
                context.mouseX(),
                context.mouseY()
        );

        TokenDefinition detailsDefinition = resolveDetailsDefinition(
                tokenDefinitionRegistry,
                hoveredDefinition,
                selection
        );

        int panelHeight = calculatePanelHeight(definitions.size());

        int x = PANEL_X;
        int y = getPanelY(context.screenHeight(), panelHeight);

        renderPanelBackground(context, x, y, PANEL_WIDTH, panelHeight);

        int textX = x + PADDING;
        int textY = y + PADDING;

        drawLine(context, font, "Token Catalog", textX, textY, TITLE_COLOR);
        textY += LINE_HEIGHT + 4;

        renderCreateTokenButton(
                context,
                font,
                x + PANEL_WIDTH - PADDING - 18,
                y + PADDING,
                isCreateTokenButtonAt(
                        tokenDefinitionRegistry,
                        context.screenHeight(),
                        context.mouseX(),
                        context.mouseY()
                )
        );

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

            boolean selected = selection != null && selection.isSelected(definition.id());

            renderTokenRow(
                    context,
                    font,
                    definition,
                    textX,
                    textY,
                    hovered,
                    selected
            );

            textY += TOKEN_ROW_HEIGHT;
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
        }

        if (detailsDefinition != null) {
            int popupX;
            int popupY;

            if (hoveredDefinition.isPresent()) {
                popupX = (int) Math.round(context.mouseX()) + 14;
                popupY = (int) Math.round(context.mouseY())
                        - calculateDetailsPopupHeight(detailsDefinition)
                        - 14;
            } else {
                popupX = x + PANEL_WIDTH + DETAILS_POPUP_GAP;
                popupY = resolvePopupYForDefinition(
                        context,
                        definitions,
                        detailsDefinition
                );
            }

            renderTokenDetailsPopup(
                    context,
                    font,
                    detailsDefinition,
                    popupX,
                    popupY
            );
        }
    }

    public boolean containsPoint(
            TokenDefinitionRegistry tokenDefinitionRegistry,
            int screenHeight,
            double mouseX,
            double mouseY
    ) {
        int panelHeight = calculatePanelHeight(
                tokenDefinitionRegistry.getAll().size()
        );

        int panelX = PANEL_X;
        int panelY = getPanelY(screenHeight, panelHeight);

        return mouseX >= panelX
                && mouseX <= panelX + PANEL_WIDTH
                && mouseY >= panelY
                && mouseY <= panelY + panelHeight;
    }

    private void renderCreateTokenButton(
            VRenderContext context,
            Font font,
            int x,
            int y,
            boolean hovered
    ) {
        int size = 18;

        int borderColor = hovered ? 0xFFFFAA33 : PANEL_BORDER;

        context.graphics().fill(
                x,
                y,
                x + size,
                y + size,
                0xAA000000
        );

        context.graphics().hLine(x, x + size, y, borderColor);
        context.graphics().hLine(x, x + size, y + size, borderColor);
        context.graphics().vLine(x, y, y + size, borderColor);
        context.graphics().vLine(x + size, y, y + size, borderColor);

        String text = "+";

        context.graphics().drawString(
                font,
                text,
                x + size / 2 - font.width(text) / 2 + 1,
                y + size / 2 - 4 + 1,
                0xFFFFFFFF,
                false
        );
    }

    private void renderTokenRow(
            VRenderContext context,
            Font font,
            TokenDefinition definition,
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
                    y + TOKEN_ROW_HEIGHT,
                    rowBackground
            );
        }

        int thumbnailX = x;
        int thumbnailY = y + 2;

        renderTokenThumbnail(
                context,
                definition,
                thumbnailX,
                thumbnailY,
                THUMBNAIL_SIZE,
                THUMBNAIL_SIZE
        );

        int textX = x + THUMBNAIL_SIZE + 8;
        int titleY = y + 3;
        int subtitleY = y + 15;

        drawLine(
                context,
                font,
                definition.displayName(),
                textX,
                titleY,
                selected ? TITLE_COLOR : hovered ? HOVER_TEXT_COLOR : TEXT_COLOR
        );

        drawLine(
                context,
                font,
                definition.states().size() + " states",
                textX,
                subtitleY,
                MUTED_TEXT_COLOR
        );
    }

    private void renderTokenThumbnail(
            VRenderContext context,
            TokenDefinition definition,
            int x,
            int y,
            int width,
            int height
    ) {
        CanvasVisual visual = getDefaultVisual(definition);

        if (visual != null) {
            visualRenderer.render(
                    context,
                    visual,
                    x,
                    y,
                    x + width,
                    y + height
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

        context.graphics().hLine(x, x + width, y, THUMBNAIL_BORDER);
        context.graphics().hLine(x, x + width, y + height, THUMBNAIL_BORDER);
        context.graphics().vLine(x, y, y + height, THUMBNAIL_BORDER);
        context.graphics().vLine(x + width, y, y + height, THUMBNAIL_BORDER);
    }

    private CanvasVisual getDefaultVisual(TokenDefinition definition) {
        CanvasObjectState state = definition.states().get(definition.defaultStateId());

        if (state == null) {
            return null;
        }

        return state.visual();
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

    public boolean isCreateTokenButtonAt(
            TokenDefinitionRegistry tokenDefinitionRegistry,
            int screenHeight,
            double mouseX,
            double mouseY
    ) {
        int tokenCount = tokenDefinitionRegistry == null
                ? 0
                : tokenDefinitionRegistry.size();

        int panelHeight = calculatePanelHeight(tokenCount);

        int panelX = PANEL_X;
        int panelY = getPanelY(screenHeight, panelHeight);

        int buttonSize = 18;
        int buttonX = panelX + PANEL_WIDTH - PADDING - buttonSize;
        int buttonY = panelY + PADDING;

        return mouseX >= buttonX
                && mouseX <= buttonX + buttonSize
                && mouseY >= buttonY
                && mouseY <= buttonY + buttonSize;
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

        int panelHeight = calculatePanelHeight(definitions.size());

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
            int rowTop = firstTokenY + i * TOKEN_ROW_HEIGHT;
            int rowBottom = rowTop + TOKEN_ROW_HEIGHT;

            if (mouseY >= rowTop && mouseY <= rowBottom) {
                return Optional.of(definitions.get(i));
            }
        }

        return Optional.empty();
    }

    private void renderTokenDetailsPopup(
            VRenderContext context,
            Font font,
            TokenDefinition definition,
            int x,
            int y
    ) {
        int popupHeight = calculateDetailsPopupHeight(definition);

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

        drawLine(context, font, "Details:", textX, textY, TITLE_COLOR);
        textY += LINE_HEIGHT;

        drawLine(context, font, "ID: " + definition.id(), textX, textY, MUTED_TEXT_COLOR);
        textY += LINE_HEIGHT;

        drawLine(
                context,
                font,
                "Size: "
                        + formatNumber(definition.defaultSize().x())
                        + "x"
                        + formatNumber(definition.defaultSize().y()),
                textX,
                textY,
                TEXT_COLOR
        );
        textY += LINE_HEIGHT;

        drawLine(
                context,
                font,
                "Default State: " + definition.defaultStateId(),
                textX,
                textY,
                TEXT_COLOR
        );
        textY += LINE_HEIGHT;

        drawLine(context, font, "States:", textX, textY, MUTED_TEXT_COLOR);
        textY += LINE_HEIGHT;

        int index = 0;

        for (CanvasObjectState state : definition.states().values()) {
            if (index >= MAX_VISIBLE_STATES) {
                int remaining = definition.states().size() - MAX_VISIBLE_STATES;

                drawLine(
                        context,
                        font,
                        "... +" + remaining + " more",
                        textX,
                        textY,
                        MUTED_TEXT_COLOR
                );

                return;
            }

            boolean defaultState = state.id().equals(definition.defaultStateId());

            String prefix = defaultState ? "> " : "  ";

            drawLine(
                    context,
                    font,
                    prefix + state.id() + " - " + state.displayName(),
                    textX,
                    textY,
                    defaultState ? HOVER_TEXT_COLOR : TEXT_COLOR
            );

            textY += LINE_HEIGHT;
            index++;
        }
    }

    private int resolvePopupYForDefinition(
            VRenderContext context,
            List<TokenDefinition> definitions,
            TokenDefinition detailsDefinition
    ) {
        int panelHeight = calculatePanelHeight(definitions.size());
        int panelY = getPanelY(context.screenHeight(), panelHeight);

        int firstTokenY = panelY + PADDING + LINE_HEIGHT + 4 + LINE_HEIGHT + 4;

        int visibleDefinitions = Math.min(definitions.size(), MAX_VISIBLE_TOKENS);

        for (int i = 0; i < visibleDefinitions; i++) {
            TokenDefinition definition = definitions.get(i);

            if (definition.id().equals(detailsDefinition.id())) {
                return firstTokenY + i * TOKEN_ROW_HEIGHT;
            }
        }

        return panelY;
    }

    private int calculateDetailsPopupHeight(TokenDefinition definition) {
        int visibleStates = Math.min(definition.states().size(), MAX_VISIBLE_STATES);

        int lines = 5 + visibleStates;

        if (definition.states().size() > MAX_VISIBLE_STATES) {
            lines++;
        }

        return PADDING * 2 + lines * LINE_HEIGHT;
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

    private TokenDefinition resolveDetailsDefinition(
            TokenDefinitionRegistry tokenDefinitionRegistry,
            Optional<TokenDefinition> hoveredDefinition,
            TokenCatalogSelection selection
    ) {
        if (hoveredDefinition.isPresent()) {
            return hoveredDefinition.get();
        }

        if (selection == null || !selection.hasSelection()) {
            return null;
        }

        return tokenDefinitionRegistry
                .findById(selection.getSelectedTokenDefinitionId())
                .orElse(null);
    }

    private List<TokenDefinition> getSortedDefinitions(TokenDefinitionRegistry tokenDefinitionRegistry) {
        List<TokenDefinition> definitions = new ArrayList<>(tokenDefinitionRegistry.getAll());

        definitions.sort(Comparator.comparing(TokenDefinition::displayName));

        return definitions;
    }

    private int calculatePanelHeight(int tokenCount) {
        int visibleDefinitions = Math.min(tokenCount, MAX_VISIBLE_TOKENS);

        int headerHeight = PADDING + LINE_HEIGHT + 4 + LINE_HEIGHT + 4;

        int tokenListHeight = visibleDefinitions * TOKEN_ROW_HEIGHT;

        int extraHeight = 0;

        if (tokenCount > MAX_VISIBLE_TOKENS) {
            extraHeight += LINE_HEIGHT;
        }

        return headerHeight + tokenListHeight + extraHeight + PADDING + 8;
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