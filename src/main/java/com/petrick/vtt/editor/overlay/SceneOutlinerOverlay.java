package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.editor.hud.EditorHudTheme;
import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.feature.selection.SelectionManager;
import com.petrick.vtt.feature.tabletop.VttLight;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttSceneMap;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Lists scene maps, canvas objects and lights in separate selectable sections. */
public final class SceneOutlinerOverlay {
    private static final int PANEL_WIDTH = 220;
    private static final int PADDING = 8;
    private static final int LINE_HEIGHT = 10;
    private static final int PANEL_X = 10;
    private static final int PANEL_Y = 105;
    private static final int MAX_VISIBLE_MAPS = 5;
    private static final int MAX_VISIBLE_OBJECTS = 10;
    private static final int MAX_VISIBLE_LIGHTS = 8;
    private static final int PANEL_BACKGROUND = 0xAA000000;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int TEXT_COLOR = 0xFFDDDDDD;
    private static final int MUTED_TEXT_COLOR = 0xFF888888;

    private int mapScrollOffset;
    private int objectScrollOffset;
    private int lightScrollOffset;
    private ScrollSection draggingScrollbar = ScrollSection.NONE;

    public void render(
            VRenderContext context,
            Font font,
            CanvasScene canvasScene,
            VttScene vttScene,
            SelectionManager selectionManager,
            String selectedMapId,
            String selectedLightId
    ) {
        List<VttSceneMap> maps = sortedMaps(vttScene);
        List<CanvasObject> objects = canvasScene.getObjects();
        List<VttLight> lights = lights(vttScene);
        Layout layout = layout(maps.size(), objects.size(), lights.size());
        renderPanelBackground(context, PANEL_X, PANEL_Y, PANEL_WIDTH, layout.panelHeight());

        int textX = PANEL_X + PADDING;
        drawLine(context, font, "Scene Outliner", textX, PANEL_Y + PADDING, TITLE_COLOR);
        drawLine(context, font, "Maps: " + maps.size(), textX, layout.mapsTitleY(), TEXT_COLOR);
        if (maps.isEmpty()) {
            drawLine(context, font, "  No maps", textX, layout.firstMapY(), MUTED_TEXT_COLOR);
        } else {
            for (int index = 0; index < layout.visibleMaps(); index++) {
                VttSceneMap map = maps.get(mapScrollOffset + index);
                boolean selected = map.getId().equals(selectedMapId);
                String text = (selected ? "> " : "  ")
                        + (map.isVisible() ? "[V] " : "[H] ")
                        + "[" + map.getLayerIndex() + "] " + map.getDisplayName();
                drawLine(context, font, text, textX,
                        layout.firstMapY() + index * LINE_HEIGHT,
                        selected ? EditorHudTheme.opaqueSelection()
                                : map.isVisible() ? TEXT_COLOR : MUTED_TEXT_COLOR);
            }
            if (maps.size() > MAX_VISIBLE_MAPS) {
                drawLine(context, font, range(
                        mapScrollOffset, layout.visibleMaps(), maps.size()),
                        textX, layout.mapRangeY(), MUTED_TEXT_COLOR);
                EditorScrollbar.render(context, scrollbarX(), layout.firstMapY(), 4,
                        MAX_VISIBLE_MAPS * LINE_HEIGHT, maps.size(), MAX_VISIBLE_MAPS,
                        mapScrollOffset, EditorHudTheme.outline());
            }
        }

        drawLine(context, font, "Objects: " + objects.size(),
                textX, layout.objectsTitleY(), TEXT_COLOR);
        if (objects.isEmpty()) {
            drawLine(context, font, "  No objects",
                    textX, layout.firstObjectY(), MUTED_TEXT_COLOR);
        } else {
            for (int index = 0; index < layout.visibleObjects(); index++) {
                CanvasObject object = objects.get(objectScrollOffset + index);
                boolean selected = selectionManager.isSelected(object.id());
                int layerIndex = canvasScene.getObjectLayerIndex(object.id());
                String text = (selected ? "> " : "  ")
                        + (object.visible() ? "[V] " : "[H] ")
                        + "[" + layerIndex + "] " + object.displayName()
                        + " {" + object.activeStateId() + "}";
                drawLine(context, font, text, textX,
                        layout.firstObjectY() + index * LINE_HEIGHT,
                        selected ? EditorHudTheme.opaqueSelection()
                                : object.visible() ? TEXT_COLOR : MUTED_TEXT_COLOR);
            }
            if (objects.size() > MAX_VISIBLE_OBJECTS) {
                drawLine(context, font, range(
                        objectScrollOffset, layout.visibleObjects(), objects.size()),
                        textX, layout.objectRangeY(), MUTED_TEXT_COLOR);
                EditorScrollbar.render(context, scrollbarX(), layout.firstObjectY(), 4,
                        MAX_VISIBLE_OBJECTS * LINE_HEIGHT, objects.size(),
                        MAX_VISIBLE_OBJECTS, objectScrollOffset,
                        EditorHudTheme.outline());
            }
        }

        drawLine(context, font, "Lights: " + lights.size(),
                textX, layout.lightsTitleY(), TEXT_COLOR);
        if (lights.isEmpty()) {
            drawLine(context, font, "  No lights",
                    textX, layout.firstLightY(), MUTED_TEXT_COLOR);
        } else {
            for (int index = 0; index < layout.visibleLights(); index++) {
                VttLight light = lights.get(lightScrollOffset + index);
                boolean selected = light.getId().equals(selectedLightId);
                String text = (selected ? "> " : "  ")
                        + (light.isEnabled() ? "[V] " : "[H] ")
                        + light.getType().name() + " " + light.getId();
                drawLine(context, font, text, textX,
                        layout.firstLightY() + index * LINE_HEIGHT,
                        selected ? EditorHudTheme.opaqueSelection()
                                : light.isEnabled() ? TEXT_COLOR : MUTED_TEXT_COLOR);
            }
            if (lights.size() > MAX_VISIBLE_LIGHTS) {
                drawLine(context, font, range(
                        lightScrollOffset, layout.visibleLights(), lights.size()),
                        textX, layout.lightRangeY(), MUTED_TEXT_COLOR);
                EditorScrollbar.render(context, scrollbarX(), layout.firstLightY(), 4,
                        MAX_VISIBLE_LIGHTS * LINE_HEIGHT, lights.size(), MAX_VISIBLE_LIGHTS,
                        lightScrollOffset, EditorHudTheme.outline());
            }
        }
    }

