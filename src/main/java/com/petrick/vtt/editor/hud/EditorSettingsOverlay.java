package com.petrick.vtt.editor.hud;

import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttSceneGrid;
import com.petrick.vtt.feature.tabletop.VttSceneLighting;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Extensible settings window for the active scene. */
public final class EditorSettingsOverlay {
    private static final int WIDTH = 340;
    private static final int HEIGHT = 260;
    private static final int CATEGORY_WIDTH = 92;
    private static final int PANEL_BACKGROUND = 0xF0101014;
    private static final int CONTROL_BACKGROUND = 0xE018181E;
    private static final int CONTROL_HOVER = 0xE032323C;
    private static final int TEXT = 0xFFF4F4F4;
    private static final int MUTED = 0xFF99999F;
    private static final int[] COLOR_PRESETS = {
            0xFFFFFF, 0xA0A0A0, 0xFF5555, 0x55FF55,
            0x5555FF, 0x55FFFF, 0xFFFF55, 0xFF55FF
    };
    private static final int[] HUD_COLOR_PRESETS = {
            0xFFFFFF, 0xB8B8B8, 0x68686E, 0x202024,
            0x66DDEE, 0x3399FF, 0x7655FF, 0xAA55FF,
            0xFF5577, 0xFF9F43, 0xFFE066, 0x55D98B
    };
    private static final int[] DARKNESS_COLOR_PRESETS = {
            0x000000, 0x08080C, 0x101827, 0x181020,
            0x201010, 0x0A1820, 0x181818, 0x28282E
    };

    private boolean draggingOpacity;
    private boolean draggingVisionQuality;
    private boolean draggingVisionPixels;
    private Category selectedCategory = Category.GRID;
    private ThemeColor selectedThemeColor = ThemeColor.OUTLINE;
    private ThemeColor focusedHudHexColor;
    private String hudHexBuffer = "";
    private boolean replaceHudHexOnType;
    private boolean focusedLightingHexColor;
    private String lightingHexBuffer = "";
    private boolean replaceLightingHexOnType;

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
        border(context, panel, EditorHudTheme.outline());
        context.graphics().drawCenteredString(
                font, "Settings", panel.x() + panel.width() / 2, panel.y() + 8, TEXT);
        context.graphics().hLine(
                panel.x() + 8, panel.right() - 8, panel.y() + 21,
                EditorHudTheme.outline());

        renderCategory(context, font, gridCategoryBounds(panel), "Grid",
                selectedCategory == Category.GRID);
        renderCategory(context, font, sceneCategoryBounds(panel), "Scene",
                selectedCategory == Category.SCENE);
        renderCategory(context, font, hudCategoryBounds(panel), "HUD",
                selectedCategory == Category.HUD);
        renderCategory(context, font, lightingCategoryBounds(panel), "Lighting",
                selectedCategory == Category.LIGHTING);
        context.graphics().vLine(panel.x() + CATEGORY_WIDTH + 8,
                panel.y() + 29, panel.bottom() - 8, 0xFF66666C);

