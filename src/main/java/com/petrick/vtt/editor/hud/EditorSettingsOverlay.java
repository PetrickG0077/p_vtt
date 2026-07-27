package com.petrick.vtt.editor.hud;

import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttSceneGrid;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;

import java.util.Locale;

/** Extensible settings window for the active scene. */
public final class EditorSettingsOverlay {
    private static final int WIDTH = 340;
    private static final int HEIGHT = 190;
    private static final int CATEGORY_WIDTH = 92;
    private static final int PANEL_BACKGROUND = 0xF0101014;
    private static final int PANEL_BORDER = 0xFFE8E8E8;
    private static final int SELECTED_BACKGROUND = 0xE0245266;
    private static final int CONTROL_BACKGROUND = 0xE018181E;
    private static final int CONTROL_HOVER = 0xE032323C;
    private static final int ACTIVE = 0xFF66DDEE;
    private static final int TEXT = 0xFFF4F4F4;
    private static final int MUTED = 0xFF99999F;
    private static final int[] COLOR_PRESETS = {
            0xFFFFFF, 0xA0A0A0, 0xFF5555, 0x55FF55,
            0x5555FF, 0x55FFFF, 0xFFFF55, 0xFF55FF
    };

    private boolean draggingOpacity;
    private Category selectedCategory = Category.GRID;

    public void render(
            VRenderContext context,
            Font font,
            VttScene scene,
            boolean editable
    ) {
        VttSceneGrid grid = scene.getGrid();
        Bounds panel = bounds(context.screenWidth(), context.screenHeight());
        context.graphics().fill(panel.x(), panel.y(), panel.right(), panel.bottom(),
                PANEL_BACKGROUND);
        border(context, panel, PANEL_BORDER);
        context.graphics().drawCenteredString(
                font, "Settings", panel.x() + panel.width() / 2, panel.y() + 8, TEXT);
        context.graphics().hLine(panel.x() + 8, panel.right() - 8, panel.y() + 21, PANEL_BORDER);

        renderCategory(context, font, gridCategoryBounds(panel), "Grid",
                selectedCategory == Category.GRID);
        renderCategory(context, font, sceneCategoryBounds(panel), "Scene",
                selectedCategory == Category.SCENE);
        context.graphics().vLine(panel.x() + CATEGORY_WIDTH + 8,
                panel.y() + 29, panel.bottom() - 8, 0xFF66666C);

        if (selectedCategory == Category.SCENE) {
            renderScene(context, font, panel, scene, editable);
            return;
        }

        int contentX = panel.x() + CATEGORY_WIDTH + 20;
        context.graphics().drawString(font, "Grid", contentX, panel.y() + 31, TEXT, false);
        if (!editable) {
            context.graphics().drawString(
                    font, "Read only", panel.right() - 58, panel.y() + 31, MUTED, false);
        }

        renderColors(context, font, panel, grid, editable);
        renderOpacity(context, font, panel, grid, editable);
        renderGridSize(context, font, panel, grid, editable);
        renderLineWidth(context, font, panel, grid, editable);
        renderTopLayer(context, font, panel, grid, editable);
    }

    public Interaction mouseClicked(
            double mouseX,
            double mouseY,
            int button,
            int screenWidth,
            int screenHeight,
            VttScene scene,
            boolean editable
    ) {
        Bounds panel = bounds(screenWidth, screenHeight);
        if (!panel.contains(mouseX, mouseY)) return Interaction.NONE;
        if (button != 0) return Interaction.CONSUMED;

        if (gridCategoryBounds(panel).contains(mouseX, mouseY)) {
            selectedCategory = Category.GRID;
            draggingOpacity = false;
            return Interaction.CONSUMED;
        }
        if (sceneCategoryBounds(panel).contains(mouseX, mouseY)) {
            selectedCategory = Category.SCENE;
            draggingOpacity = false;
            return Interaction.CONSUMED;
        }
        if (!editable || scene == null) return Interaction.CONSUMED;

        if (selectedCategory == Category.SCENE) {
            if (chooseBackgroundBounds(panel).contains(mouseX, mouseY)) {
                return Interaction.CHOOSE_BACKGROUND;
            }
            if (scene.getBackgroundAssetId() != null
                    && removeBackgroundBounds(panel).contains(mouseX, mouseY)) {
                return Interaction.REMOVE_BACKGROUND;
            }
            if (scene.getBackgroundAssetId() != null
                    && editSceneBounds(panel).contains(mouseX, mouseY)) {
                return Interaction.EDIT_SCENE;
            }
            return Interaction.CONSUMED;
        }

        VttSceneGrid grid = scene.getGrid();
        if (grid == null) return Interaction.CONSUMED;

        for (int index = 0; index < COLOR_PRESETS.length; index++) {
            if (colorBounds(panel, index).contains(mouseX, mouseY)) {
                grid.setColorRgb(COLOR_PRESETS[index]);
                return Interaction.CHANGED;
            }
        }

        Bounds opacity = opacitySliderBounds(panel);
        if (opacity.contains(mouseX, mouseY)) {
            draggingOpacity = true;
            updateOpacity(grid, opacity, mouseX);
            return Interaction.CHANGED;
        }

        if (gridSizeMinusBounds(panel).contains(mouseX, mouseY)) {
            grid.setGridSize(grid.getGridSize() - 16.0);
            return Interaction.CHANGED;
        }
        if (gridSizePlusBounds(panel).contains(mouseX, mouseY)) {
            grid.setGridSize(grid.getGridSize() + 16.0);
            return Interaction.CHANGED;
        }
        if (lineWidthMinusBounds(panel).contains(mouseX, mouseY)) {
            grid.setLineWidth(grid.getLineWidth() - 1);
            return Interaction.CHANGED;
        }
        if (lineWidthPlusBounds(panel).contains(mouseX, mouseY)) {
            grid.setLineWidth(grid.getLineWidth() + 1);
            return Interaction.CHANGED;
        }
        if (topLayerBounds(panel).contains(mouseX, mouseY)) {
            grid.setTopLayer(!grid.isTopLayer());
            return Interaction.CHANGED;
        }
        return Interaction.CONSUMED;
    }

