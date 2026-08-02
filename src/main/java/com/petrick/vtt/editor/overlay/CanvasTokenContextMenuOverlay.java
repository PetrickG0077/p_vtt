package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasObjectState;
import com.petrick.vtt.feature.tabletop.VttSceneObject;
import com.petrick.vtt.editor.token.VttPlayerOption;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.List;

/** Context menu for a token instance placed on the canvas. */
public final class CanvasTokenContextMenuOverlay {
    private static final int MAIN_WIDTH = 112;
    private static final int STATES_WIDTH = 148;
    private static final int ROW_HEIGHT = 18;
    private static final int PANEL = 0xF018181E;
    private static final int TEXT = 0xFFF4F4F4;
    private static final int MUTED = 0xFF88888E;
    private static final int ACCENT = 0xFF55CCFF;
    private static final int[] COLORS = {
            0xFFFFFF, 0xD9D9D9, 0x888888, 0x333333,
            0xFF5555, 0xFFAA55, 0xFFFF55, 0x55FF55,
            0x55FFFF, 0x5599FF, 0x5555FF, 0xAA55FF,
            0xFF55FF, 0xFF88AA, 0xAA7744, 0x66AA88
    };

    private String objectId;
    private int x;
    private int y;
    private Submenu submenu = Submenu.NONE;
    private VisionField focusedField;
    private String fieldBuffer = "";
    private boolean replaceFieldOnType;
    private boolean masterMenu = true;

    public void open(
            String objectId, int mouseX, int mouseY, int screenWidth, int screenHeight,
            boolean masterMenu
    ) {
        this.objectId = objectId;
        this.masterMenu = masterMenu;
        this.x = Math.max(4, Math.min(screenWidth - MAIN_WIDTH - 4, mouseX + 8));
        this.y = Math.max(4, Math.min(screenHeight - mainHeight() - 4, mouseY - 8));
        submenu = Submenu.NONE;
        focusedField = null;
        fieldBuffer = "";
    }

    public void close() {
        objectId = null;
        submenu = Submenu.NONE;
        focusedField = null;
        fieldBuffer = "";
        replaceFieldOnType = false;
    }

    public boolean isOpen() { return objectId != null; }
    public String objectId() { return objectId; }

    public void render(
            VRenderContext context, Font font, CanvasObject token, VttSceneObject sceneObject,
            List<VttPlayerOption> players
    ) {
        if (!isOpen() || token == null || sceneObject == null) return;
        fillPanel(context, x, y, MAIN_WIDTH, mainHeight());
        if (masterMenu) {
            row(context, font, 0, "Edit", true);
            row(context, font, 1, "States  >", true);
            row(context, font, 2, "Color  >", true);
            row(context, font, 3, "Visible", true);
            toggle(context, x + MAIN_WIDTH - 24, y + 7 + 3 * ROW_HEIGHT, token.visible());
            row(context, font, 4, "Vision  >", true);
            row(context, font, 5, "Owner  >", true);
            row(context, font, 6, "Duplicate", true);
            row(context, font, 7, "Delete", true);
        } else {
            row(context, font, 0, "States  >", true);
            row(context, font, 1, "Color  >", true);
            row(context, font, 2, "Delete", true);
        }

        int childX = childX(context.screenWidth());
        if (submenu == Submenu.STATES) renderStates(context, font, token, childX);
        else if (submenu == Submenu.COLOR) renderColors(context, childX, sceneObject);
        else if (submenu == Submenu.VISION) renderVision(context, font, childX, sceneObject);
        else if (submenu == Submenu.OWNER) renderOwners(
                context, font, childX, sceneObject, players);
    }

