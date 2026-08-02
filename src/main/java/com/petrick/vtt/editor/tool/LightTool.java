package com.petrick.vtt.editor.tool;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.tabletop.VttLight;
import com.petrick.vtt.feature.tabletop.VttLightType;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.vision.SceneVisionGeometry;
import com.petrick.vtt.feature.tabletop.vision.SceneVisionRaycaster;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Creates and edits point lights through compact in-canvas controls. */
public final class LightTool implements Tool {
    public static final String ID = "light";
    private static final int YELLOW = 0xFFFFFF33;
    private static final int CYAN = 0xFF55CCFF;
    private static final int HANDLE = 0xFFFFFFFF;
    private static final int PANEL = 0xF018181E;
    private static final int TEXT = 0xFFF4F4F4;
    private static final int MUTED = 0xFF88888E;
    private static final int REVEAL_ONLY = -1;
    private static final int[] COLORS = {
            REVEAL_ONLY,
            0xFFFFFF, 0xFFF1A8, 0xFFCC66, 0xFF8844,
            0xFF5555, 0x55AAFF, 0x66FFFF, 0x88FF88
    };

    private final Supplier<VttScene> sceneSupplier;
    private final Runnable saveAction;
    private final SceneVisionGeometry geometry = new SceneVisionGeometry();
    private final SceneVisionRaycaster raycaster = new SceneVisionRaycaster();
    private String selectedId;
    private Vec2d creationWorld;
    private Popup popup = Popup.NONE;
    private int popupX;
    private int popupY;
    private boolean moving;
    private RadiusHandle radiusHandle;
    private Vec2d dragOffset;
    private Field focusedField;
    private String fieldBuffer = "";
    private boolean replaceFieldOnType;
    private boolean draggingIntensity;

    public LightTool(Supplier<VttScene> sceneSupplier, Runnable saveAction) {
        this.sceneSupplier = sceneSupplier;
        this.saveAction = saveAction;
    }

    @Override public String getId() { return ID; }

    @Override
    public boolean mouseClicked(
            ToolContext context, double mouseX, double mouseY, int button, int modifiers
    ) {
        if (popup != Popup.NONE) return clickPopup(mouseX, mouseY, button);
        Vec2d world = context.renderState().screenToWorld(new Vec2d(mouseX, mouseY));
        VttLight selected = selectedLight();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            RadiusHandle handle = selected == null ? null
                    : handleAt(context, selected, mouseX, mouseY);
            if (handle != null) {
                radiusHandle = handle;
                return true;
            }
            VttLight hit = lightAt(context, mouseX, mouseY);
            if (hit != null) {
                selectedId = hit.getId();
                moving = true;
                dragOffset = new Vec2d(hit.getX() - world.x(), hit.getY() - world.y());
                return true;
            }
            selectedId = null;
            creationWorld = world;
            openPopup(Popup.CREATE, mouseX, mouseY);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            VttLight hit = lightAt(context, mouseX, mouseY);
            if (hit != null) {
                selectedId = hit.getId();
                openPopup(Popup.PROPERTIES, mouseX, mouseY);
                return true;
            }
            creationWorld = world;
            openPopup(Popup.CREATE, mouseX, mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(
            ToolContext context, double mouseX, double mouseY, int button,
            double dragX, double dragY, int modifiers
    ) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (draggingIntensity && popup == Popup.PROPERTIES) {
            updateIntensity(mouseX);
            return true;
        }
        VttLight light = selectedLight();
        if (light == null) return false;
        Vec2d world = context.renderState().screenToWorld(new Vec2d(mouseX, mouseY));
        if (moving && dragOffset != null) {
            light.setX(world.x() + dragOffset.x());
            light.setY(world.y() + dragOffset.y());
            return true;
        }
        if (radiusHandle != null) {
            double distance = Math.hypot(world.x() - light.getX(), world.y() - light.getY());
            if (radiusHandle == RadiusHandle.INNER) light.setInnerRadius(distance);
            else light.setOuterRadius(Math.max(distance, light.getInnerRadius()));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(
            ToolContext context, double mouseX, double mouseY, int button, int modifiers
    ) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingIntensity) {
            updateIntensity(mouseX);
            draggingIntensity = false;
            saveAction.run();
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !moving && radiusHandle == null) return false;
        mouseDragged(context, mouseX, mouseY, button, 0.0, 0.0, modifiers);
        moving = false;
        radiusHandle = null;
        dragOffset = null;
        saveAction.run();
        return true;
    }

    @Override
    public void render(VRenderContext context, ToolContext toolContext) {
        VttScene scene = sceneSupplier.get();
        if (scene == null) return;
        for (VttLight light : scene.getLights()) renderCenter(context, light);
        VttLight selected = selectedLight();
        if (selected != null) renderSelection(context, selected);
        if (popup != Popup.NONE) renderPopup(context, Minecraft.getInstance().font);
    }

    public boolean deleteSelected() {
        VttScene scene = sceneSupplier.get();
        if (scene == null || selectedId == null || !scene.removeLight(selectedId)) return false;
        selectedId = null;
        closePopup();
        saveAction.run();
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (popup == Popup.NONE) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closePopup();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            applyField();
            focusedField = null;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && focusedField != null) {
            if (replaceFieldOnType) {
                fieldBuffer = "";
                replaceFieldOnType = false;
            } else if (!fieldBuffer.isEmpty()) {
                fieldBuffer = fieldBuffer.substring(0, fieldBuffer.length() - 1);
            }
            applyField();
            return true;
        }
        return focusedField != null;
    }