    public Optional<String> findMapIdAt(
            VttScene scene, CanvasScene canvasScene, double mouseX, double mouseY
    ) {
        List<VttSceneMap> maps = sortedMaps(scene);
        Layout layout = layout(maps.size(), canvasScene.getObjects().size(), lightCount(scene));
        if (!insidePanel(mouseX, mouseY, layout)) return Optional.empty();
        for (int index = 0; index < layout.visibleMaps(); index++) {
            if (insideRow(mouseY, layout.firstMapY() + index * LINE_HEIGHT)) {
                return Optional.of(maps.get(mapScrollOffset + index).getId());
            }
        }
        return Optional.empty();
    }

    public Optional<String> findObjectIdAt(
            CanvasScene scene, VttScene vttScene, double mouseX, double mouseY
    ) {
        List<CanvasObject> objects = scene.getObjects();
        Layout layout = layout(sortedMaps(vttScene).size(), objects.size(), lightCount(vttScene));
        if (!insidePanel(mouseX, mouseY, layout)) return Optional.empty();
        for (int index = 0; index < layout.visibleObjects(); index++) {
            if (insideRow(mouseY, layout.firstObjectY() + index * LINE_HEIGHT)) {
                return Optional.of(objects.get(objectScrollOffset + index).id());
            }
        }
        return Optional.empty();
    }

    public Optional<String> findLightIdAt(
            CanvasScene canvasScene, VttScene vttScene, double mouseX, double mouseY
    ) {
        List<VttLight> lights = lights(vttScene);
        Layout layout = layout(sortedMaps(vttScene).size(),
                canvasScene.getObjects().size(), lights.size());
        if (!insidePanel(mouseX, mouseY, layout)) return Optional.empty();
        for (int index = 0; index < layout.visibleLights(); index++) {
            if (insideRow(mouseY, layout.firstLightY() + index * LINE_HEIGHT)) {
                return Optional.of(lights.get(lightScrollOffset + index).getId());
            }
        }
        return Optional.empty();
    }

