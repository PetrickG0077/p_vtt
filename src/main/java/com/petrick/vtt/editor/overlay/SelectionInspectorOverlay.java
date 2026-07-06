package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.core.math.Rectd;
import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.feature.selection.SelectionManager;
import com.petrick.vtt.platform.render.VRenderContext;
import com.petrick.vtt.feature.asset.BuiltInTextureAssetRef;
import com.petrick.vtt.feature.canvas.visual.ColorVisual;
import com.petrick.vtt.feature.canvas.visual.TextureVisual;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Painel debug para inspecionar objetos selecionados no canvas.
 *
 * Isso é temporário para o Sprint 1.
 * Futuramente pode virar um inspector real do editor.
 */
public final class SelectionInspectorOverlay {

    private static final int PANEL_WIDTH = 220;

    private static final int PADDING = 8;

    private static final int LINE_HEIGHT = 10;

    private static final int PANEL_BACKGROUND = 0xAA000000;

    private static final int PANEL_BORDER = 0xFF3399FF;

    private static final int TITLE_COLOR = 0xFFFFFFFF;

    private static final int TEXT_COLOR = 0xFFDDDDDD;

    private static final int MUTED_TEXT_COLOR = 0xFFAAAAAA;

    public void render(
            VRenderContext context,
            Font font,
            CanvasScene scene,
            SelectionManager selectionManager
    ) {
        Set<String> selectedIds = selectionManager.getSelectedObjectIds();

        if (selectedIds.isEmpty()) {
            renderNoSelection(context, font);
            return;
        }

        List<CanvasObject> selectedObjects = getSelectedObjects(scene, selectedIds);

        int lines = calculateLineCount(selectedObjects);
        int panelHeight = PADDING * 2 + lines * LINE_HEIGHT;

        int x = context.screenWidth() - PANEL_WIDTH - 10;
        int y = 10;

        renderPanelBackground(context, x, y, PANEL_WIDTH, panelHeight);

        int textX = x + PADDING;
        int textY = y + PADDING;

        drawLine(context, font, "Selection Inspector", textX, textY, TITLE_COLOR);
        textY += LINE_HEIGHT + 4;

        drawLine(
                context,
                font,
                "Selected Objects: " + selectedObjects.size(),
                textX,
                textY,
                TEXT_COLOR
        );
        textY += LINE_HEIGHT + 4;

        if (selectedObjects.size() == 1) {
            renderSingleObjectInfo(context, font, selectedObjects.getFirst(), textX, textY);
        } else {
            renderMultipleObjectsInfo(context, font, selectedObjects, textX, textY);
        }
    }

    private void renderNoSelection(VRenderContext context, Font font) {
        int panelHeight = PADDING * 2 + 4 * LINE_HEIGHT;

        int x = context.screenWidth() - PANEL_WIDTH - 10;
        int y = 10;

        renderPanelBackground(context, x, y, PANEL_WIDTH, panelHeight);

        int textX = x + PADDING;
        int textY = y + PADDING;

        drawLine(context, font, "Selection Inspector", textX, textY, TITLE_COLOR);
        textY += LINE_HEIGHT + 4;

        drawLine(context, font, "No object selected", textX, textY, MUTED_TEXT_COLOR);
    }

    private void renderSingleObjectInfo(
            VRenderContext context,
            Font font,
            CanvasObject object,
            int x,
            int y
    ) {
        Rectd bounds = object.bounds();

        drawLine(context, font, "ID: " + object.id(), x, y, TEXT_COLOR);
        y += LINE_HEIGHT;

        drawLine(
                context,
                font,
                "Position: " + formatVec(object.transform().position()),
                x,
                y,
                TEXT_COLOR
        );
        y += LINE_HEIGHT;

        drawLine(
                context,
                font,
                "Base Size: " + formatVec(object.size()),
                x,
                y,
                TEXT_COLOR
        );
        y += LINE_HEIGHT;

        drawLine(
                context,
                font,
                "Scale: " + formatVec(object.transform().scale()),
                x,
                y,
                TEXT_COLOR
        );
        y += LINE_HEIGHT;

        drawLine(
                context,
                font,
                "Scaled Size: " + formatVec(object.scaledSize()),
                x,
                y,
                TEXT_COLOR
        );
        y += LINE_HEIGHT;

        drawLine(
                context,
                font,
                "Rotation: " + formatDouble(object.transform().rotationDegrees()) + "°",
                x,
                y,
                TEXT_COLOR
        );
        y += LINE_HEIGHT;

        drawLine(
                context,
                font,
                "Visible: " + object.visible(),
                x,
                y,
                TEXT_COLOR
        );
        y += LINE_HEIGHT;

        y = renderVisualInfo(context, font, object, x, y);

        drawLine(
                context,
                font,
                "Bounds:",
                x,
                y,
                MUTED_TEXT_COLOR
        );
        y += LINE_HEIGHT;

        drawLine(
                context,
                font,
                "x " + formatDouble(bounds.x()) + "  y " + formatDouble(bounds.y()),
                x,
                y,
                TEXT_COLOR
        );
        y += LINE_HEIGHT;

        drawLine(
                context,
                font,
                "w " + formatDouble(bounds.width()) + "  h " + formatDouble(bounds.height()),
                x,
                y,
                TEXT_COLOR
        );
    }