        if (selectedCategory == Category.HUD) {
            renderHudTheme(context, font, panel);
            return;
        }
        if (selectedCategory == Category.LIGHTING) {
            renderLighting(context, font, panel, scene.getLighting(), editable);
            return;
        }
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
            focusedHudHexColor = null;
            return Interaction.CONSUMED;
        }
        if (sceneCategoryBounds(panel).contains(mouseX, mouseY)) {
            selectedCategory = Category.SCENE;
            draggingOpacity = false;
            focusedHudHexColor = null;
            return Interaction.CONSUMED;
        }
        if (hudCategoryBounds(panel).contains(mouseX, mouseY)) {
            selectedCategory = Category.HUD;
            draggingOpacity = false;
            return Interaction.CONSUMED;
        }
        if (lightingCategoryBounds(panel).contains(mouseX, mouseY)) {
            selectedCategory = Category.LIGHTING;
            draggingOpacity = false;
            focusedHudHexColor = null;
            focusedLightingHexColor = false;
            return Interaction.CONSUMED;
        }
        if (selectedCategory == Category.HUD) {
            for (ThemeColor themeColor : ThemeColor.values()) {
                Bounds row = hudColorRowBounds(panel, themeColor);
                if (hudHexFieldBounds(row).contains(mouseX, mouseY)) {
                    selectedThemeColor = themeColor;
                    focusedHudHexColor = themeColor;
                    hudHexBuffer = colorHex(hudColor(themeColor));
                    replaceHudHexOnType = true;
                    return Interaction.CONSUMED;
                }
                if (row.contains(mouseX, mouseY)) {
                    selectedThemeColor = themeColor;
                    focusedHudHexColor = null;
                    return Interaction.CONSUMED;
                }
            }
            for (int index = 0; index < HUD_COLOR_PRESETS.length; index++) {
                if (hudPaletteBounds(panel, index).contains(mouseX, mouseY)) {
                    focusedHudHexColor = null;
                    applyHudColor(HUD_COLOR_PRESETS[index]);
                    return Interaction.HUD_THEME_CHANGED;
                }
            }
            focusedHudHexColor = null;
            return Interaction.CONSUMED;
        }
        if (selectedCategory == Category.LIGHTING) {
            if (scene == null) return Interaction.CONSUMED;
            VttSceneLighting lighting = scene.getLighting();
            if (darknessHexFieldBounds(panel).contains(mouseX, mouseY)) {
                focusedLightingHexColor = true;
                lightingHexBuffer = colorHex(lighting.getDarknessColorRgb());
                replaceLightingHexOnType = true;
                return Interaction.CONSUMED;
            }
            for (int index = 0; index < DARKNESS_COLOR_PRESETS.length; index++) {
                if (darknessColorBounds(panel, index).contains(mouseX, mouseY)) {
                    focusedLightingHexColor = false;
                    lighting.setDarknessColorRgb(DARKNESS_COLOR_PRESETS[index]);
                    return Interaction.CHANGED;
                }
            }
            Bounds pixels = visionPixelSizeSliderBounds(panel);
            if (pixels.contains(mouseX, mouseY)) {
                draggingVisionPixels = true;
                updateVisionPixelSize(lighting, pixels, mouseX);
                return Interaction.CHANGED;
            }
            Bounds quality = visionQualitySliderBounds(panel);
            if (editable && quality.contains(mouseX, mouseY)) {
                draggingVisionQuality = true;
                updateVisionQuality(lighting, quality, mouseX);
                return Interaction.CHANGED;
            }
            if (editable && applyDarknessColorBounds(panel).contains(mouseX, mouseY)) {
                return Interaction.APPLY_LIGHTING_COLOR;
            }
            return Interaction.CONSUMED;
        }

        if (!editable || scene == null) return Interaction.CONSUMED;

        if (selectedCategory == Category.SCENE) {
            if (chooseBackgroundBounds(panel).contains(mouseX, mouseY)) {
                return Interaction.CHOOSE_BACKGROUND;
            }
            if (hasSceneMaps(scene)
                    && removeBackgroundBounds(panel).contains(mouseX, mouseY)) {
                return Interaction.REMOVE_BACKGROUND;
            }
            if (hasSceneMaps(scene)
                    && editSceneBounds(panel).contains(mouseX, mouseY)) {
                return Interaction.EDIT_SCENE;
            }
            if (setInitialViewBounds(panel).contains(mouseX, mouseY)) {
                return Interaction.SET_INITIAL_VIEW;
            }
            if (scene.getInitialCameraView() != null
                    && resetInitialViewBounds(panel).contains(mouseX, mouseY)) {
                return Interaction.RESET_INITIAL_VIEW;
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
            VttScene scene,
            boolean editable
    ) {
        if (scene == null) return false;
        Bounds panel = bounds(screenWidth, screenHeight);
        if (selectedCategory == Category.GRID && draggingOpacity) {
            updateOpacity(scene.getGrid(), opacitySliderBounds(panel), mouseX);
            return true;
        }
        if (editable && selectedCategory == Category.LIGHTING && draggingVisionQuality) {
            updateVisionQuality(scene.getLighting(), visionQualitySliderBounds(panel), mouseX);
            return true;
        }
        if (selectedCategory == Category.LIGHTING && draggingVisionPixels) {
            updateVisionPixelSize(scene.getLighting(), visionPixelSizeSliderBounds(panel), mouseX);
            return true;
        }
        return false;
    }

    public boolean mouseReleased(
            double mouseX,
            int button,
            int screenWidth,
            int screenHeight,
            VttScene scene,
            boolean editable
    ) {
        if (button != 0 || !draggingOpacity && !draggingVisionQuality
                && !draggingVisionPixels) return false;
        if (scene != null) {
            Bounds panel = bounds(screenWidth, screenHeight);
            if (draggingOpacity) {
                updateOpacity(scene.getGrid(), opacitySliderBounds(panel), mouseX);
            }
            if (editable && draggingVisionQuality) {
                updateVisionQuality(scene.getLighting(), visionQualitySliderBounds(panel), mouseX);
            }
            if (draggingVisionPixels) {
                updateVisionPixelSize(
                        scene.getLighting(), visionPixelSizeSliderBounds(panel), mouseX);
            }
        }
        draggingOpacity = false;
        draggingVisionQuality = false;
        draggingVisionPixels = false;
        return true;
    }

    public boolean contains(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        return bounds(screenWidth, screenHeight).contains(mouseX, mouseY);
    }

    public boolean keyPressed(int keyCode, VttScene scene, boolean editable) {
        if (selectedCategory == Category.LIGHTING && focusedLightingHexColor) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                focusedLightingHexColor = false;
                lightingHexBuffer = "";
                replaceLightingHexOnType = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                applyLightingHexBuffer(scene, editable);
                focusedLightingHexColor = false;
                replaceLightingHexOnType = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (replaceLightingHexOnType) {
                    lightingHexBuffer = "";
                    replaceLightingHexOnType = false;
                } else if (!lightingHexBuffer.isEmpty()) {
                    lightingHexBuffer = lightingHexBuffer.substring(
                            0, lightingHexBuffer.length() - 1);
                }
                applyLightingHexBuffer(scene, editable);
                return true;
            }
            return true;
        }
        if (selectedCategory != Category.HUD || focusedHudHexColor == null) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            focusedHudHexColor = null;
            hudHexBuffer = "";
            replaceHudHexOnType = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            applyHudHexBuffer();
            focusedHudHexColor = null;
            replaceHudHexOnType = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (replaceHudHexOnType) {
                hudHexBuffer = "";
                replaceHudHexOnType = false;
            } else if (!hudHexBuffer.isEmpty()) {
                hudHexBuffer = hudHexBuffer.substring(0, hudHexBuffer.length() - 1);
            }
            applyHudHexBuffer();
            return true;
        }
        return true;
    }

    public boolean charTyped(char codePoint, VttScene scene, boolean editable) {
        if (selectedCategory == Category.LIGHTING && focusedLightingHexColor) {
            boolean hash = codePoint == '#';
            boolean hexadecimal = Character.digit(codePoint, 16) >= 0;
            if (!hash && !hexadecimal) return true;
            if (replaceLightingHexOnType) {
                lightingHexBuffer = "";
                replaceLightingHexOnType = false;
            }
            if (hash) {
                if (!lightingHexBuffer.isEmpty()) return true;
                lightingHexBuffer = "#";
            } else {
                int digitCount = lightingHexBuffer.startsWith("#")
                        ? lightingHexBuffer.length() - 1 : lightingHexBuffer.length();
                if (digitCount >= 6) return true;
                lightingHexBuffer += Character.toUpperCase(codePoint);
            }
            applyLightingHexBuffer(scene, editable);
            return true;
        }
        if (selectedCategory != Category.HUD || focusedHudHexColor == null) return false;
        boolean hash = codePoint == '#';
        boolean hexadecimal = Character.digit(codePoint, 16) >= 0;
        if (!hash && !hexadecimal) return true;
        if (replaceHudHexOnType) {
            hudHexBuffer = "";
            replaceHudHexOnType = false;
        }
        if (hash) {
            if (!hudHexBuffer.isEmpty()) return true;
            hudHexBuffer = "#";
        } else {
            int digitCount = hudHexBuffer.startsWith("#")
                    ? hudHexBuffer.length() - 1 : hudHexBuffer.length();
            if (digitCount >= 6) return true;
            hudHexBuffer += Character.toUpperCase(codePoint);
        }
        applyHudHexBuffer();
        return true;
    }

    public boolean isEditSceneButtonAt(
            double mouseX,
            double mouseY,
            int screenWidth,
            int screenHeight,
            VttScene scene,
            boolean editable
    ) {
        return editable && scene != null && hasSceneMaps(scene)
                && selectedCategory == Category.SCENE
                && editSceneBounds(bounds(screenWidth, screenHeight)).contains(mouseX, mouseY);
    }

    public void cancelDrag() {
        draggingOpacity = false;
        draggingVisionQuality = false;
        draggingVisionPixels = false;
        focusedHudHexColor = null;
        focusedLightingHexColor = false;
        replaceHudHexOnType = false;
    }

    public boolean isDraggingOpacity() {
        return draggingOpacity || draggingVisionQuality || draggingVisionPixels;
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
                    EditorHudTheme.selection());
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

        context.graphics().drawString(font, "Maps", contentX, panel.y() + 87, MUTED, false);
        String background = scene.getMaps().isEmpty()
                ? scene.getBackgroundAssetId() == null ? "None" : "Legacy background"
                : scene.getMaps().size() == 1
                ? scene.getMaps().getFirst().getDisplayName()
                : scene.getMaps().size() + " maps in scene";
        context.graphics().drawString(font, ellipsize(font, background, 210),
                contentX, panel.y() + 100,
                hasSceneMaps(scene) ? TEXT : MUTED, false);

        renderSceneButton(context, font, chooseBackgroundBounds(panel),
                "Add Map",
                editable);
        renderSceneButton(context, font, editSceneBounds(panel),
                "Edit Scene", editable && hasSceneMaps(scene));
        renderSceneButton(context, font, removeBackgroundBounds(panel),
                scene.getMaps().isEmpty() ? "Remove Legacy" : "Clear Maps",
                editable && hasSceneMaps(scene));

        context.graphics().drawString(
                font, "Initial View", contentX, panel.y() + 158, MUTED, false);
        String initialView = scene.getInitialCameraView() == null
                ? "Not configured"
                : String.format(Locale.ROOT, "x %.0f  y %.0f  zoom %.2f",
                scene.getInitialCameraView().getX(),
                scene.getInitialCameraView().getY(),
                scene.getInitialCameraView().getZoom());
        context.graphics().drawString(font, initialView,
                contentX, panel.y() + 171,
                scene.getInitialCameraView() == null ? MUTED : TEXT, false);
        renderSceneButton(context, font, setInitialViewBounds(panel),
                "Set Current View", editable);
        renderSceneButton(context, font, resetInitialViewBounds(panel),
                "Reset", editable && scene.getInitialCameraView() != null);
    }

    private void renderHudTheme(
            VRenderContext context,
            Font font,
            Bounds panel
    ) {
        int contentX = panel.x() + CATEGORY_WIDTH + 20;
        context.graphics().drawString(
                font, "HUD Theme", contentX, panel.y() + 31, TEXT, false);
        context.graphics().drawString(
                font, "Saved locally and applied immediately",
                contentX, panel.y() + 44, MUTED, false);

        for (ThemeColor themeColor : ThemeColor.values()) {
            Bounds row = hudColorRowBounds(panel, themeColor);
            boolean selected = themeColor == selectedThemeColor;
            boolean hovered = row.contains(context.mouseX(), context.mouseY());
            context.graphics().fill(
                    row.x(), row.y(), row.right(), row.bottom(),
                    selected ? EditorHudTheme.selection()
                            : hovered ? CONTROL_HOVER : CONTROL_BACKGROUND);
            border(context, row, selected
                    ? EditorHudTheme.opaqueSelection()
                    : EditorHudTheme.outline());
            context.graphics().drawString(
                    font, themeColor.label, row.x() + 7, row.y() + 7,
                    TEXT, false);

            int color = hudColor(themeColor);
            Bounds preview = new Bounds(row.right() - 84, row.y() + 4, 24, 16);
            context.graphics().fill(
                    preview.x(), preview.y(), preview.right(), preview.bottom(),
                    color);
            border(context, preview, EditorHudTheme.outline());
            Bounds hexField = hudHexFieldBounds(row);
            boolean focused = themeColor == focusedHudHexColor;
            context.graphics().fill(
                    hexField.x(), hexField.y(), hexField.right(), hexField.bottom(),
                    focused ? 0xFF25252C : 0xFF18181E);
            border(context, hexField, focused
                    ? EditorHudTheme.opaqueSelection()
                    : EditorHudTheme.outline());
            String value = focused
                    ? hudHexBuffer + (System.currentTimeMillis() / 500L % 2L == 0L ? "_" : "")
                    : colorHex(color);
            context.graphics().drawString(
                    font, value, hexField.x() + 3, hexField.y() + 4,
                    focused ? TEXT : MUTED, false);
        }

        context.graphics().drawString(
                font, "Color", contentX, panel.y() + 157, MUTED, false);
        for (int index = 0; index < HUD_COLOR_PRESETS.length; index++) {
            Bounds swatch = hudPaletteBounds(panel, index);
            int rgb = HUD_COLOR_PRESETS[index];
            context.graphics().fill(
                    swatch.x(), swatch.y(), swatch.right(), swatch.bottom(),
                    0xFF000000 | rgb);
            boolean current = (hudColor(selectedThemeColor) & 0x00FFFFFF) == rgb;
            border(context, swatch, current
                    ? EditorHudTheme.opaqueSelection()
                    : EditorHudTheme.outline());
        }
    }

    private void renderLighting(
            VRenderContext context, Font font, Bounds panel,
            VttSceneLighting lighting, boolean editable
    ) {
        int contentX = panel.x() + CATEGORY_WIDTH + 20;
        context.graphics().drawString(
                font, "Lighting", contentX, panel.y() + 31, TEXT, false);
        if (!editable) {
            context.graphics().drawString(
                    font, "Local display", panel.right() - 76, panel.y() + 31, MUTED, false);
        }

        context.graphics().drawString(
                font, "Darkness Color", contentX, panel.y() + 51, TEXT, false);
        for (int index = 0; index < DARKNESS_COLOR_PRESETS.length; index++) {
            Bounds swatch = darknessColorBounds(panel, index);
            context.graphics().fill(swatch.x(), swatch.y(), swatch.right(), swatch.bottom(),
                    0xFF000000 | DARKNESS_COLOR_PRESETS[index]);
            border(context, swatch,
                    lighting.getDarknessColorRgb() == DARKNESS_COLOR_PRESETS[index]
                            ? EditorHudTheme.opaqueSelection()
                            : EditorHudTheme.outline());
        }
        Bounds hexField = darknessHexFieldBounds(panel);
        context.graphics().fill(hexField.x(), hexField.y(), hexField.right(), hexField.bottom(),
                focusedLightingHexColor ? 0xFF25252C : 0xFF18181E);
        border(context, hexField, focusedLightingHexColor
                ? EditorHudTheme.opaqueSelection() : EditorHudTheme.outline());
        String hexValue = focusedLightingHexColor
                ? lightingHexBuffer + (System.currentTimeMillis() / 500L % 2L == 0L ? "_" : "")
                : colorHex(lighting.getDarknessColorRgb());
        context.graphics().drawString(font, hexValue,
                hexField.x() + 4, hexField.y() + 4,
                focusedLightingHexColor ? TEXT : MUTED, false);

        Bounds pixelSlider = visionPixelSizeSliderBounds(panel);
        context.graphics().drawString(font,
                "Vision Pixel Size  " + lighting.getVisionPixelSize() + " px",
                pixelSlider.x(), pixelSlider.y() - 14, TEXT, false);
        renderLightingSlider(context, pixelSlider,
                (lighting.getVisionPixelSize() - VttSceneLighting.MIN_VISION_PIXEL_SIZE)
                        / (double) (VttSceneLighting.MAX_VISION_PIXEL_SIZE
                        - VttSceneLighting.MIN_VISION_PIXEL_SIZE), true);

        renderSceneButton(context, font, applyDarknessColorBounds(panel),
                "Apply Color to Players", editable);

        Bounds slider = visionQualitySliderBounds(panel);
        context.graphics().drawString(font,
                "Vision Quality  " + lighting.getVisionRayCount() + " rays",
                slider.x(), slider.y() - 14, TEXT, false);
        double progress = (lighting.getVisionRayCount()
                - VttSceneLighting.MIN_VISION_RAY_COUNT)
                / (double) (VttSceneLighting.MAX_VISION_RAY_COUNT
                - VttSceneLighting.MIN_VISION_RAY_COUNT);
        renderLightingSlider(context, slider, progress, editable);

        context.graphics().drawString(font,
                "Larger pixels improve performance",
                contentX, panel.y() + 180, MUTED, false);
        context.graphics().drawString(font,
                "but make vision more pixelated.",
                contentX, panel.y() + 193, MUTED, false);
        context.graphics().drawString(font,
                "Ray count controls the outline.",
                contentX, panel.y() + 210, MUTED, false);
        context.graphics().drawString(font,
                "Ambient light and sources: future",
                contentX, panel.y() + 227, MUTED, false);
    }

    private void renderLightingSlider(
            VRenderContext context, Bounds slider, double progress, boolean editable
    ) {
        context.graphics().fill(
                slider.x(), slider.y() + 3, slider.right(), slider.y() + 6, 0xFF55555A);
        int knobX = slider.x() + (int) Math.round(progress * slider.width());
        context.graphics().fill(
                slider.x(), slider.y() + 3, knobX, slider.y() + 6,
                editable ? EditorHudTheme.opaqueSelection() : MUTED);
        context.graphics().fill(
                knobX - 3, slider.y(), knobX + 4, slider.bottom(),
                editable ? 0xFFFFFFFF : MUTED);
    }

    private int hudColor(ThemeColor color) {
        return switch (color) {
            case OUTLINE -> EditorHudTheme.outline();
            case FOLDER_BACKGROUND -> EditorHudTheme.folderBackground();
            case SELECTION -> EditorHudTheme.selection();
        };
    }

    private void applyHudColor(int rgb) {
        applyHudColor(selectedThemeColor, rgb);
    }

    private void applyHudColor(ThemeColor themeColor, int rgb) {
        int color = (themeColor.alpha << 24) | (rgb & 0x00FFFFFF);
        switch (themeColor) {
            case OUTLINE -> EditorHudTheme.setOutline(color);
            case FOLDER_BACKGROUND -> EditorHudTheme.setFolderBackground(color);
            case SELECTION -> EditorHudTheme.setSelection(color);
        }
    }

    private void applyHudHexBuffer() {
        if (focusedHudHexColor == null) return;
        String value = hudHexBuffer == null ? "" : hudHexBuffer.trim();
        if (value.startsWith("#")) value = value.substring(1);
        if (value.length() != 6) return;
        try {
            applyHudColor(focusedHudHexColor, Integer.parseUnsignedInt(value, 16));
        } catch (NumberFormatException ignored) {
        }
    }

    private void applyLightingHexBuffer(VttScene scene, boolean editable) {
        if (scene == null) return;
        String value = lightingHexBuffer == null ? "" : lightingHexBuffer.trim();
        if (value.startsWith("#")) value = value.substring(1);
        if (value.length() != 6) return;
        try {
            scene.getLighting().setDarknessColorRgb(
                    Integer.parseUnsignedInt(value, 16));
        } catch (NumberFormatException ignored) {
        }
    }

    private String colorHex(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0x00FFFFFF);
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

    private boolean hasSceneMaps(VttScene scene) {
        return scene != null
                && (!scene.getMaps().isEmpty() || scene.getBackgroundAssetId() != null);
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
                    grid.getColorRgb() == COLOR_PRESETS[index]
                            ? EditorHudTheme.opaqueSelection()
                            : EditorHudTheme.outline());
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
                editable ? EditorHudTheme.opaqueSelection() : MUTED);
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
        border(context, toggle, editable ? EditorHudTheme.outline() : MUTED);
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
        border(context, bounds, editable ? EditorHudTheme.outline() : 0xFF55555A);
    }

    private void updateOpacity(VttSceneGrid grid, Bounds slider, double mouseX) {
        grid.setOpacity((mouseX - slider.x()) / slider.width());
    }

    private void updateVisionQuality(
            VttSceneLighting lighting, Bounds slider, double mouseX
    ) {
        double progress = Math.max(0.0,
                Math.min(1.0, (mouseX - slider.x()) / slider.width()));
        int range = VttSceneLighting.MAX_VISION_RAY_COUNT
                - VttSceneLighting.MIN_VISION_RAY_COUNT;
        lighting.setVisionRayCount(VttSceneLighting.MIN_VISION_RAY_COUNT
                + (int) Math.round(progress * range));
    }

    private void updateVisionPixelSize(
            VttSceneLighting lighting, Bounds slider, double mouseX
    ) {
        double progress = Math.max(0.0,
                Math.min(1.0, (mouseX - slider.x()) / slider.width()));
        int range = VttSceneLighting.MAX_VISION_PIXEL_SIZE
                - VttSceneLighting.MIN_VISION_PIXEL_SIZE;
        lighting.setVisionPixelSize(VttSceneLighting.MIN_VISION_PIXEL_SIZE
                + (int) Math.round(progress * range));
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

    private Bounds hudCategoryBounds(Bounds panel) {
        return new Bounds(panel.x() + 8, panel.y() + 93, CATEGORY_WIDTH - 9, 22);
    }

    private Bounds lightingCategoryBounds(Bounds panel) {
        return new Bounds(panel.x() + 8, panel.y() + 118, CATEGORY_WIDTH - 9, 22);
    }

    private Bounds darknessColorBounds(Bounds panel, int index) {
        return new Bounds(
                panel.x() + CATEGORY_WIDTH + 20 + index * 20,
                panel.y() + 66, 16, 16);
    }

    private Bounds visionQualitySliderBounds(Bounds panel) {
        return new Bounds(
                panel.x() + CATEGORY_WIDTH + 20,
                panel.y() + 162, 210, 10);
    }

    private Bounds visionPixelSizeSliderBounds(Bounds panel) {
        return new Bounds(
                panel.x() + CATEGORY_WIDTH + 20,
                panel.y() + 125, 210, 10);
    }

    private Bounds applyDarknessColorBounds(Bounds panel) {
        return new Bounds(
                panel.x() + CATEGORY_WIDTH + 20,
                panel.y() + 91, 142, 20);
    }

    private Bounds darknessHexFieldBounds(Bounds panel) {
        return new Bounds(
                panel.x() + CATEGORY_WIDTH + 178,
                panel.y() + 64, 60, 20);
    }

    private Bounds hudColorRowBounds(Bounds panel, ThemeColor color) {
        return new Bounds(
                panel.x() + CATEGORY_WIDTH + 20,
                panel.y() + 60 + color.ordinal() * 31,
                220, 25);
    }

    private Bounds hudPaletteBounds(Bounds panel, int index) {
        return new Bounds(
                panel.x() + CATEGORY_WIDTH + 20 + index * 18,
                panel.y() + 173, 14, 14);
    }

    private Bounds hudHexFieldBounds(Bounds row) {
        return new Bounds(row.right() - 56, row.y() + 3, 52, 19);
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

    private Bounds setInitialViewBounds(Bounds panel) {
        return new Bounds(panel.x() + CATEGORY_WIDTH + 20, panel.y() + 188, 142, 22);
    }

    private Bounds resetInitialViewBounds(Bounds panel) {
        return new Bounds(panel.x() + CATEGORY_WIDTH + 166, panel.y() + 188, 66, 22);
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
        HUD_THEME_CHANGED,
        CHOOSE_BACKGROUND,
        REMOVE_BACKGROUND,
        EDIT_SCENE,
        SET_INITIAL_VIEW,
        RESET_INITIAL_VIEW,
        APPLY_LIGHTING_COLOR
    }

    private enum Category {
        GRID,
        SCENE,
        HUD,
        LIGHTING
    }

    private enum ThemeColor {
        OUTLINE("Outline", 0xFF),
        FOLDER_BACKGROUND("Folders", 0xAA),
        SELECTION("Select", 0xE0);

        private final String label;
        private final int alpha;

        ThemeColor(String label, int alpha) {
            this.label = label;
            this.alpha = alpha;
        }
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