    public boolean charTyped(char character) {
        if (popup != Popup.PROPERTIES || focusedField == null) return false;
        if (replaceFieldOnType) {
            fieldBuffer = "";
            replaceFieldOnType = false;
        }
        if ((character >= '0' && character <= '9') || character == '.'
                || focusedField == Field.COLOR && (character == '#' || Character.digit(character, 16) >= 0)) {
            if (fieldBuffer.length() < 12) fieldBuffer += Character.toUpperCase(character);
            applyField();
        }
        return true;
    }

    public void deactivate() {
        moving = false;
        radiusHandle = null;
        selectedId = null;
        closePopup();
    }

    private void renderCenter(VRenderContext context, VttLight light) {
        Vec2d p = context.renderState().worldToScreen(new Vec2d(light.getX(), light.getY()));
        int x = (int) Math.round(p.x());
        int y = (int) Math.round(p.y());
        context.graphics().fill(x - 4, y - 4, x + 4, y + 4, YELLOW);
        border(context, x - 5, y - 5, 10, 10,
                light.getId().equals(selectedId) ? CYAN : 0xFFFFAA00);
    }

    private void renderSelection(VRenderContext context, VttLight light) {
        Vec2d origin = new Vec2d(light.getX(), light.getY());
        var segments = geometry.build(sceneSupplier.get());
        renderPolygon(context, raycaster.buildVisibilityPolygon(
                origin, light.getOuterRadius(), segments), 0xAA66CCFF);
        renderPolygon(context, raycaster.buildVisibilityPolygon(
                origin, light.getInnerRadius(), segments), 0xAA2299BB);
        renderHandle(context, origin.add(new Vec2d(light.getInnerRadius(), 0.0)));
        renderHandle(context, origin.add(new Vec2d(light.getOuterRadius(), 0.0)));
    }

    private void renderPolygon(VRenderContext context, List<Vec2d> polygon, int color) {
        for (int i = 0; i < polygon.size(); i++) {
            Vec2d a = context.renderState().worldToScreen(polygon.get(i));
            Vec2d b = context.renderState().worldToScreen(polygon.get((i + 1) % polygon.size()));
            renderLine(context, a, b, color);
        }
    }