    public boolean mouseDragged(
            double mouseX,
            int screenWidth,
            int screenHeight,
            VttSceneGrid grid,
            boolean editable
    ) {
        if (selectedCategory != Category.GRID
                || !draggingOpacity || !editable || grid == null) return false;
        updateOpacity(grid, opacitySliderBounds(bounds(screenWidth, screenHeight)), mouseX);
        return true;
    }

    public boolean mouseReleased(
            double mouseX,
            int button,
            int screenWidth,
            int screenHeight,
            VttSceneGrid grid,
            boolean editable
    ) {
        if (button != 0 || !draggingOpacity) return false;
        if (editable && grid != null) {
            updateOpacity(grid, opacitySliderBounds(bounds(screenWidth, screenHeight)), mouseX);
        }
        draggingOpacity = false;
        return true;
    }

    public boolean contains(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        return bounds(screenWidth, screenHeight).contains(mouseX, mouseY);
    }

    public boolean isEditSceneButtonAt(
            double mouseX,
            double mouseY,
            int screenWidth,
            int screenHeight,
            VttScene scene,
            boolean editable
    ) {
        return editable && scene != null && scene.getBackgroundAssetId() != null
                && selectedCategory == Category.SCENE
                && editSceneBounds(bounds(screenWidth, screenHeight)).contains(mouseX, mouseY);
    }

    public void cancelDrag() {
        draggingOpacity = false;
    }

    public boolean isDraggingOpacity() {
        return draggingOpacity;
    }

    private void renderCategory(
            VRenderContext context,
            Font font,
            Bounds bounds,
            String label,
            boolean selected
    ) {
        if (selected) {
            context.graphics().fill(
                    bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                    SELECTED_BACKGROUND);
        }
        context.graphics().drawCenteredString(
                font, label, bounds.x() + bounds.width() / 2, bounds.y() + 6,
                selected ? TEXT : MUTED);
    }

    private void renderScene(
            VRenderContext context,
            Font font,
            Bounds panel,
            VttScene scene,
            boolean editable
    ) {
        int contentX = panel.x() + CATEGORY_WIDTH + 20;
        context.graphics().drawString(font, "Scene", contentX, panel.y() + 31, TEXT, false);
        if (!editable) {
            context.graphics().drawString(
                    font, "Read only", panel.right() - 58, panel.y() + 31, MUTED, false);
        }

        context.graphics().drawString(font, "Name", contentX, panel.y() + 52, MUTED, false);
        context.graphics().drawString(font,
                ellipsize(font, scene.getDisplayName(), 210),
                contentX, panel.y() + 65, TEXT, false);

        context.graphics().drawString(font, "Background", contentX, panel.y() + 87, MUTED, false);
        String background = scene.getBackgroundAssetId() == null
                ? "None"
                : scene.getBackgroundAssetId();
        context.graphics().drawString(font, ellipsize(font, background, 210),
                contentX, panel.y() + 100,
                scene.getBackgroundAssetId() == null ? MUTED : TEXT, false);

        renderSceneButton(context, font, chooseBackgroundBounds(panel),
                scene.getBackgroundAssetId() == null ? "Choose" : "Change",
                editable);
        renderSceneButton(context, font, editSceneBounds(panel),
                "Edit Scene", editable && scene.getBackgroundAssetId() != null);
        renderSceneButton(context, font, removeBackgroundBounds(panel),
                "Remove", editable && scene.getBackgroundAssetId() != null);
    }