    public Interaction mouseClicked(
            double mouseX, double mouseY, int button,
            CanvasObject token, VttSceneObject sceneObject, int screenWidth,
            List<VttPlayerOption> players
    ) {
        if (!isOpen() || token == null || sceneObject == null) return Interaction.none();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return Interaction.handled();
        int childX = childX(screenWidth);
        Interaction child = switch (submenu) {
            case STATES -> clickStates(mouseX, mouseY, token, childX);
            case COLOR -> clickColors(mouseX, mouseY, childX);
            case VISION -> clickVision(mouseX, mouseY, sceneObject, childX);
            case OWNER -> clickOwners(mouseX, mouseY, childX, players);
            case NONE -> Interaction.none();
        };
        if (child.action() != Action.NONE || child.consumed()) return child;

        if (inside(mouseX, mouseY, x, y, MAIN_WIDTH, mainHeight())) {
            int row = (int) ((mouseY - y - 5) / ROW_HEIGHT);
            if (!masterMenu) return switch (row) {
                case 0 -> openSubmenu(Submenu.STATES);
                case 1 -> openSubmenu(Submenu.COLOR);
                case 2 -> new Interaction(Action.DELETE, null, 0, true);
                default -> Interaction.handled();
            };
            return switch (row) {
                case 0 -> new Interaction(Action.EDIT, null, 0, true);
                case 1 -> openSubmenu(Submenu.STATES);
                case 2 -> openSubmenu(Submenu.COLOR);
                case 3 -> new Interaction(Action.TOGGLE_VISIBLE, null, 0, true);
                case 4 -> openSubmenu(Submenu.VISION);
                case 5 -> openSubmenu(Submenu.OWNER);
                case 6 -> new Interaction(Action.DUPLICATE, null, 0, true);
                case 7 -> new Interaction(Action.DELETE, null, 0, true);
                default -> Interaction.handled();
            };
        }
        close();
        return Interaction.handled();
    }

