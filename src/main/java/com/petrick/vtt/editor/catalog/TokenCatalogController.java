package com.petrick.vtt.editor.catalog;

import com.petrick.vtt.editor.overlay.TokenCatalogOverlay;
import com.petrick.vtt.feature.token.TokenDefinition;
import com.petrick.vtt.feature.token.TokenDefinitionRegistry;

import java.util.Optional;

/**
 * Controla as interações do Token Catalog.
 *
 * Responsabilidades:
 * - selecionar TokenDefinition
 * - detectar drag com delay/distância mínima
 * - detectar duplo clique
 * - limpar seleção ao clicar fora
 *
 * Ele não cria CanvasObject diretamente.
 * A criação continua sendo responsabilidade da VTTScreen por enquanto.
 */
public final class TokenCatalogController {

    private static final long TOKEN_DRAG_HOLD_DELAY_MS = 25L;

    private static final double TOKEN_DRAG_MIN_DISTANCE = 6.0;

    private static final long DOUBLE_CLICK_MS = 300L;

    private final TokenCatalogSelection selection;

    private TokenDefinition draggingTokenDefinition;

    private long dragStartTimeMs;

    private double dragStartMouseX;

    private double dragStartMouseY;

    private long lastClickTimeMs;

    private String lastClickDefinitionId;

    private int scrollOffset;

    private boolean draggingScrollbar;

    public TokenCatalogController(TokenCatalogSelection selection) {
        if (selection == null) {
            throw new IllegalArgumentException("TokenCatalogSelection cannot be null");
        }

        this.selection = selection;
    }

    public TokenCatalogClickResult mouseClicked(
            TokenCatalogOverlay overlay,
            TokenDefinitionRegistry registry,
            boolean catalogVisible,
            int screenHeight,
            double mouseX,
            double mouseY
    ) {
        if (!catalogVisible) {
            selection.clear();
            clearDrag();
            draggingScrollbar = false;
            return TokenCatalogClickResult.none();
        }

        scrollOffset = overlay.clampScrollOffset(registry, scrollOffset);
        if (overlay.isScrollbarAt(registry, screenHeight, mouseX, mouseY)) {
            draggingScrollbar = true;
            scrollOffset = overlay.scrollOffsetFromMouse(registry, screenHeight, mouseY);
            clearDrag();
            return TokenCatalogClickResult.consumeClick();
        }

        Optional<TokenDefinition> clickedDefinition = overlay.findTokenDefinitionAt(
                registry,
                screenHeight,
                mouseX,
                mouseY,
                scrollOffset
        );

        if (clickedDefinition.isEmpty()) {
            if (!overlay.containsPoint(registry, screenHeight, mouseX, mouseY)) {
                selection.clear();
            }

            clearDrag();
            return TokenCatalogClickResult.none();
        }

        TokenDefinition definition = clickedDefinition.get();

        selection.select(definition.id());

        if (isDoubleClick(definition)) {
            clearDrag();
            return TokenCatalogClickResult.createAtCameraCenter(definition);
        }

        draggingTokenDefinition = definition;
        dragStartTimeMs = System.currentTimeMillis();
        dragStartMouseX = mouseX;
        dragStartMouseY = mouseY;

        return TokenCatalogClickResult.consumeClick();
    }

    public TokenDefinition mouseReleased(double mouseX, double mouseY) {
        if (draggingTokenDefinition == null) {
            return null;
        }

        TokenDefinition definition = draggingTokenDefinition;

        boolean shouldCreate = isDragReady(mouseX, mouseY);

        clearDrag();

        if (!shouldCreate) {
            return null;
        }

        return definition;
    }

    public boolean mouseScrolled(TokenCatalogOverlay overlay, TokenDefinitionRegistry registry,
                                 boolean catalogVisible, int screenHeight,
                                 double mouseX, double mouseY, double scrollY) {
        if (!catalogVisible || !overlay.containsPoint(registry, screenHeight, mouseX, mouseY)) {
            return false;
        }
        scrollOffset = overlay.clampScrollOffset(registry,
                scrollOffset + (scrollY < 0 ? 1 : scrollY > 0 ? -1 : 0));
        return true;
    }

    public boolean mouseDragged(TokenCatalogOverlay overlay, TokenDefinitionRegistry registry,
                                int screenHeight, double mouseY) {
        if (!draggingScrollbar) return false;
        scrollOffset = overlay.scrollOffsetFromMouse(registry, screenHeight, mouseY);
        return true;
    }

    public boolean releaseScrollbar() {
        boolean wasDragging = draggingScrollbar;
        draggingScrollbar = false;
        return wasDragging;
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    public boolean shouldShowDragPreview(double mouseX, double mouseY) {
        if (draggingTokenDefinition == null) {
            return false;
        }

        return isDragReady(mouseX, mouseY);
    }

    public TokenDefinition getDraggingTokenDefinition() {
        return draggingTokenDefinition;
    }

    public TokenCatalogSelection getSelection() {
        return selection;
    }

    public void clearDrag() {
        draggingTokenDefinition = null;
        dragStartTimeMs = 0L;
        dragStartMouseX = 0.0;
        dragStartMouseY = 0.0;
    }

    private boolean isDragReady(double mouseX, double mouseY) {
        long heldTimeMs = System.currentTimeMillis() - dragStartTimeMs;

        if (heldTimeMs < TOKEN_DRAG_HOLD_DELAY_MS) {
            return false;
        }

        double deltaX = mouseX - dragStartMouseX;
        double deltaY = mouseY - dragStartMouseY;

        double distanceSquared = deltaX * deltaX + deltaY * deltaY;

        return distanceSquared >= TOKEN_DRAG_MIN_DISTANCE * TOKEN_DRAG_MIN_DISTANCE;
    }

    private boolean isDoubleClick(TokenDefinition definition) {
        long nowMs = System.currentTimeMillis();

        boolean sameToken = definition.id().equals(lastClickDefinitionId);
        boolean withinTime = nowMs - lastClickTimeMs <= DOUBLE_CLICK_MS;

        lastClickTimeMs = nowMs;
        lastClickDefinitionId = definition.id();

        return sameToken && withinTime;
    }
}