    private void renderSceneButton(
            VRenderContext context,
            Font font,
            Bounds bounds,
            String label,
            boolean enabled
    ) {
        renderControl(context, bounds,
                enabled && bounds.contains(context.mouseX(), context.mouseY()), enabled);
        context.graphics().drawCenteredString(font, label,
                bounds.x() + bounds.width() / 2, bounds.y() + 5,
                enabled ? TEXT : MUTED);
    }

    private String ellipsize(Font font, String value, int maxWidth) {
        if (value == null || font.width(value) <= maxWidth) return value == null ? "" : value;
        String suffix = "...";
        int end = value.length();
        while (end > 0 && font.width(value.substring(0, end) + suffix) > maxWidth) end--;
        return value.substring(0, end) + suffix;
    }

    private void renderColors(
            VRenderContext context,
            Font font,
            Bounds panel,
            VttSceneGrid grid,
            boolean editable
    ) {
        int contentX = panel.x() + CATEGORY_WIDTH + 20;
        context.graphics().drawString(font, "Color", contentX, panel.y() + 49, TEXT, false);
        for (int index = 0; index < COLOR_PRESETS.length; index++) {
            Bounds swatch = colorBounds(panel, index);
            int color = 0xFF000000 | COLOR_PRESETS[index];
            context.graphics().fill(swatch.x(), swatch.y(), swatch.right(), swatch.bottom(), color);
            border(context, swatch,
                    grid.getColorRgb() == COLOR_PRESETS[index] ? ACTIVE : PANEL_BORDER);
            if (!editable) {
                context.graphics().fill(
                        swatch.x(), swatch.y(), swatch.right(), swatch.bottom(), 0x55000000);
            }
        }
    }

    private void renderOpacity(
            VRenderContext context,
            Font font,
            Bounds panel,
            VttSceneGrid grid,
            boolean editable
    ) {
        Bounds slider = opacitySliderBounds(panel);
        String value = Math.round(grid.getOpacity() * 100.0) + "%";
        context.graphics().drawString(
                font, "Opacity  " + value, slider.x(), slider.y() - 14, TEXT, false);
        context.graphics().fill(
                slider.x(), slider.y() + 3, slider.right(), slider.y() + 6, 0xFF55555A);
        int knobX = slider.x() + (int) Math.round(grid.getOpacity() * slider.width());
        context.graphics().fill(
                slider.x(), slider.y() + 3, knobX, slider.y() + 6,
                editable ? ACTIVE : MUTED);
        context.graphics().fill(
                knobX - 3, slider.y(), knobX + 4, slider.bottom(),
                editable ? 0xFFFFFFFF : MUTED);
    }

    private void renderGridSize(
            VRenderContext context,
            Font font,
            Bounds panel,
            VttSceneGrid grid,
            boolean editable
    ) {
        int x = panel.x() + CATEGORY_WIDTH + 20;
        int y = panel.y() + 111;
        context.graphics().drawString(font, "Grid Size", x, y, TEXT, false);
        renderStepper(context, font, gridSizeMinusBounds(panel), gridSizePlusBounds(panel),
                String.format(Locale.ROOT, "%.0f px/m", grid.getGridSize()), editable);
    }

    private void renderLineWidth(
            VRenderContext context,
            Font font,
            Bounds panel,
            VttSceneGrid grid,
            boolean editable
    ) {
        int x = panel.x() + CATEGORY_WIDTH + 145;
        int y = panel.y() + 111;
        context.graphics().drawString(font, "Line Width", x, y, TEXT, false);
        renderStepper(context, font, lineWidthMinusBounds(panel), lineWidthPlusBounds(panel),
                grid.getLineWidth() + " px", editable);
    }

    private void renderStepper(
            VRenderContext context,
            Font font,
            Bounds minus,
            Bounds plus,
            String value,
            boolean editable
    ) {
        boolean minusHovered = editable && minus.contains(context.mouseX(), context.mouseY());
        boolean plusHovered = editable && plus.contains(context.mouseX(), context.mouseY());
        renderControl(context, minus, minusHovered, editable);
        renderControl(context, plus, plusHovered, editable);
        context.graphics().drawCenteredString(
                font, "-", minus.x() + minus.width() / 2, minus.y() + 4,
                editable ? TEXT : MUTED);
        context.graphics().drawCenteredString(
                font, "+", plus.x() + plus.width() / 2, plus.y() + 4,
                editable ? TEXT : MUTED);
        int centerX = (minus.right() + plus.x()) / 2;
        context.graphics().drawCenteredString(font, value, centerX, minus.y() + 4,
                editable ? TEXT : MUTED);
    }