    private int renderVisualInfo(
            VRenderContext context,
            Font font,
            CanvasObject object,
            int x,
            int y
    ) {
        if (object.visual() instanceof ColorVisual colorVisual) {
            drawLine(
                    context,
                    font,
                    "Visual: Color",
                    x,
                    y,
                    TEXT_COLOR
            );
            y += LINE_HEIGHT;

            drawLine(
                    context,
                    font,
                    "Color: #" + String.format(Locale.ROOT, "%08X", colorVisual.color()),
                    x,
                    y,
                    TEXT_COLOR
            );
            y += LINE_HEIGHT;

            return y;
        }

        if (object.visual() instanceof TextureVisual textureVisual) {
            drawLine(
                    context,
                    font,
                    "Visual: Texture",
                    x,
                    y,
                    TEXT_COLOR
            );
            y += LINE_HEIGHT;

            drawLine(
                    context,
                    font,
                    "Asset: " + textureVisual.assetRef().id(),
                    x,
                    y,
                    TEXT_COLOR
            );
            y += LINE_HEIGHT;

            if (textureVisual.assetRef() instanceof BuiltInTextureAssetRef builtInTexture) {
                drawLine(
                        context,
                        font,
                        "Asset Type: Built-in",
                        x,
                        y,
                        TEXT_COLOR
                );
                y += LINE_HEIGHT;

                drawLine(
                        context,
                        font,
                        "Texture Size: "
                                + builtInTexture.textureWidth()
                                + "x"
                                + builtInTexture.textureHeight(),
                        x,
                        y,
                        TEXT_COLOR
                );
                y += LINE_HEIGHT;
            }

            return y;
        }

        drawLine(
                context,
                font,
                "Visual: Unknown",
                x,
                y,
                MUTED_TEXT_COLOR
        );

        return y + LINE_HEIGHT;
    }

    private void renderMultipleObjectsInfo(
            VRenderContext context,
            Font font,
            List<CanvasObject> selectedObjects,
            int x,
            int y
    ) {
        drawLine(context, font, "IDs:", x, y, MUTED_TEXT_COLOR);
        y += LINE_HEIGHT;

        int maxVisibleObjects = 8;

        for (int i = 0; i < selectedObjects.size(); i++) {
            if (i >= maxVisibleObjects) {
                int remaining = selectedObjects.size() - maxVisibleObjects;

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

            CanvasObject object = selectedObjects.get(i);

            drawLine(
                    context,
                    font,
                    "- " + object.id(),
                    x,
                    y,
                    TEXT_COLOR
            );
            y += LINE_HEIGHT;
        }
    }

    private List<CanvasObject> getSelectedObjects(CanvasScene scene, Set<String> selectedIds) {
        List<CanvasObject> selectedObjects = new ArrayList<>();

        for (String selectedId : selectedIds) {
            CanvasObject object = scene.findObjectById(selectedId);

            if (object != null) {
                selectedObjects.add(object);
            }
        }

        selectedObjects.sort((a, b) -> a.id().compareToIgnoreCase(b.id()));

        return selectedObjects;
    }

    private int calculateLineCount(List<CanvasObject> selectedObjects) {
        if (selectedObjects.isEmpty()) {
            return 4;
        }

        if (selectedObjects.size() == 1) {
            return 18;
        }

        return Math.min(selectedObjects.size(), 8) + 5;
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

    private String formatVec(com.petrick.vtt.core.math.Vec2d value) {
        return formatDouble(value.x()) + ", " + formatDouble(value.y());
    }

    private String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}