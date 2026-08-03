package com.petrick.vtt.editor.tool;

import com.petrick.vtt.VTT;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.tabletop.VttLight;
import com.petrick.vtt.feature.tabletop.VttLightType;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.editor.hud.HexColorFormat;
import com.petrick.vtt.editor.hud.EditorColorPickerOverlay;
import com.petrick.vtt.feature.tabletop.vision.SceneVisionGeometry;
import com.petrick.vtt.feature.tabletop.vision.SceneVisionRaycaster;
import com.petrick.vtt.feature.attachment.AttachmentBindingService;
import com.petrick.vtt.feature.attachment.AttachmentVisibilityResolver;
import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Creates and edits point and spot lights through compact in-canvas controls. */
public final class LightTool implements Tool {
    public static final String ID = "light";
    private static final ResourceLocation LIGHT_ICON = ResourceLocation.fromNamespaceAndPath(
            VTT.MOD_ID, "textures/gui/editor_hud/light.png");
    private static final int LIGHT_ICON_TEXTURE_SIZE = 16;
    private static final int YELLOW = 0xFFFFFF33;
    private static final int CYAN = 0xFF55CCFF;
    private static final int HANDLE = 0xFFFFFFFF;
    private static final int PANEL = 0xF018181E;
    private static final int TEXT = 0xFFF4F4F4;
    private static final int MUTED = 0xFF88888E;
    private static final double DIRECTION_HANDLE_OFFSET = 44.0;
    private static final double CONE_HANDLE_OFFSET = 24.0;
    private static final double INNER_CONE_HANDLE_OFFSET = 8.0;
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
    private LightHandle editHandle;
    private Vec2d dragOffset;
    private Field focusedField;
    private String fieldBuffer = "";
    private boolean replaceFieldOnType;
    private boolean draggingIntensity;
    private final EditorColorPickerOverlay colorPicker = new EditorColorPickerOverlay();
    private CanvasScene lastCanvasScene;
    private boolean attachmentTargetsOpen;
    private CanvasObject lightDropAttachmentTarget;

    public LightTool(Supplier<VttScene> sceneSupplier, Runnable saveAction) {
        this.sceneSupplier = sceneSupplier;
        this.saveAction = saveAction;
    }

    @Override public String getId() { return ID; }