    private void renderTopLayer(
            VRenderContext context,
            Font font,
            Bounds panel,
            VttSceneGrid grid,
            boolean editable
    ) {
        Bounds toggle = topLayerBounds(panel);
        context.graphics().drawString(
                font, "Top Layer", toggle.x() - 68, toggle.y() + 2, TEXT, false);
        context.graphics().fill(
                toggle.x(), toggle.y(), toggle.right(), toggle.bottom(),
                grid.isTopLayer() ? 0xFF246677 : 0xFF3A3A40);
        border(context, toggle, editable ? PANEL_BORDER : MUTED);
        int knobLeft = grid.isTopLayer() ? toggle.right() - 11 : toggle.x() + 2;
        context.graphics().fill(
                knobLeft, toggle.y() + 2, knobLeft + 9, toggle.bottom() - 2,
                editable ? 0xFFFFFFFF : MUTED);
    }

    private void renderControl(
            VRenderContext context,
            Bounds bounds,
            boolean hovered,
            boolean editable
    ) {
        context.graphics().fill(
                bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                hovered ? CONTROL_HOVER : CONTROL_BACKGROUND);
        border(context, bounds, editable ? PANEL_BORDER : 0xFF55555A);
    }

    private void updateOpacity(VttSceneGrid grid, Bounds slider, double mouseX) {
        grid.setOpacity((mouseX - slider.x()) / slider.width());
    }

    private Bounds bounds(int screenWidth, int screenHeight) {
        int x = (screenWidth - WIDTH) / 2;
        int y = Math.max(42, (screenHeight - HEIGHT) / 3);
        return new Bounds(x, y, WIDTH, HEIGHT);
    }

    private Bounds gridCategoryBounds(Bounds panel) {
        return new Bounds(panel.x() + 8, panel.y() + 43, CATEGORY_WIDTH - 9, 22);
    }

    private Bounds sceneCategoryBounds(Bounds panel) {
        return new Bounds(panel.x() + 8, panel.y() + 68, CATEGORY_WIDTH - 9, 22);
    }

    private Bounds chooseBackgroundBounds(Bounds panel) {
        return new Bounds(panel.x() + CATEGORY_WIDTH + 20, panel.y() + 125, 72, 22);
    }

    private Bounds removeBackgroundBounds(Bounds panel) {
        return new Bounds(panel.x() + CATEGORY_WIDTH + 184, panel.y() + 125, 48, 22);
    }

    private Bounds editSceneBounds(Bounds panel) {
        return new Bounds(panel.x() + CATEGORY_WIDTH + 96, panel.y() + 125, 84, 22);
    }

    private Bounds colorBounds(Bounds panel, int index) {
        return new Bounds(
                panel.x() + CATEGORY_WIDTH + 20 + index * 17,
                panel.y() + 63, 13, 13);
    }

    private Bounds opacitySliderBounds(Bounds panel) {
        return new Bounds(panel.x() + CATEGORY_WIDTH + 20, panel.y() + 91, 136, 10);
    }

    private Bounds gridSizeMinusBounds(Bounds panel) {
        return new Bounds(panel.x() + CATEGORY_WIDTH + 20, panel.y() + 126, 17, 17);
    }

    private Bounds gridSizePlusBounds(Bounds panel) {
        return new Bounds(panel.x() + CATEGORY_WIDTH + 108, panel.y() + 126, 17, 17);
    }

    private Bounds lineWidthMinusBounds(Bounds panel) {
        return new Bounds(panel.x() + CATEGORY_WIDTH + 145, panel.y() + 126, 17, 17);
    }

    private Bounds lineWidthPlusBounds(Bounds panel) {
        return new Bounds(panel.x() + CATEGORY_WIDTH + 225, panel.y() + 126, 17, 17);
    }

    private Bounds topLayerBounds(Bounds panel) {
        return new Bounds(panel.x() + CATEGORY_WIDTH + 88, panel.y() + 158, 28, 13);
    }

    private void border(VRenderContext context, Bounds bounds, int color) {
        context.graphics().hLine(bounds.x(), bounds.right(), bounds.y(), color);
        context.graphics().hLine(bounds.x(), bounds.right(), bounds.bottom(), color);
        context.graphics().vLine(bounds.x(), bounds.y(), bounds.bottom(), color);
        context.graphics().vLine(bounds.right(), bounds.y(), bounds.bottom(), color);
    }

    public enum Interaction {
        NONE,
        CONSUMED,
        CHANGED,
        CHOOSE_BACKGROUND,
        REMOVE_BACKGROUND,
        EDIT_SCENE
    }

    private enum Category {
        GRID,
        SCENE
    }

    private record Bounds(int x, int y, int width, int height) {
        private int right() {
            return x + width;
        }

        private int bottom() {
            return y + height;
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= right()
                    && mouseY >= y && mouseY <= bottom();
        }
    }
}
