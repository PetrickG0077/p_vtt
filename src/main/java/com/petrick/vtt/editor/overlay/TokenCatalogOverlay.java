package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.VTT;
import com.petrick.vtt.editor.catalog.CatalogFolderBrowser;
import com.petrick.vtt.editor.catalog.TokenCatalogSelection;
import com.petrick.vtt.feature.asset.animation.AnimatedTextureService;
import com.petrick.vtt.feature.canvas.CanvasObjectState;
import com.petrick.vtt.feature.canvas.visual.CanvasVisual;
import com.petrick.vtt.feature.canvas.visual.CanvasVisualRenderer;
import com.petrick.vtt.feature.token.TokenDefinition;
import com.petrick.vtt.feature.token.TokenDefinitionRegistry;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;

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
 * - os detalhes aparecem somente com hover;
 * - o menu de contexto pode suprimir o popup de detalhes.
 */
public final class TokenCatalogOverlay {

    private static final int PANEL_WIDTH = 210;

    private static final int PADDING = 8;

    private static final int LINE_HEIGHT = 10;

    private static final int TOKEN_ROW_HEIGHT = 30;

    private static final int THUMBNAIL_SIZE = 24;

    /** Leaves the catalog immediately above the centered bottom HUD bar. */
    private static final int PANEL_Y_OFFSET_FROM_BOTTOM = 40;

    private static final int MAX_VISIBLE_TOKENS = 2;

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
    private static final int FOLDER_ROW_BACKGROUND = 0xAA4B236E;
    private static final ResourceLocation FOLDER_ICON =
            ResourceLocation.fromNamespaceAndPath(
                    VTT.MOD_ID, "textures/gui/editor_hud/folder.png");

    private final CanvasVisualRenderer visualRenderer;
    private final CatalogFolderBrowser<TokenDefinition> folderBrowser =
            new CatalogFolderBrowser<>();

    private boolean detailsPopupSuppressed;

    public TokenCatalogOverlay() {
        this.visualRenderer = new CanvasVisualRenderer(new AnimatedTextureService());
    }