    public boolean mouseClickedScrollbar(
            CanvasScene canvasScene, VttScene vttScene, double mouseX, double mouseY
    ) {
        List<VttSceneMap> maps = sortedMaps(vttScene);
        int objectCount = canvasScene.getObjects().size();
        int lightCount = lightCount(vttScene);
        Layout layout = layout(maps.size(), objectCount, lightCount);
        if (maps.size() > MAX_VISIBLE_MAPS && EditorScrollbar.contains(
                mouseX, mouseY, scrollbarX() - 3, layout.firstMapY(),
                10, MAX_VISIBLE_MAPS * LINE_HEIGHT)) {
            draggingScrollbar = ScrollSection.MAPS;
            updateMapScrollFromMouse(maps.size(), layout, mouseY);
            return true;
        }
        if (lightCount > MAX_VISIBLE_LIGHTS && EditorScrollbar.contains(
                mouseX, mouseY, scrollbarX() - 3, layout.firstLightY(),
                10, MAX_VISIBLE_LIGHTS * LINE_HEIGHT)) {
            draggingScrollbar = ScrollSection.LIGHTS;
            updateLightScrollFromMouse(lightCount, layout, mouseY);
            return true;
        }
        if (objectCount > MAX_VISIBLE_OBJECTS && EditorScrollbar.contains(
                mouseX, mouseY, scrollbarX() - 3, layout.firstObjectY(),
                10, MAX_VISIBLE_OBJECTS * LINE_HEIGHT)) {
            draggingScrollbar = ScrollSection.OBJECTS;
            updateObjectScrollFromMouse(objectCount, layout, mouseY);
            return true;
        }
        return false;
    }

    public boolean mouseDraggedScrollbar(
            CanvasScene canvasScene, VttScene vttScene, double mouseY
    ) {
        List<VttSceneMap> maps = sortedMaps(vttScene);
        int lightCount = lightCount(vttScene);
        Layout layout = layout(maps.size(), canvasScene.getObjects().size(), lightCount);
        if (draggingScrollbar == ScrollSection.MAPS) {
            updateMapScrollFromMouse(maps.size(), layout, mouseY);
            return true;
        }
        if (draggingScrollbar == ScrollSection.LIGHTS) {
            updateLightScrollFromMouse(lightCount, layout, mouseY);
            return true;
        }
        if (draggingScrollbar == ScrollSection.OBJECTS) {
            updateObjectScrollFromMouse(canvasScene.getObjects().size(), layout, mouseY);
            return true;
        }
        return false;
    }

    public boolean mouseReleasedScrollbar() {
        boolean wasDragging = draggingScrollbar != ScrollSection.NONE;
        draggingScrollbar = ScrollSection.NONE;
        return wasDragging;
    }

    public boolean mouseScrolled(
            CanvasScene canvasScene, VttScene vttScene,
            double mouseX, double mouseY, double scrollY
    ) {
        List<VttSceneMap> maps = sortedMaps(vttScene);
        int objectCount = canvasScene.getObjects().size();
        int lightCount = lightCount(vttScene);
        Layout layout = layout(maps.size(), objectCount, lightCount);
        if (!insidePanel(mouseX, mouseY, layout)) return false;
        int delta = scrollY < 0 ? 1 : scrollY > 0 ? -1 : 0;
        if (mouseY < layout.objectsTitleY()) {
            mapScrollOffset = EditorScrollbar.clampOffset(
                    mapScrollOffset + delta, maps.size(), MAX_VISIBLE_MAPS);
        } else if (mouseY < layout.lightsTitleY()) {
            objectScrollOffset = EditorScrollbar.clampOffset(
                    objectScrollOffset + delta, objectCount, MAX_VISIBLE_OBJECTS);
        } else {
            lightScrollOffset = EditorScrollbar.clampOffset(
                    lightScrollOffset + delta, lightCount, MAX_VISIBLE_LIGHTS);
        }
        return true;
    }

    public boolean contains(
            CanvasScene canvasScene, VttScene vttScene, double mouseX, double mouseY
    ) {
        return insidePanel(mouseX, mouseY,
                layout(sortedMaps(vttScene).size(), canvasScene.getObjects().size(),
                        lightCount(vttScene)));
    }