    @Override
    public boolean mouseClicked(
            ToolContext context, double mouseX, double mouseY, int button, int modifiers
    ) {
        lastCanvasScene = context.scene();
        if (colorPicker.isOpen()) {
            return colorPicker.mouseClicked(
                    mouseX, mouseY, button,
                    screenWidth(), screenHeight());
        }
        if (popup != Popup.NONE) return clickPopup(mouseX, mouseY, button);
        Vec2d world = context.renderState().screenToWorld(new Vec2d(mouseX, mouseY));
        VttLight selected = selectedLight();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            LightHandle handle = selected == null ? null
                    : handleAt(context, selected, mouseX, mouseY);
            if (handle != null) {
                editHandle = handle;
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
        if (colorPicker.isOpen()) {
            return colorPicker.mouseDragged(
                    mouseX, mouseY,
                    screenWidth(), screenHeight());
        }
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
            lightDropAttachmentTarget = attachmentTargetAt(context.scene(), world);
            return true;
        }
        if (editHandle != null) {
            double distance = Math.hypot(world.x() - light.getX(), world.y() - light.getY());
            if (editHandle == LightHandle.INNER) {
                light.setInnerRadius(distance);
            } else if (editHandle == LightHandle.OUTER) {
                light.setOuterRadius(Math.max(distance, light.getInnerRadius()));
            } else if (editHandle == LightHandle.DIRECTION) {
                light.setDirectionDegrees(Math.toDegrees(Math.atan2(
                        world.y() - light.getY(), world.x() - light.getX())));
            } else if (editHandle == LightHandle.OUTER_CONE) {
                double pointer = Math.toDegrees(Math.atan2(
                        world.y() - light.getY(), world.x() - light.getX()));
                double difference = Math.abs(shortestAngleDegrees(
                        pointer - light.getDirectionDegrees()));
                light.setConeAngleDegrees(difference * 2.0);
            } else if (editHandle == LightHandle.INNER_CONE) {
                double pointer = Math.toDegrees(Math.atan2(
                        world.y() - light.getY(), world.x() - light.getX()));
                double difference = Math.abs(shortestAngleDegrees(
                        pointer - light.getDirectionDegrees()));
                light.setInnerConeAngleDegrees(difference * 2.0);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(
            ToolContext context, double mouseX, double mouseY, int button, int modifiers
    ) {
        if (colorPicker.isOpen()) return colorPicker.mouseReleased();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingIntensity) {
            updateIntensity(mouseX);
            draggingIntensity = false;
            saveAction.run();
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !moving && editHandle == null) return false;
        mouseDragged(context, mouseX, mouseY, button, 0.0, 0.0, modifiers);
        CanvasObject dropTarget = moving
                ? attachmentTargetAt(context.scene(), context.renderState().screenToWorld(
                new Vec2d(mouseX, mouseY))) : null;
        VttLight releasedLight = selectedLight();
        if (dropTarget != null && releasedLight != null) {
            AttachmentBindingService.bindLight(
                    sceneSupplier.get(), context.scene(), releasedLight.getId(), dropTarget.id());
        }
        AttachmentBindingService.recaptureLight(
                sceneSupplier.get(), context.scene(), selectedId);
        moving = false;
        editHandle = null;
        dragOffset = null;
        lightDropAttachmentTarget = null;
        saveAction.run();
        return true;
    }

    @Override
    public void render(VRenderContext context, ToolContext toolContext) {
        lastCanvasScene = toolContext.scene();
        VttScene scene = sceneSupplier.get();
        if (scene == null) return;
        for (VttLight light : scene.getLights()) {
            if (AttachmentVisibilityResolver.isLightEffectivelyVisible(
                    scene, toolContext.scene(), light)) {
                renderCenter(context, light);
            }
        }
        VttLight selected = selectedLight();
        if (selected != null && AttachmentVisibilityResolver.isLightEffectivelyVisible(
                scene, toolContext.scene(), selected)) {
            renderSelection(context, selected);
        }
        if (moving && lightDropAttachmentTarget != null) {
            renderAttachmentDropTarget(context, lightDropAttachmentTarget);
        }
        if (popup != Popup.NONE) renderPopup(context, Minecraft.getInstance().font);
        colorPicker.render(context, Minecraft.getInstance().font);
    }

    public boolean deleteSelected() {
        VttScene scene = sceneSupplier.get();
        if (scene == null || selectedId == null || !scene.removeLight(selectedId)) return false;
        selectedId = null;
        closePopup();
        saveAction.run();
        return true;
    }

    public boolean selectLight(String lightId) {
        VttScene scene = sceneSupplier.get();
        if (scene == null || lightId == null || scene.getLights().stream()
                .noneMatch(light -> light != null && lightId.equals(light.getId()))) {
            return false;
        }
        selectedId = lightId;
        closePopup();
        return true;
    }

    public String getSelectedLightId() {
        return selectedLight() == null ? null : selectedId;
    }

    public boolean keyPressed(int keyCode) {
        if (colorPicker.isOpen()) return colorPicker.keyPressed(keyCode);
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
        if (colorPicker.isOpen()) return colorPicker.charTyped(character);
        if (popup != Popup.PROPERTIES || focusedField == null) return false;
        if (focusedField == Field.COLOR) {
            if (!HexColorFormat.accepts(character)) return true;
            if (replaceFieldOnType) {
                fieldBuffer = "";
                replaceFieldOnType = false;
            }
            String updated = HexColorFormat.append(fieldBuffer, character);
            if (updated.equals(fieldBuffer)) return true;
            fieldBuffer = updated;
            applyField();
            return true;
        }
        if ((character >= '0' && character <= '9') || character == '.') {
            if (replaceFieldOnType) {
                fieldBuffer = "";
                replaceFieldOnType = false;
            }
            if (fieldBuffer.length() < 12) fieldBuffer += character;
            applyField();
        }
        return true;
    }

    public void deactivate() {
        colorPicker.cancel();
        moving = false;
        editHandle = null;
        lightDropAttachmentTarget = null;
        selectedId = null;
        closePopup();
    }

    private void renderCenter(VRenderContext context, VttLight light) {
        Vec2d p = context.renderState().worldToScreen(new Vec2d(light.getX(), light.getY()));
        int x = (int) Math.round(p.x());
        int y = (int) Math.round(p.y());
        context.graphics().fill(x - 7, y - 7, x + 7, y + 7, YELLOW);
        border(context, x - 7, y - 7, 14, 14,
                light.getId().equals(selectedId) ? CYAN : 0xFFFFAA00);
        context.graphics().blit(
                LIGHT_ICON, x - 5, y - 5, 10, 10,
                0.0F, 0.0F, LIGHT_ICON_TEXTURE_SIZE, LIGHT_ICON_TEXTURE_SIZE,
                LIGHT_ICON_TEXTURE_SIZE, LIGHT_ICON_TEXTURE_SIZE);
        if (light.getType() == VttLightType.SPOT) {
            double radians = Math.toRadians(light.getDirectionDegrees());
            Vec2d start = new Vec2d(x + Math.cos(radians) * 7.0,
                    y + Math.sin(radians) * 7.0);
            Vec2d end = new Vec2d(x + Math.cos(radians) * 14.0,
                    y + Math.sin(radians) * 14.0);
            renderLine(context, start, end, YELLOW);
        }
    }

    private void renderSelection(VRenderContext context, VttLight light) {
        Vec2d origin = new Vec2d(light.getX(), light.getY());
        var segments = geometry.build(sceneSupplier.get());
        double direction = light.getType() == VttLightType.SPOT
                ? light.getDirectionDegrees() : 0.0;
        if (light.getType() == VttLightType.SPOT) {
            renderPolygon(context, raycaster.buildVisibilityCone(
                    origin, light.getOuterRadius(), segments,
                    Math.toRadians(direction), Math.toRadians(light.getConeAngleDegrees()), 256),
                    0xAA66CCFF);
            renderPolygon(context, raycaster.buildVisibilityCone(
                    origin, light.getInnerRadius(), segments,
                    Math.toRadians(direction), Math.toRadians(light.getConeAngleDegrees()), 256),
                    0xAA2299BB);
            renderPolygon(context, raycaster.buildVisibilityCone(
                    origin, light.getOuterRadius(), segments,
                    Math.toRadians(direction),
                    Math.toRadians(light.getInnerConeAngleDegrees()), 256),
                    0xAAFFD84A);
            Vec2d directionEnd = pointAt(
                    origin, light.getOuterRadius() + DIRECTION_HANDLE_OFFSET, direction);
            renderLine(context, context.renderState().worldToScreen(origin),
                    context.renderState().worldToScreen(directionEnd), 0xAAFFD84A);
            renderHandle(context, directionEnd);
            renderHandle(context, pointAt(
                    origin, light.getOuterRadius() + CONE_HANDLE_OFFSET,
                    direction + light.getConeAngleDegrees() / 2.0));
            renderHandle(context, pointAt(
                    origin, light.getOuterRadius() + INNER_CONE_HANDLE_OFFSET,
                    direction + light.getInnerConeAngleDegrees() / 2.0));
        } else {
            renderPolygon(context, raycaster.buildVisibilityPolygon(
                    origin, light.getOuterRadius(), segments), 0xAA66CCFF);
            renderPolygon(context, raycaster.buildVisibilityPolygon(
                    origin, light.getInnerRadius(), segments), 0xAA2299BB);
        }
        renderHandle(context, pointAt(origin, light.getInnerRadius(), direction));
        renderHandle(context, pointAt(origin, light.getOuterRadius(), direction));
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
        int height = popupHeight();
        context.graphics().fill(popupX, popupY, popupX + width, popupY + height, PANEL);
        border(context, popupX, popupY, width, height, CYAN);
        if (popup == Popup.CREATE) {
            popupRow(context, font, 0, "Create Point Light", true);
            popupRow(context, font, 1, "Create Spot Light", true);
            popupRow(context, font, 2, "Cancel", true);
            return;
        }
        VttLight light = selectedLight();
        boolean spot = light != null && light.getType() == VttLightType.SPOT;
        context.graphics().drawString(font, spot ? "Spot Light" : "Point Light",
                popupX + 7, popupY + 7, TEXT, false);
        renderField(context, font, Field.OUTER, "Outer Radius", 25);
        renderField(context, font, Field.INNER, "Inner Radius", 47);
        if (spot) {
            renderField(context, font, Field.DIRECTION, "Direction", 69);
            renderField(context, font, Field.OUTER_CONE, "Outer Cone", 91);
            renderField(context, font, Field.INNER_CONE, "Inner Cone", 113);
        }
        renderField(context, font, Field.COLOR, "Color", fieldOffset(Field.COLOR));
        renderPickerPreview(context);
        int paletteY = paletteY();
        for (int i = 0; i < COLORS.length; i++) {
            int x = popupX + 7 + i * 16;
            renderColorSwatch(context, x, paletteY, COLORS[i]);
        }
        double intensity = light == null ? VttLight.DEFAULT_INTENSITY : light.getIntensity();
        context.graphics().drawString(font,
                String.format(Locale.ROOT, "Intensity  %.2fx", intensity),
                popupX + 7, intensityLabelY(), TEXT, false);
        int trackX = popupX + 7;
        int trackY = intensityTrackY();
        int trackWidth = 140;
        context.graphics().fill(trackX, trackY, trackX + trackWidth, trackY + 5, 0xFF55555B);
        double progress = (intensity - VttLight.MIN_INTENSITY)
                / (VttLight.MAX_INTENSITY - VttLight.MIN_INTENSITY);
        int knobX = trackX + (int) Math.round(progress * trackWidth);
        context.graphics().fill(knobX - 2, trackY - 3, knobX + 3, trackY + 8, CYAN);
        popupRow(context, font, duplicateRow(), "Duplicate", true);
        popupRow(context, font, deleteRow(), "Delete", true);
        VttLight selected = selectedLight();
        popupRow(context, font, attachRow(), selected != null && selected.isAttached()
                ? "Attached to  >" : "Attach to  >", true);
        popupRow(context, font, detachRow(), "Detach",
                selected != null && selected.isAttached());
        if (attachmentTargetsOpen) renderAttachmentTargets(context, font);
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
                if (row == 1) createSpotLight();
                if (row == 2) closePopup();
            }
            return true;
        }
        if (attachmentTargetsOpen && clickAttachmentTarget(mouseX, mouseY)) return true;
        for (Field field : Field.values()) {
            if (!fieldVisible(field)) continue;
            int y = popupY + fieldOffset(field);
            if (mouseX >= popupX + 79 && mouseX <= popupX + 147
                    && mouseY >= y && mouseY <= y + 18) {
                focusedField = field;
                fieldBuffer = valueOf(field);
                replaceFieldOnType = true;
                return true;
            }
        }
        int colorOffset = fieldOffset(Field.COLOR);
        if (mouseX >= popupX + 61 && mouseX <= popupX + 75
                && mouseY >= popupY + colorOffset + 3
                && mouseY <= popupY + colorOffset + 17) {
            VttLight light = selectedLight();
            if (light != null) {
                int initial = light.isTintEnabled() ? light.getColorRgb() : 0xFFFFFF;
                colorPicker.open(initial, rgb -> {
                    light.setColorRgb(rgb);
                    light.setTintEnabled(true);
                    saveAction.run();
                });
            }
            return true;
        }
        int paletteY = paletteY();
        if (mouseX >= popupX + 7 && mouseX <= popupX + 151
                && mouseY >= paletteY && mouseY <= paletteY + 13) {
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
                && mouseY >= intensityTrackY() - 7
                && mouseY <= intensityTrackY() + 11) {
            draggingIntensity = true;
            updateIntensity(mouseX);
            return true;
        }
        if (mouseX >= popupX && mouseX <= popupX + 154) {
            int row = (int) ((mouseY - popupY - 5) / 19);
            if (row == attachRow()) attachmentTargetsOpen = !attachmentTargetsOpen;
            else if (row == detachRow()) detachSelectedLight();
            else if (row == duplicateRow()) duplicateSelected();
            else if (row == deleteRow()) deleteSelected();
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
        copy.setDirectionDegrees(source.getDirectionDegrees());
        copy.setConeAngleDegrees(source.getConeAngleDegrees());
        copy.setInnerConeAngleDegrees(source.getInnerConeAngleDegrees());
        copy.setTintEnabled(source.isTintEnabled());
        copy.setEnabled(source.isEnabled());
        copy.setAttachedToObjectId(source.getAttachedToObjectId());
        copy.setAttachmentOffsetX(source.getAttachmentOffsetX());
        copy.setAttachmentOffsetY(source.getAttachmentOffsetY());
        copy.setAttachmentDirectionOffsetDegrees(
                source.getAttachmentDirectionOffsetDegrees());
        scene.addLight(copy);
        selectedId = copy.getId();
        AttachmentBindingService.recaptureLight(scene, lastCanvasScene, selectedId);
        closePopup();
        saveAction.run();
        return true;
    }

    private void createPointLight() {
        createLight(VttLightType.POINT);
    }

    private void createSpotLight() {
        createLight(VttLightType.SPOT);
    }

    private void createLight(VttLightType type) {
        VttScene scene = sceneSupplier.get();
        if (scene == null || creationWorld == null) return;
        VttLight light = new VttLight(nextId(scene), creationWorld.x(), creationWorld.y());
        light.setType(type);
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
                var color = HexColorFormat.parse(fieldBuffer);
                if (color.isPresent()) {
                    light.setColorRgb(color.getAsInt());
                    light.setTintEnabled(true);
                }
            } else {
                double value = Double.parseDouble(fieldBuffer);
                if (focusedField == Field.OUTER) {
                    light.setOuterRadius(Math.max(value, light.getInnerRadius()));
                } else if (focusedField == Field.INNER) {
                    light.setInnerRadius(Math.min(value, light.getOuterRadius()));
                } else if (focusedField == Field.DIRECTION) {
                    light.setDirectionDegrees(value);
                } else if (focusedField == Field.OUTER_CONE) {
                    light.setConeAngleDegrees(value);
                } else if (focusedField == Field.INNER_CONE) {
                    light.setInnerConeAngleDegrees(value);
                }
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
            case DIRECTION -> String.format(Locale.ROOT, "%.0f", light.getDirectionDegrees());
            case OUTER_CONE -> String.format(Locale.ROOT, "%.0f", light.getConeAngleDegrees());
            case INNER_CONE -> String.format(Locale.ROOT, "%.0f",
                    light.getInnerConeAngleDegrees());
            case COLOR -> light.isTintEnabled()
                    ? HexColorFormat.format(light.getColorRgb()) : "None";
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

    private void renderPickerPreview(VRenderContext context) {
        VttLight light = selectedLight();
        if (light == null) return;
        int left = popupX + 62;
        int top = popupY + fieldOffset(Field.COLOR) + 4;
        int color = light.isTintEnabled() ? 0xFF000000 | light.getColorRgb() : 0xFFAAAAAA;
        context.graphics().fill(left, top, left + 12, top + 12, color);
        border(context, left, top, 12, 12, CYAN);
    }

    private void renderAttachmentTargets(VRenderContext context, Font font) {
        List<CanvasObject> targets = attachmentTargets();
        int width = 154;
        int height = Math.max(24, 10 + targets.size() * 18);
        int x = attachmentPopupX(width);
        context.graphics().fill(x, popupY, x + width, popupY + height, PANEL);
        border(context, x, popupY, width, height, CYAN);
        if (targets.isEmpty()) {
            context.graphics().drawString(font, "No attachments in scene",
                    x + 7, popupY + 8, MUTED, false);
            return;
        }
        VttLight light = selectedLight();
        for (int i = 0; i < targets.size(); i++) {
            CanvasObject target = targets.get(i);
            boolean current = light != null
                    && target.id().equals(light.getAttachedToObjectId());
            String name = target.displayName();
            if (name.length() > 19) name = name.substring(0, 16) + "...";
            context.graphics().drawString(font,
                    name + (current ? "  Attached" : ""),
                    x + 7, popupY + 7 + i * 18, current ? CYAN : TEXT, false);
        }
    }

    private boolean clickAttachmentTarget(double mouseX, double mouseY) {
        List<CanvasObject> targets = attachmentTargets();
        int width = 154;
        int height = Math.max(24, 10 + targets.size() * 18);
        int x = attachmentPopupX(width);
        if (mouseX < x || mouseX > x + width
                || mouseY < popupY || mouseY > popupY + height) return false;
        int index = (int) ((mouseY - popupY - 5) / 18);
        if (index >= 0 && index < targets.size()) {
            VttLight light = selectedLight();
            if (light != null && AttachmentBindingService.bindLight(
                    sceneSupplier.get(), lastCanvasScene, light.getId(),
                    targets.get(index).id())) {
                saveAction.run();
            }
        }
        return true;
    }

    private void detachSelectedLight() {
        VttLight light = selectedLight();
        if (light != null && AttachmentBindingService.detachLight(
                sceneSupplier.get(), light.getId())) {
            attachmentTargetsOpen = false;
            saveAction.run();
        }
    }

    private List<CanvasObject> attachmentTargets() {
        if (lastCanvasScene == null) return List.of();
        return lastCanvasScene.getObjects().stream()
                .filter(CanvasObject::hasSourceAttachmentDefinition)
                .toList();
    }

    private CanvasObject attachmentTargetAt(CanvasScene canvasScene, Vec2d worldPosition) {
        if (canvasScene == null) return null;
        List<CanvasObject> objects = canvasScene.getObjects();
        for (int index = objects.size() - 1; index >= 0; index--) {
            CanvasObject object = objects.get(index);
            if (object.hasSourceAttachmentDefinition() && object.visible()
                    && object.containsWorldPoint(worldPosition)) {
                return object;
            }
        }
        return null;
    }

    private void renderAttachmentDropTarget(VRenderContext context, CanvasObject attachment) {
        Vec2d center = context.renderState().worldToScreen(attachment.transform().position());
        int x = (int) Math.round(center.x());
        int y = (int) Math.round(center.y());
        context.graphics().fill(x - 6, y - 6, x + 7, y + 7, 0xCC00FFFF);
        context.graphics().fill(x - 3, y - 3, x + 4, y + 4, 0xFF202028);
    }

    private int attachmentPopupX(int width) {
        int right = popupX + 157;
        return right + width <= screenWidth() - 4 ? right : popupX - width - 3;
    }

    private int screenWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    private int screenHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    private VttLight lightAt(ToolContext context, double mouseX, double mouseY) {
        VttScene scene = sceneSupplier.get();
        if (scene == null) return null;
        List<VttLight> lights = scene.getLights();
        for (int i = lights.size() - 1; i >= 0; i--) {
            VttLight light = lights.get(i);
            Vec2d p = context.renderState().worldToScreen(new Vec2d(light.getX(), light.getY()));
            if (Math.abs(mouseX - p.x()) <= 8 && Math.abs(mouseY - p.y()) <= 8) return light;
        }
        return null;
    }

    private LightHandle handleAt(
            ToolContext context, VttLight light, double mouseX, double mouseY
    ) {
        Vec2d origin = new Vec2d(light.getX(), light.getY());
        double direction = light.getType() == VttLightType.SPOT
                ? light.getDirectionDegrees() : 0.0;
        Vec2d inner = context.renderState().worldToScreen(
                pointAt(origin, light.getInnerRadius(), direction));
        Vec2d outer = context.renderState().worldToScreen(
                pointAt(origin, light.getOuterRadius(), direction));
        if (Math.hypot(mouseX - inner.x(), mouseY - inner.y()) <= 9) return LightHandle.INNER;
        if (Math.hypot(mouseX - outer.x(), mouseY - outer.y()) <= 9) return LightHandle.OUTER;
        if (light.getType() == VttLightType.SPOT) {
            Vec2d directionHandle = context.renderState().worldToScreen(
                    pointAt(origin, light.getOuterRadius() + DIRECTION_HANDLE_OFFSET, direction));
            if (Math.hypot(mouseX - directionHandle.x(), mouseY - directionHandle.y()) <= 9) {
                return LightHandle.DIRECTION;
            }
            Vec2d coneHandle = context.renderState().worldToScreen(pointAt(
                    origin, light.getOuterRadius() + CONE_HANDLE_OFFSET,
                    direction + light.getConeAngleDegrees() / 2.0));
            if (Math.hypot(mouseX - coneHandle.x(), mouseY - coneHandle.y()) <= 9) {
                return LightHandle.OUTER_CONE;
            }
            Vec2d innerConeHandle = context.renderState().worldToScreen(pointAt(
                    origin, light.getOuterRadius() + INNER_CONE_HANDLE_OFFSET,
                    direction + light.getInnerConeAngleDegrees() / 2.0));
            if (Math.hypot(mouseX - innerConeHandle.x(), mouseY - innerConeHandle.y()) <= 9) {
                return LightHandle.INNER_CONE;
            }
        }
        return null;
    }

    private Vec2d pointAt(Vec2d origin, double distance, double angleDegrees) {
        double radians = Math.toRadians(angleDegrees);
        return origin.add(new Vec2d(Math.cos(radians) * distance,
                Math.sin(radians) * distance));
    }

    private double shortestAngleDegrees(double degrees) {
        return ((degrees + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
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
        popupY = Math.max(4, (int) Math.round(mouseY) - popupHeight());
        focusedField = null;
        draggingIntensity = false;
        attachmentTargetsOpen = false;
    }

    private void updateIntensity(double mouseX) {
        VttLight light = selectedLight();
        if (light == null) return;
        double progress = Math.max(0.0, Math.min(1.0,
                (mouseX - (popupX + 7.0)) / 140.0));
        light.setIntensity(VttLight.MIN_INTENSITY
                + progress * (VttLight.MAX_INTENSITY - VttLight.MIN_INTENSITY));
    }

    private boolean isSpot() {
        VttLight light = selectedLight();
        return light != null && light.getType() == VttLightType.SPOT;
    }

    private boolean fieldVisible(Field field) {
        return field != Field.DIRECTION
                && field != Field.OUTER_CONE
                && field != Field.INNER_CONE || isSpot();
    }

    private int fieldOffset(Field field) {
        return switch (field) {
            case OUTER -> 25;
            case INNER -> 47;
            case DIRECTION -> 69;
            case OUTER_CONE -> 91;
            case INNER_CONE -> 113;
            case COLOR -> isSpot() ? 135 : 69;
        };
    }

    private int paletteY() { return popupY + (isSpot() ? 157 : 91); }
    private int intensityLabelY() { return popupY + (isSpot() ? 177 : 111); }
    private int intensityTrackY() { return popupY + (isSpot() ? 192 : 126); }
    private int attachRow() { return isSpot() ? 11 : 8; }
    private int detachRow() { return isSpot() ? 12 : 9; }
    private int duplicateRow() { return isSpot() ? 13 : 10; }
    private int deleteRow() { return isSpot() ? 14 : 11; }
    private int popupHeight() {
        if (popup == Popup.CREATE) return 64;
        return isSpot() ? 299 : 236;
    }

    private void closePopup() {
        popup = Popup.NONE;
        creationWorld = null;
        focusedField = null;
        fieldBuffer = "";
        replaceFieldOnType = false;
        draggingIntensity = false;
        attachmentTargetsOpen = false;
    }

    private void border(VRenderContext context, int x, int y, int w, int h, int color) {
        context.graphics().hLine(x, x + w, y, color);
        context.graphics().hLine(x, x + w, y + h, color);
        context.graphics().vLine(x, y, y + h, color);
        context.graphics().vLine(x + w, y, y + h, color);
    }

    private enum Popup { NONE, CREATE, PROPERTIES }
    private enum LightHandle { INNER, OUTER, DIRECTION, OUTER_CONE, INNER_CONE }
    private enum Field { OUTER, INNER, DIRECTION, OUTER_CONE, INNER_CONE, COLOR }
}