    public void render(
            VRenderContext context,
            Font font,
            TokenDefinitionRegistry tokenDefinitionRegistry,
            TokenCatalogSelection selection,
            int scrollOffset
    ) {
        List<CatalogFolderBrowser.Row<TokenDefinition>> rows =
                getVisibleRows(tokenDefinitionRegistry);

        Optional<TokenDefinition> hoveredDefinition = findTokenDefinitionAt(
                tokenDefinitionRegistry,
                context.screenWidth(),
                context.screenHeight(),
                context.mouseX(),
                context.mouseY(),
                scrollOffset
        );

        TokenDefinition detailsDefinition = resolveDetailsDefinition(hoveredDefinition);

        int panelHeight = calculatePanelHeight(rows.size());

        int x = getPanelX(context.screenWidth());
        int y = getPanelY(context.screenHeight(), panelHeight);

        renderPanelBackground(context, x, y, PANEL_WIDTH, panelHeight);

        int textX = x + PADDING;
        int textY = y + PADDING;

        drawLine(context, font, folderBrowser.breadcrumb("Token Catalog"),
                textX, textY, TITLE_COLOR);
        textY += LINE_HEIGHT + 4;

        drawLine(
                context,
                font,
                "Tokens: " + (tokenDefinitionRegistry == null
                        ? 0 : tokenDefinitionRegistry.size()),
                textX,
                textY,
                TEXT_COLOR
        );
        textY += LINE_HEIGHT + 4;

        if (rows.isEmpty()) {
            drawLine(context, font, "No token definitions", textX, textY, MUTED_TEXT_COLOR);
            return;
        }

        int firstDefinition = clampScrollOffset(rows.size(), scrollOffset);
        int visibleDefinitions = Math.min(MAX_VISIBLE_TOKENS, rows.size() - firstDefinition);

        for (int i = 0; i < visibleDefinitions; i++) {
            CatalogFolderBrowser.Row<TokenDefinition> row =
                    rows.get(firstDefinition + i);
            if (row.folder()) {
                renderFolderRow(context, font, row, textX, textY);
                textY += TOKEN_ROW_HEIGHT;
                continue;
            }
            TokenDefinition definition = row.item();

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

        if (rows.size() > MAX_VISIBLE_TOKENS) {
            drawLine(
                    context,
                    font,
                    (firstDefinition + 1) + "-"
                            + (firstDefinition + visibleDefinitions) + " / " + rows.size(),
                    textX,
                    textY,
                    MUTED_TEXT_COLOR
            );

            EditorScrollbar.render(
                    context,
                    x + PANEL_WIDTH - PADDING - 4,
                    getFirstTokenY(y),
                    4,
                    MAX_VISIBLE_TOKENS * TOKEN_ROW_HEIGHT,
                    rows.size(),
                    MAX_VISIBLE_TOKENS,
                    firstDefinition,
                    PANEL_BORDER
            );
        }

        if (!detailsPopupSuppressed && detailsDefinition != null) {
            int popupX = (int) Math.round(context.mouseX()) + 14;
            int popupY = (int) Math.round(context.mouseY())
                    - calculateDetailsPopupHeight(detailsDefinition)
                    - 14;

            renderTokenDetailsPopup(
                    context,
                    font,
                    detailsDefinition,
                    popupX,
                    popupY
            );
        }
    }

    public void suppressDetailsPopup() {
        this.detailsPopupSuppressed = true;
    }

    public void allowDetailsPopup() {
        this.detailsPopupSuppressed = false;
    }

    public boolean isDetailsPopupSuppressed() {
        return detailsPopupSuppressed;
    }

    public boolean containsPoint(
            TokenDefinitionRegistry tokenDefinitionRegistry,
            int screenWidth,
            int screenHeight,
            double mouseX,
            double mouseY
    ) {
        int tokenCount = getVisibleRows(tokenDefinitionRegistry).size();

        int panelHeight = calculatePanelHeight(tokenCount);

        int panelX = getPanelX(screenWidth);
        int panelY = getPanelY(screenHeight, panelHeight);

        return mouseX >= panelX
                && mouseX <= panelX + PANEL_WIDTH
                && mouseY >= panelY
                && mouseY <= panelY + panelHeight;
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

    private void renderFolderRow(
            VRenderContext context,
            Font font,
            CatalogFolderBrowser.Row<TokenDefinition> row,
            int x,
            int y
    ) {
        context.graphics().fill(
                x - 3, y, x + PANEL_WIDTH - PADDING * 2,
                y + TOKEN_ROW_HEIGHT, FOLDER_ROW_BACKGROUND);
        context.graphics().blit(
                FOLDER_ICON, x + 3, y + 7,
                16, 16, 0.0F, 0.0F, 32, 32, 32, 32);
        drawLine(context, font, row.displayName(),
                x + 24, y + 10, 0xFFFFFFFF);
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

    public TokenDefinition findTokenAt(
            TokenDefinitionRegistry tokenDefinitionRegistry,
            int screenWidth,
            int screenHeight,
            double mouseX,
            double mouseY,
            int scrollOffset
    ) {
        return findTokenDefinitionAt(
                tokenDefinitionRegistry,
                screenWidth,
                screenHeight,
                mouseX,
                mouseY,
                scrollOffset
        ).orElse(null);
    }

    public Optional<TokenDefinition> findTokenDefinitionAt(
            TokenDefinitionRegistry tokenDefinitionRegistry,
            int screenWidth,
            int screenHeight,
            double mouseX,
            double mouseY,
            int scrollOffset
    ) {
        List<CatalogFolderBrowser.Row<TokenDefinition>> rows =
                getVisibleRows(tokenDefinitionRegistry);

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        int panelHeight = calculatePanelHeight(rows.size());

        int panelX = getPanelX(screenWidth);
        int panelY = getPanelY(screenHeight, panelHeight);

        if (mouseX < panelX || mouseX > panelX + PANEL_WIDTH) {
            return Optional.empty();
        }

        if (mouseY < panelY || mouseY > panelY + panelHeight) {
            return Optional.empty();
        }

        int firstTokenY = getFirstTokenY(panelY);

        int firstDefinition = clampScrollOffset(rows.size(), scrollOffset);
        int visibleDefinitions = Math.min(MAX_VISIBLE_TOKENS, rows.size() - firstDefinition);

        for (int i = 0; i < visibleDefinitions; i++) {
            int rowTop = firstTokenY + i * TOKEN_ROW_HEIGHT;
            int rowBottom = rowTop + TOKEN_ROW_HEIGHT;

            if (mouseY >= rowTop && mouseY <= rowBottom) {
                return Optional.ofNullable(rows.get(firstDefinition + i).item());
            }
        }

        return Optional.empty();
    }

    public boolean openFolderAt(
            TokenDefinitionRegistry registry,
            int screenWidth,
            int screenHeight,
            double mouseX,
            double mouseY,
            int scrollOffset
    ) {
        List<CatalogFolderBrowser.Row<TokenDefinition>> rows = getVisibleRows(registry);
        int panelHeight = calculatePanelHeight(rows.size());
        int panelX = getPanelX(screenWidth);
        int panelY = getPanelY(screenHeight, panelHeight);
        if (mouseX < panelX || mouseX > panelX + PANEL_WIDTH) return false;
        int first = clampScrollOffset(rows.size(), scrollOffset);
        int visible = Math.min(MAX_VISIBLE_TOKENS, rows.size() - first);
        int firstY = getFirstTokenY(panelY);
        for (int index = 0; index < visible; index++) {
            int top = firstY + index * TOKEN_ROW_HEIGHT;
            if (mouseY >= top && mouseY <= top + TOKEN_ROW_HEIGHT) {
                return folderBrowser.open(rows.get(first + index));
            }
        }
        return false;
    }

    public boolean isScrollbarAt(TokenDefinitionRegistry registry, int screenWidth, int screenHeight,
                                 double mouseX, double mouseY) {
        int tokenCount = getVisibleRows(registry).size();
        if (tokenCount <= MAX_VISIBLE_TOKENS) return false;
        int panelY = getPanelY(screenHeight, calculatePanelHeight(tokenCount));
        return EditorScrollbar.contains(mouseX, mouseY,
                getPanelX(screenWidth) + PANEL_WIDTH - PADDING - 7, getFirstTokenY(panelY),
                10, MAX_VISIBLE_TOKENS * TOKEN_ROW_HEIGHT);
    }

    public int scrollOffsetFromMouse(TokenDefinitionRegistry registry, int screenHeight,
                                     double mouseY) {
        int tokenCount = getVisibleRows(registry).size();
        int panelY = getPanelY(screenHeight, calculatePanelHeight(tokenCount));
        return EditorScrollbar.offsetForMouse(mouseY, getFirstTokenY(panelY),
                MAX_VISIBLE_TOKENS * TOKEN_ROW_HEIGHT, tokenCount, MAX_VISIBLE_TOKENS);
    }

    public int clampScrollOffset(TokenDefinitionRegistry registry, int offset) {
        return clampScrollOffset(getVisibleRows(registry).size(), offset);
    }

    private int clampScrollOffset(int tokenCount, int offset) {
        return EditorScrollbar.clampOffset(offset, tokenCount, MAX_VISIBLE_TOKENS);
    }

    private int getFirstTokenY(int panelY) {
        return panelY + PADDING + LINE_HEIGHT + 4 + LINE_HEIGHT + 4;
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

        drawLine(
                context,
                font,
                definition.displayName(),
                textX,
                textY,
                TITLE_COLOR
        );
        textY += LINE_HEIGHT;

        drawLine(
                context,
                font,
                "ID: " + definition.id(),
                textX,
                textY,
                MUTED_TEXT_COLOR
        );
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
                "Default State: " + formatDefaultState(definition),
                textX,
                textY,
                TEXT_COLOR
        );
        textY += LINE_HEIGHT;

        drawLine(
                context,
                font,
                "States: " + definition.states().size(),
                textX,
                textY,
                MUTED_TEXT_COLOR
        );
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

    private String formatDefaultState(TokenDefinition definition) {
        CanvasObjectState defaultState = definition.states().get(definition.defaultStateId());

        if (defaultState == null) {
            return definition.defaultStateId();
        }

        return definition.defaultStateId() + " - " + defaultState.displayName();
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
            Optional<TokenDefinition> hoveredDefinition
    ) {
        return hoveredDefinition.orElse(null);
    }

    private List<CatalogFolderBrowser.Row<TokenDefinition>> getVisibleRows(
            TokenDefinitionRegistry registry
    ) {
        if (registry == null) return List.of();
        return folderBrowser.rows(
                registry.getAll(),
                definition -> registry.folderOf(definition.id()),
                TokenDefinition::displayName);
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

    private int getPanelX(int screenWidth) {
        return (screenWidth - PANEL_WIDTH) / 2;
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