    private void renderHandle(VRenderContext context, Vec2d world) {
        Vec2d p = context.renderState().worldToScreen(world);
        int x = (int) Math.round(p.x());
        int y = (int) Math.round(p.y());
        context.graphics().fill(x - 3, y - 3, x + 4, y + 4, HANDLE);
        border(context, x - 4, y - 4, 8, 8, CYAN);
    }

    private void renderLine(VRenderContext context, Vec2d a, Vec2d b, int color) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy))));
        for (int i = 0; i <= steps; i++) {
            int x = (int) Math.round(a.x() + dx * i / steps);
            int y = (int) Math.round(a.y() + dy * i / steps);
            context.graphics().fill(x, y, x + 1, y + 1, color);
        }
    }

    private void renderPopup(VRenderContext context, Font font) {
        int width = popup == Popup.CREATE ? 126 : 154;
        int height = popup == Popup.CREATE ? 64 : 198;
        context.graphics().fill(popupX, popupY, popupX + width, popupY + height, PANEL);
        border(context, popupX, popupY, width, height, CYAN);
        if (popup == Popup.CREATE) {
            popupRow(context, font, 0, "Create Point Light", true);
            popupRow(context, font, 1, "Create Spot Light", false);
            popupRow(context, font, 2, "Cancel", true);
            return;
        }
        context.graphics().drawString(font, "Point Light", popupX + 7, popupY + 7, TEXT, false);
        renderField(context, font, Field.OUTER, "Outer Radius", 25);
        renderField(context, font, Field.INNER, "Inner Radius", 47);
        renderField(context, font, Field.COLOR, "Color", 69);
        for (int i = 0; i < COLORS.length; i++) {
            int x = popupX + 7 + i * 16;
            renderColorSwatch(context, x, popupY + 91, COLORS[i]);
        }
        VttLight light = selectedLight();
        double intensity = light == null ? VttLight.DEFAULT_INTENSITY : light.getIntensity();
        context.graphics().drawString(font,
                String.format(Locale.ROOT, "Intensity  %.2fx", intensity),
                popupX + 7, popupY + 111, TEXT, false);
        int trackX = popupX + 7;
        int trackY = popupY + 126;
        int trackWidth = 140;
        context.graphics().fill(trackX, trackY, trackX + trackWidth, trackY + 5, 0xFF55555B);
        double progress = (intensity - VttLight.MIN_INTENSITY)
                / (VttLight.MAX_INTENSITY - VttLight.MIN_INTENSITY);
        int knobX = trackX + (int) Math.round(progress * trackWidth);
        context.graphics().fill(knobX - 2, trackY - 3, knobX + 3, trackY + 8, CYAN);
        popupRow(context, font, 8, "Duplicate", true);
        popupRow(context, font, 9, "Delete", true);
    }

    private void popupRow(VRenderContext context, Font font, int row, String label, boolean enabled) {
        int y = popupY + 5 + row * 19;
        context.graphics().drawString(font, label, popupX + 7, y,
                enabled ? TEXT : MUTED, false);
    }

    private void renderField(VRenderContext context, Font font, Field field, String label, int yOffset) {
        context.graphics().drawString(font, label, popupX + 7, popupY + yOffset + 4, TEXT, false);
        int x = popupX + 79;
        context.graphics().fill(x, popupY + yOffset, popupX + 147, popupY + yOffset + 18, 0xFF101014);
        border(context, x, popupY + yOffset, 68, 18, focusedField == field ? CYAN : 0xFF66666C);
        String value = focusedField == field ? fieldBuffer : valueOf(field);
        context.graphics().drawString(font, value, x + 4, popupY + yOffset + 5, TEXT, false);
    }

    private boolean clickPopup(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        if (popup == Popup.CREATE) {
            int row = (int) ((mouseY - popupY - 3) / 19);
            if (mouseX >= popupX && mouseX <= popupX + 126) {
                if (row == 0) createPointLight();
                if (row == 2) closePopup();
            }
            return true;
        }
        for (Field field : Field.values()) {
            int y = popupY + switch (field) { case OUTER -> 25; case INNER -> 47; case COLOR -> 69; };
            if (mouseX >= popupX + 79 && mouseX <= popupX + 147
                    && mouseY >= y && mouseY <= y + 18) {
                focusedField = field;
                fieldBuffer = valueOf(field);
                replaceFieldOnType = true;
                return true;
            }
        }
        if (mouseX >= popupX + 7 && mouseX <= popupX + 151
                && mouseY >= popupY + 91 && mouseY <= popupY + 104) {
            int index = (int) ((mouseX - popupX - 7) / 16);
            VttLight light = selectedLight();
            if (light != null && index >= 0 && index < COLORS.length) {
                if (COLORS[index] == REVEAL_ONLY) {
                    light.setTintEnabled(false);
                } else {
                    light.setColorRgb(COLORS[index]);
                    light.setTintEnabled(true);
                }
                saveAction.run();
            }
            return true;
        }
        if (mouseX >= popupX + 7 && mouseX <= popupX + 147
                && mouseY >= popupY + 119 && mouseY <= popupY + 137) {
            draggingIntensity = true;
            updateIntensity(mouseX);
            return true;
        }
        if (mouseX >= popupX && mouseX <= popupX + 154) {
            int row = (int) ((mouseY - popupY - 5) / 19);
            if (row == 8) duplicateSelected();
            else if (row == 9) deleteSelected();
        }
        return true;
    }

    private boolean duplicateSelected() {
        VttScene scene = sceneSupplier.get();
        VttLight source = selectedLight();
        if (scene == null || source == null) return false;
        VttLight copy = new VttLight(nextId(scene), source.getX() + 32.0, source.getY() + 32.0);
        copy.setType(source.getType());
        copy.setOuterRadius(source.getOuterRadius());
        copy.setInnerRadius(source.getInnerRadius());
        copy.setColorRgb(source.getColorRgb());
        copy.setIntensity(source.getIntensity());
        copy.setTintEnabled(source.isTintEnabled());
        copy.setEnabled(source.isEnabled());
        scene.addLight(copy);
        selectedId = copy.getId();
        closePopup();
        saveAction.run();
        return true;
    }

    private void createPointLight() {
        VttScene scene = sceneSupplier.get();
        if (scene == null || creationWorld == null) return;
        VttLight light = new VttLight(nextId(scene), creationWorld.x(), creationWorld.y());
        light.setType(VttLightType.POINT);
        scene.addLight(light);
        selectedId = light.getId();
        closePopup();
        saveAction.run();
    }

    private void applyField() {
        VttLight light = selectedLight();
        if (light == null || focusedField == null) return;
        try {
            if (focusedField == Field.COLOR) {
                String hex = fieldBuffer.startsWith("#") ? fieldBuffer.substring(1) : fieldBuffer;
                if (hex.length() == 6) {
                    light.setColorRgb(Integer.parseUnsignedInt(hex, 16));
                    light.setTintEnabled(true);
                }
            } else {
                double value = Double.parseDouble(fieldBuffer);
                if (focusedField == Field.OUTER) light.setOuterRadius(Math.max(value, light.getInnerRadius()));
                else light.setInnerRadius(Math.min(value, light.getOuterRadius()));
            }
            saveAction.run();
        } catch (NumberFormatException ignored) {}
    }

    private String valueOf(Field field) {
        VttLight light = selectedLight();
        if (light == null) return "";
        return switch (field) {
            case OUTER -> String.format(Locale.ROOT, "%.0f", light.getOuterRadius());
            case INNER -> String.format(Locale.ROOT, "%.0f", light.getInnerRadius());
            case COLOR -> light.isTintEnabled()
                    ? String.format(Locale.ROOT, "#%06X", light.getColorRgb()) : "None";
        };
    }

    private void renderColorSwatch(VRenderContext context, int x, int y, int color) {
        VttLight selected = selectedLight();
        boolean active = selected != null && (color == REVEAL_ONLY
                ? !selected.isTintEnabled()
                : selected.isTintEnabled() && selected.getColorRgb() == color);
        if (color == REVEAL_ONLY) {
            int cell = 4;
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 4; column++) {
                    int shade = (row + column) % 2 == 0 ? 0xFF55555B : 0xFFBBBBBF;
                    int left = x + column * cell;
                    int top = y + row * cell;
                    context.graphics().fill(left, top,
                            Math.min(x + 13, left + cell), Math.min(y + 13, top + cell), shade);
                }
            }
        } else {
            context.graphics().fill(x, y, x + 13, y + 13, 0xFF000000 | color);
        }
        border(context, x, y, 13, 13, active ? CYAN : 0xFFAAAAAA);
    }

    private VttLight lightAt(ToolContext context, double mouseX, double mouseY) {
        VttScene scene = sceneSupplier.get();
        if (scene == null) return null;
        List<VttLight> lights = scene.getLights();
        for (int i = lights.size() - 1; i >= 0; i--) {
            VttLight light = lights.get(i);
            Vec2d p = context.renderState().worldToScreen(new Vec2d(light.getX(), light.getY()));
            if (Math.abs(mouseX - p.x()) <= 6 && Math.abs(mouseY - p.y()) <= 6) return light;
        }
        return null;
    }

    private RadiusHandle handleAt(
            ToolContext context, VttLight light, double mouseX, double mouseY
    ) {
        Vec2d origin = new Vec2d(light.getX(), light.getY());
        Vec2d inner = context.renderState().worldToScreen(
                origin.add(new Vec2d(light.getInnerRadius(), 0.0)));
        Vec2d outer = context.renderState().worldToScreen(
                origin.add(new Vec2d(light.getOuterRadius(), 0.0)));
        if (Math.hypot(mouseX - inner.x(), mouseY - inner.y()) <= 9) return RadiusHandle.INNER;
        if (Math.hypot(mouseX - outer.x(), mouseY - outer.y()) <= 9) return RadiusHandle.OUTER;
        return null;
    }

    private VttLight selectedLight() {
        VttScene scene = sceneSupplier.get();
        if (scene == null || selectedId == null) return null;
        return scene.getLights().stream().filter(light -> selectedId.equals(light.getId()))
                .findFirst().orElse(null);
    }

    private String nextId(VttScene scene) {
        int index = 1;
        while (true) {
            String id = "light_" + index++;
            if (scene.getLights().stream().noneMatch(light -> id.equals(light.getId()))) return id;
        }
    }

    private void openPopup(Popup popup, double mouseX, double mouseY) {
        this.popup = popup;
        int popupWidth = popup == Popup.CREATE ? 126 : 154;
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        popupX = Math.max(4, Math.min(screenWidth - popupWidth - 4,
                (int) Math.round(mouseX) + 8));
        popupY = Math.max(4, (int) Math.round(mouseY)
                - (popup == Popup.CREATE ? 64 : 198));
        focusedField = null;
        draggingIntensity = false;
    }

    private void updateIntensity(double mouseX) {
        VttLight light = selectedLight();
        if (light == null) return;
        double progress = Math.max(0.0, Math.min(1.0,
                (mouseX - (popupX + 7.0)) / 140.0));
        light.setIntensity(VttLight.MIN_INTENSITY
                + progress * (VttLight.MAX_INTENSITY - VttLight.MIN_INTENSITY));
    }

    private void closePopup() {
        popup = Popup.NONE;
        creationWorld = null;
        focusedField = null;
        fieldBuffer = "";
        replaceFieldOnType = false;
        draggingIntensity = false;
    }

    private void border(VRenderContext context, int x, int y, int w, int h, int color) {
        context.graphics().hLine(x, x + w, y, color);
        context.graphics().hLine(x, x + w, y + h, color);
        context.graphics().vLine(x, y, y + h, color);
        context.graphics().vLine(x + w, y, y + h, color);
    }

    private enum Popup { NONE, CREATE, PROPERTIES }
    private enum RadiusHandle { INNER, OUTER }
    private enum Field { OUTER, INNER, COLOR }
}