    public Interaction keyPressed(int keyCode, VttSceneObject sceneObject) {
        if (!isOpen()) return Interaction.none();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return Interaction.handled();
        }
        if (focusedField == null) return Interaction.none();
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (replaceFieldOnType) {
                fieldBuffer = "";
                replaceFieldOnType = false;
            } else if (!fieldBuffer.isEmpty()) {
                fieldBuffer = fieldBuffer.substring(0, fieldBuffer.length() - 1);
            }
            return Interaction.handled();
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            return commitField(sceneObject);
        }
        return Interaction.handled();
    }

    public boolean charTyped(char character) {
        if (!isOpen() || focusedField == null) return false;
        if (character < '0' || character > '9') return true;
        if (replaceFieldOnType) {
            fieldBuffer = "";
            replaceFieldOnType = false;
        }
        if (fieldBuffer.length() < 8) fieldBuffer += character;
        return true;
    }

    private Interaction clickStates(double mouseX, double mouseY, CanvasObject token, int childX) {
        int height = Math.max(24, 10 + token.states().size() * ROW_HEIGHT);
        if (!inside(mouseX, mouseY, childX, y, STATES_WIDTH, height)) return Interaction.none();
        int index = (int) ((mouseY - y - 5) / ROW_HEIGHT);
        if (index < 0 || index >= token.states().size()) return Interaction.handled();
        CanvasObjectState state = token.states().values().stream().skip(index).findFirst().orElse(null);
        return state == null ? Interaction.handled()
                : new Interaction(Action.SET_STATE, state.id(), 0, true);
    }

    private Interaction clickColors(double mouseX, double mouseY, int childX) {
        if (!inside(mouseX, mouseY, childX, y, 82, 82)) return Interaction.none();
        int column = (int) ((mouseX - childX - 5) / 18);
        int row = (int) ((mouseY - y - 5) / 18);
        int index = row * 4 + column;
        if (column < 0 || column >= 4 || row < 0 || row >= 4 || index >= COLORS.length) {
            return Interaction.handled();
        }
        return new Interaction(Action.SET_COLOR, null, COLORS[index], true);
    }

    private Interaction clickVision(
            double mouseX, double mouseY, VttSceneObject object, int childX
    ) {
        if (!inside(mouseX, mouseY, childX, y, 190, 112)) return Interaction.none();
        if (mouseY < y + 27) return new Interaction(Action.TOGGLE_VISION, null, 0, true);
        if (mouseY < y + 48) {
            return object.isVisionEnabled()
                    ? new Interaction(Action.TOGGLE_OWN_LIGHT, null, 0, true)
                    : Interaction.handled();
        }
        if (mouseX >= childX + 108 && mouseX <= childX + 182) {
            if (mouseY >= y + 55 && mouseY <= y + 75) {
                focus(VisionField.INNER, object.getVisionInnerRadius());
                return Interaction.handled();
            }
            if (mouseY >= y + 81 && mouseY <= y + 101) {
                double outer = object.getVisionOuterRadius() > 0.0
                        ? object.getVisionOuterRadius() : 512.0;
                focus(VisionField.OUTER, outer);
                return Interaction.handled();
            }
        }
        return Interaction.handled();
    }

    private Interaction clickOwners(
            double mouseX, double mouseY, int childX, List<VttPlayerOption> players
    ) {
        int count = 1 + (players == null ? 0 : players.size());
        int height = Math.max(24, 10 + count * ROW_HEIGHT);
        if (!inside(mouseX, mouseY, childX, y, 180, height)) return Interaction.none();
        int index = (int) ((mouseY - y - 5) / ROW_HEIGHT);
        if (index == 0) return new Interaction(Action.SET_OWNER, "", 0, true);
        if (players == null || index < 1 || index > players.size()) return Interaction.handled();
        return new Interaction(Action.SET_OWNER, players.get(index - 1).id(), 0, true);
    }

    private Interaction commitField(VttSceneObject object) {
        try {
            double value = Double.parseDouble(fieldBuffer);
            Action action = focusedField == VisionField.INNER
                    ? Action.SET_VISION_INNER : Action.SET_VISION_OUTER;
            focusedField = null;
            return new Interaction(action, null, (int) Math.round(value), true);
        } catch (NumberFormatException ignored) {
            fieldBuffer = focusedField == VisionField.INNER
                    ? format(object.getVisionInnerRadius())
                    : format(object.getVisionOuterRadius());
            return Interaction.handled();
        }
    }

    private void renderStates(VRenderContext context, Font font, CanvasObject token, int childX) {
        int height = Math.max(24, 10 + token.states().size() * ROW_HEIGHT);
        fillPanel(context, childX, y, STATES_WIDTH, height);
        int index = 0;
        for (CanvasObjectState state : token.states().values()) {
            boolean active = state.id().equals(token.activeStateId());
            context.graphics().drawString(font, (active ? "* " : "  ") + state.displayName(),
                    childX + 7, y + 7 + index++ * ROW_HEIGHT,
                    active ? ACCENT : TEXT, false);
        }
    }

    private void renderColors(VRenderContext context, int childX, VttSceneObject object) {
        fillPanel(context, childX, y, 82, 82);
        int selected = object.getState().getTintColorRgb();
        for (int i = 0; i < COLORS.length; i++) {
            int sx = childX + 5 + i % 4 * 18;
            int sy = y + 5 + i / 4 * 18;
            context.graphics().fill(sx, sy, sx + 14, sy + 14, 0xFF000000 | COLORS[i]);
            border(context, sx, sy, 14, 14, COLORS[i] == selected ? ACCENT : 0xFF888888);
        }
    }

    private void renderVision(
            VRenderContext context, Font font, int childX, VttSceneObject object
    ) {
        fillPanel(context, childX, y, 190, 112);
        context.graphics().drawString(font, "Vision Enabled", childX + 7, y + 8, TEXT, false);
        toggle(context, childX + 159, y + 7, object.isVisionEnabled());
        int ownColor = object.isVisionEnabled() ? TEXT : MUTED;
        context.graphics().drawString(font, "Own Light", childX + 7, y + 29, ownColor, false);
        toggle(context, childX + 159, y + 28,
                object.isVisionEnabled() && object.isVisionOwnLightEnabled());
        renderVisionField(context, font, childX, y + 55, "Inner Radius", VisionField.INNER,
                object.getVisionInnerRadius());
        double outer = object.getVisionOuterRadius() > 0.0
                ? object.getVisionOuterRadius() : 512.0;
        renderVisionField(context, font, childX, y + 81, "Outer Radius", VisionField.OUTER, outer);
    }

    private void renderOwners(
            VRenderContext context, Font font, int childX, VttSceneObject object,
            List<VttPlayerOption> players
    ) {
        List<VttPlayerOption> safePlayers = players == null ? List.of() : players;
        int height = Math.max(24, 10 + (safePlayers.size() + 1) * ROW_HEIGHT);
        fillPanel(context, childX, y, 180, height);
        boolean unowned = object.getOwnerId() == null || object.getOwnerId().isBlank();
        context.graphics().drawString(font, "Unassigned", childX + 7, y + 7,
                unowned ? ACCENT : TEXT, false);
        for (int index = 0; index < safePlayers.size(); index++) {
            VttPlayerOption player = safePlayers.get(index);
            boolean owner = player.id().equals(object.getOwnerId());
            int rowY = y + 7 + (index + 1) * ROW_HEIGHT;
            context.graphics().drawString(font, player.displayName(), childX + 7, rowY,
                    owner ? ACCENT : TEXT, false);
            if (owner) {
                String marker = "Owner";
                context.graphics().drawString(font, marker,
                        childX + 173 - font.width(marker), rowY, ACCENT, false);
            }
        }
    }

    private void renderVisionField(
            VRenderContext context, Font font, int childX, int fieldY,
            String label, VisionField field, double value
    ) {
        context.graphics().drawString(font, label, childX + 7, fieldY + 6, TEXT, false);
        int fieldX = childX + 108;
        context.graphics().fill(fieldX, fieldY, fieldX + 74, fieldY + 20, 0xFF101014);
        border(context, fieldX, fieldY, 74, 20, focusedField == field ? ACCENT : 0xFF66666C);
        String text = focusedField == field ? fieldBuffer : format(value);
        context.graphics().drawString(font, text, fieldX + 5, fieldY + 6, TEXT, false);
    }

    private void row(VRenderContext context, Font font, int row, String label, boolean enabled) {
        context.graphics().drawString(font, label, x + 7, y + 7 + row * ROW_HEIGHT,
                enabled ? TEXT : MUTED, false);
    }

    private void toggle(VRenderContext context, int tx, int ty, boolean enabled) {
        context.graphics().fill(tx, ty, tx + 17, ty + 10, enabled ? 0xFF247A57 : 0xFF44444A);
        int knobX = enabled ? tx + 9 : tx + 1;
        context.graphics().fill(knobX, ty + 1, knobX + 7, ty + 9, 0xFFFFFFFF);
    }

    private Interaction openSubmenu(Submenu submenu) {
        this.submenu = submenu;
        focusedField = null;
        return Interaction.handled();
    }

    private void focus(VisionField field, double value) {
        focusedField = field;
        fieldBuffer = format(value);
        replaceFieldOnType = true;
    }

    private int childX(int screenWidth) {
        int desired = x + MAIN_WIDTH + 4;
        int childWidth = switch (submenu) {
            case VISION -> 190;
            case COLOR -> 82;
            case STATES -> STATES_WIDTH;
            case OWNER -> 180;
            case NONE -> 0;
        };
        return desired + childWidth <= screenWidth - 4 ? desired : x - childWidth - 4;
    }

    private int mainHeight() { return 10 + ROW_HEIGHT * (masterMenu ? 8 : 3); }

    private void fillPanel(VRenderContext context, int px, int py, int width, int height) {
        context.graphics().fill(px, py, px + width, py + height, PANEL);
        border(context, px, py, width, height, ACCENT);
    }

    private void border(VRenderContext context, int px, int py, int width, int height, int color) {
        context.graphics().hLine(px, px + width, py, color);
        context.graphics().hLine(px, px + width, py + height, color);
        context.graphics().vLine(px, py, py + height, color);
        context.graphics().vLine(px + width, py, py + height, color);
    }

    private boolean inside(double mouseX, double mouseY, int px, int py, int width, int height) {
        return mouseX >= px && mouseX <= px + width && mouseY >= py && mouseY <= py + height;
    }

    private String format(double value) { return String.format(Locale.ROOT, "%.0f", value); }

    public enum Action {
        NONE, EDIT, SET_STATE, SET_COLOR, TOGGLE_VISIBLE, TOGGLE_VISION,
        TOGGLE_OWN_LIGHT, SET_VISION_INNER, SET_VISION_OUTER, SET_OWNER,
        DUPLICATE, DELETE
    }

    public record Interaction(Action action, String stringValue, int intValue, boolean consumed) {
        public static Interaction none() { return new Interaction(Action.NONE, null, 0, false); }
        public static Interaction handled() { return new Interaction(Action.NONE, null, 0, true); }
    }

    private enum Submenu { NONE, STATES, COLOR, VISION, OWNER }
    private enum VisionField { INNER, OUTER }
}