    private Layout layout(int mapCount, int objectCount, int lightCount) {
        mapScrollOffset = EditorScrollbar.clampOffset(
                mapScrollOffset, mapCount, MAX_VISIBLE_MAPS);
        objectScrollOffset = EditorScrollbar.clampOffset(
                objectScrollOffset, objectCount, MAX_VISIBLE_OBJECTS);
        lightScrollOffset = EditorScrollbar.clampOffset(
                lightScrollOffset, lightCount, MAX_VISIBLE_LIGHTS);
        int visibleMaps = Math.min(MAX_VISIBLE_MAPS, mapCount);
        int visibleObjects = Math.min(MAX_VISIBLE_OBJECTS, objectCount);
        int visibleLights = Math.min(MAX_VISIBLE_LIGHTS, lightCount);
        int mapsTitleY = PANEL_Y + PADDING + LINE_HEIGHT + 4;
        int firstMapY = mapsTitleY + LINE_HEIGHT + 2;
        int mapRows = Math.max(1, visibleMaps);
        int mapRangeY = firstMapY + mapRows * LINE_HEIGHT;
        int objectsTitleY = mapRangeY + (mapCount > MAX_VISIBLE_MAPS ? LINE_HEIGHT : 0) + 5;
        int firstObjectY = objectsTitleY + LINE_HEIGHT + 2;
        int objectRows = Math.max(1, visibleObjects);
        int objectRangeY = firstObjectY + objectRows * LINE_HEIGHT;
        int lightsTitleY = objectRangeY
                + (objectCount > MAX_VISIBLE_OBJECTS ? LINE_HEIGHT : 0) + 5;
        int firstLightY = lightsTitleY + LINE_HEIGHT + 2;
        int lightRows = Math.max(1, visibleLights);
        int lightRangeY = firstLightY + lightRows * LINE_HEIGHT;
        int bottom = lightRangeY + (lightCount > MAX_VISIBLE_LIGHTS ? LINE_HEIGHT : 0);
        return new Layout(visibleMaps, visibleObjects, visibleLights, mapsTitleY, firstMapY,
                mapRangeY, objectsTitleY, firstObjectY, objectRangeY,
                lightsTitleY, firstLightY, lightRangeY,
                bottom - PANEL_Y + PADDING);
    }

    private List<VttSceneMap> sortedMaps(VttScene scene) {
        if (scene == null) return List.of();
        return scene.getMaps().stream().filter(map -> map != null)
                .sorted(Comparator.comparingInt(VttSceneMap::getLayerIndex).reversed())
                .toList();
    }

    private List<VttLight> lights(VttScene scene) {
        if (scene == null) return List.of();
        return scene.getLights().stream().filter(light -> light != null).toList();
    }

    private int lightCount(VttScene scene) {
        return lights(scene).size();
    }

    private void updateMapScrollFromMouse(int count, Layout layout, double mouseY) {
        mapScrollOffset = EditorScrollbar.offsetForMouse(
                mouseY, layout.firstMapY(), MAX_VISIBLE_MAPS * LINE_HEIGHT,
                count, MAX_VISIBLE_MAPS);
    }

    private void updateObjectScrollFromMouse(int count, Layout layout, double mouseY) {
        objectScrollOffset = EditorScrollbar.offsetForMouse(
                mouseY, layout.firstObjectY(), MAX_VISIBLE_OBJECTS * LINE_HEIGHT,
                count, MAX_VISIBLE_OBJECTS);
    }

    private void updateLightScrollFromMouse(int count, Layout layout, double mouseY) {
        lightScrollOffset = EditorScrollbar.offsetForMouse(
                mouseY, layout.firstLightY(), MAX_VISIBLE_LIGHTS * LINE_HEIGHT,
                count, MAX_VISIBLE_LIGHTS);
    }

    private boolean insidePanel(double x, double y, Layout layout) {
        return x >= PANEL_X && x <= PANEL_X + PANEL_WIDTH
                && y >= PANEL_Y && y <= PANEL_Y + layout.panelHeight();
    }

    private boolean insideRow(double mouseY, int rowY) {
        return mouseY >= rowY && mouseY <= rowY + LINE_HEIGHT;
    }

    private int scrollbarX() {
        return PANEL_X + PANEL_WIDTH - PADDING - 4;
    }

    private String range(int offset, int visible, int total) {
        return (offset + 1) + "-" + (offset + visible) + " / " + total;
    }

    private void renderPanelBackground(
            VRenderContext context, int x, int y, int width, int height
    ) {
        context.graphics().fill(x, y, x + width, y + height, PANEL_BACKGROUND);
        context.graphics().hLine(x, x + width, y, EditorHudTheme.outline());
        context.graphics().hLine(
                x, x + width, y + height, EditorHudTheme.outline());
        context.graphics().vLine(x, y, y + height, EditorHudTheme.outline());
        context.graphics().vLine(
                x + width, y, y + height, EditorHudTheme.outline());
    }

    private void drawLine(
            VRenderContext context, Font font, String text, int x, int y, int color
    ) {
        context.graphics().drawString(font, text, x, y, color, false);
    }

    private enum ScrollSection { NONE, MAPS, OBJECTS, LIGHTS }

    private record Layout(
            int visibleMaps,
            int visibleObjects,
            int visibleLights,
            int mapsTitleY,
            int firstMapY,
            int mapRangeY,
            int objectsTitleY,
            int firstObjectY,
            int objectRangeY,
            int lightsTitleY,
            int firstLightY,
            int lightRangeY,
            int panelHeight
    ) {}
}
