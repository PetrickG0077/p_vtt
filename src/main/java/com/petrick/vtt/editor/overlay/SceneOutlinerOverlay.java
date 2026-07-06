package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.feature.selection.SelectionManager;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;

import java.util.List;
import java.util.Optional;

/**
 * Painel debug que lista os objetos existentes na cena.
 *
 * Ele ajuda principalmente a recuperar objetos ocultos,
 * já que objetos invisíveis não podem ser clicados diretamente no canvas.
 */
public final class SceneOutlinerOverlay {

    private static final int PANEL_WIDTH = 220;

    private static final int PADDING = 8;

    private static final int LINE_HEIGHT = 10;

    private static final int PANEL_X = 10;

    private static final int PANEL_Y = 105;

    private static final int MAX_VISIBLE_OBJECTS = 10;

    private static final int PANEL_BACKGROUND = 0xAA000000;

    private static final int PANEL_BORDER = 0xFF66CC66;

    private static final int TITLE_COLOR = 0xFFFFFFFF;

    private static final int TEXT_COLOR = 0xFFDDDDDD;

    private static final int MUTED_TEXT_COLOR = 0xFF888888;

    private static final int SELECTED_TEXT_COLOR = 0xFF66FF66;

    public void render(
            VRenderContext context,
            Font font,
            CanvasScene scene,
            SelectionManager selectionManager
    ) {
        List<CanvasObject> objects = getSortedObjects(scene);

        int visibleObjects = Math.min(objects.size(), MAX_VISIBLE_OBJECTS);

        int lines = 3 + visibleObjects;

        if (objects.size() > MAX_VISIBLE_OBJECTS) {
            lines++;
        }

        int panelHeight = PADDING * 2 + lines * LINE_HEIGHT + 8;

        renderPanelBackground(context, PANEL_X, PANEL_Y, PANEL_WIDTH, panelHeight);

        int textX = PANEL_X + PADDING;
        int textY = PANEL_Y + PADDING;

        drawLine(context, font, "Scene Objects", textX, textY, TITLE_COLOR);
        textY += LINE_HEIGHT + 4;

        drawLine(context, font, "Objects: " + objects.size(), textX, textY, TEXT_COLOR);
        textY += LINE_HEIGHT + 4;

        if (objects.isEmpty()) {
            drawLine(context, font, "No objects", textX, textY, MUTED_TEXT_COLOR);
            return;
        }

        for (int i = 0; i < visibleObjects; i++) {
            CanvasObject object = objects.get(i);

            boolean selected = selectionManager.isSelected(object.id());

            int layerIndex = scene.getObjectLayerIndex(object.id());

            String selectedPrefix = selected ? "> " : "  ";
            String visibilityPrefix = object.visible() ? "[V] " : "[H] ";
            String text = selectedPrefix + visibilityPrefix + "[" + layerIndex + "] " + object.id();
            
            int color;

            if (selected) {
                color = SELECTED_TEXT_COLOR;
            } else if (!object.visible()) {
                color = MUTED_TEXT_COLOR;
            } else {
                color = TEXT_COLOR;
            }

            drawLine(context, font, text, textX, textY, color);
            textY += LINE_HEIGHT;
        }

        if (objects.size() > MAX_VISIBLE_OBJECTS) {
            int remaining = objects.size() - MAX_VISIBLE_OBJECTS;
            drawLine(context, font, "... +" + remaining + " more", textX, textY, MUTED_TEXT_COLOR);
        }
    }

    public Optional<String> findObjectIdAt(
            CanvasScene scene,
            double mouseX,
            double mouseY
    ) {
        List<CanvasObject> objects = getSortedObjects(scene);

        if (objects.isEmpty()) {
            return Optional.empty();
        }

        int visibleObjects = Math.min(objects.size(), MAX_VISIBLE_OBJECTS);

        int lines = 3 + visibleObjects;

        if (objects.size() > MAX_VISIBLE_OBJECTS) {
            lines++;
        }

        int panelHeight = PADDING * 2 + lines * LINE_HEIGHT + 8;

        if (mouseX < PANEL_X || mouseX > PANEL_X + PANEL_WIDTH) {
            return Optional.empty();
        }

        if (mouseY < PANEL_Y || mouseY > PANEL_Y + panelHeight) {
            return Optional.empty();
        }

        int firstObjectY = PANEL_Y + PADDING + LINE_HEIGHT + 4 + LINE_HEIGHT + 4;

        for (int i = 0; i < visibleObjects; i++) {
            int rowTop = firstObjectY + i * LINE_HEIGHT;
            int rowBottom = rowTop + LINE_HEIGHT;

            if (mouseY >= rowTop && mouseY <= rowBottom) {
                return Optional.of(objects.get(i).id());
            }
        }

        return Optional.empty();
    }

    private List<CanvasObject> getSortedObjects(CanvasScene scene) {
        return scene.getObjects();
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