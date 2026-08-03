package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.editor.hud.EditorHudTheme;
import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.tabletop.VttAttachmentBinding;
import com.petrick.vtt.feature.tabletop.VttAttachmentAnchor;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Context menu for a placed attachment instance. */
public final class CanvasAttachmentContextMenuOverlay {
    private static final int WIDTH = 210;
    private static final int TARGET_WIDTH = 154;
    private static final int ROW_HEIGHT = 18;
    private static final int ROWS = 15;
    private static final int PANEL = 0xF018181E;
    private static final int TEXT = 0xFFF4F4F4;
    private static final int MUTED = 0xFF77777D;

    private String objectId;
    private int x;
    private int y;
    private boolean targetsOpen;
    private boolean anchorsOpen;
    private boolean constraintsOpen;
    private boolean confirmDeleteSubtree;

    public void open(String objectId, int mouseX, int mouseY,
                     int screenWidth, int screenHeight) {
        this.objectId = objectId;
        int height = height();
        this.x = Math.max(4, Math.min(screenWidth - WIDTH - 4, mouseX + 8));
        this.y = Math.max(4, Math.min(screenHeight - height - 4, mouseY - height - 8));
        this.targetsOpen = false;
        this.anchorsOpen = false;
        this.constraintsOpen = false;
        this.confirmDeleteSubtree = false;
    }

    public void close() {
        objectId = null;
        targetsOpen = false;
        anchorsOpen = false;
        constraintsOpen = false;
        confirmDeleteSubtree = false;
    }

    public boolean isOpen() { return objectId != null; }
    public String objectId() { return objectId; }

    public void render(VRenderContext context, Font font, VttAttachmentBinding binding,
                       List<CanvasObject> targets, int subtreeObjects, int subtreeLights,
                       int directChildren) {
        if (!isOpen()) return;
        panel(context, x, y, WIDTH, height());
        boolean bound = binding != null && binding.isBound();
        row(context, font, 0, bound ? "Attached to  >" : "Attach to  >", true);
        row(context, font, 1, "Follow position", bound);
        toggle(context, 1, bound && binding.isFollowPosition(), bound);
        row(context, font, 2, "Follow rotation", bound);
        toggle(context, 2, bound && binding.isFollowRotation(), bound);
        row(context, font, 3, "Follow scale", bound);
        toggle(context, 3, bound && binding.isFollowScale(), bound);
        row(context, font, 4, "Follow flip", bound);
        // flipOffset is the inverse of the UI concept: false means follow the parent flip.
        toggle(context, 4, bound && !binding.isFlipOffset(), bound);
        row(context, font, 5, "Capture offset", bound);
        boolean stateSpecific = bound && binding.getParentStateId() != null;
        row(context, font, 6, stateSpecific ? "Scope: Active state" : "Scope: Global", bound);
        row(context, font, 7, "Anchor: " + (bound
                ? binding.getAnchor().displayName() : "Custom") + "  >", bound);
        row(context, font, 8, "Transform constraints  >", bound);
        row(context, font, 9, "Detach", bound);
        row(context, font, 10, "Duplicate", true);
        row(context, font, 11, "Duplicate subtree (" + subtreeObjects + "/"
                + subtreeLights + ")", true);
        row(context, font, 12, "Detach children (" + directChildren + ")",
                directChildren > 0);
        row(context, font, 13, "Delete", true);
        row(context, font, 14, confirmDeleteSubtree
                ? "Confirm delete subtree" : "Delete subtree (" + subtreeObjects + "/"
                + subtreeLights + ")", true);

        if (targetsOpen) renderTargets(context, font, binding, targets);
        if (anchorsOpen) renderAnchors(context, font, binding);
        if (constraintsOpen) renderConstraints(context, font, binding);
    }

    public Interaction mouseClicked(double mouseX, double mouseY, int button,
                                    VttAttachmentBinding binding,
                                    List<CanvasObject> targets, int screenWidth) {
        if (!isOpen()) return Interaction.none();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return Interaction.handled();
        int targetX = targetX(screenWidth);
        if (constraintsOpen && inside(mouseX, mouseY, targetX, y, TARGET_WIDTH,
                constraintsHeight())) {
            return clickConstraints(mouseX, mouseY, targetX, binding);
        }
        if (anchorsOpen && inside(mouseX, mouseY, targetX, y, TARGET_WIDTH,
                anchorHeight())) {
            int index = (int) ((mouseY - y - 5) / ROW_HEIGHT);
            VttAttachmentAnchor[] anchors = VttAttachmentAnchor.values();
            return index >= 0 && index < anchors.length
                    ? new Interaction(Action.SET_ANCHOR, anchors[index].name(), true)
                    : Interaction.handled();
        }
        if (targetsOpen && inside(mouseX, mouseY, targetX, y, TARGET_WIDTH,
                targetHeight(targets))) {
            int index = (int) ((mouseY - y - 5) / ROW_HEIGHT);
            if (index >= 0 && index < targets.size()) {
                return new Interaction(Action.BIND, targets.get(index).id(), true);
            }
            return Interaction.handled();
        }
        if (inside(mouseX, mouseY, x, y, WIDTH, height())) {
            int row = (int) ((mouseY - y - 5) / ROW_HEIGHT);
            boolean bound = binding != null && binding.isBound();
            return switch (row) {
                case 0 -> {
                    targetsOpen = !targetsOpen;
                    anchorsOpen = false;
                    constraintsOpen = false;
                    yield Interaction.handled();
                }
                case 1 -> bound ? new Interaction(Action.TOGGLE_POSITION, null, true)
                        : Interaction.handled();
                case 2 -> bound ? new Interaction(Action.TOGGLE_ROTATION, null, true)
                        : Interaction.handled();
                case 3 -> bound ? new Interaction(Action.TOGGLE_SCALE, null, true)
                        : Interaction.handled();
                case 4 -> bound ? new Interaction(Action.TOGGLE_FLIP, null, true)
                        : Interaction.handled();
                case 5 -> bound ? new Interaction(Action.CAPTURE_OFFSET, null, true)
                        : Interaction.handled();
                case 6 -> bound ? new Interaction(Action.TOGGLE_STATE_SCOPE, null, true)
                        : Interaction.handled();
                case 7 -> {
                    if (!bound) yield Interaction.handled();
                    anchorsOpen = !anchorsOpen;
                    targetsOpen = false;
                    constraintsOpen = false;
                    yield Interaction.handled();
                }
                case 8 -> {
                    if (!bound) yield Interaction.handled();
                    constraintsOpen = !constraintsOpen;
                    targetsOpen = false;
                    anchorsOpen = false;
                    yield Interaction.handled();
                }
                case 9 -> bound ? new Interaction(Action.DETACH, null, true)
                        : Interaction.handled();
                case 10 -> new Interaction(Action.DUPLICATE, null, true);
                case 11 -> new Interaction(Action.DUPLICATE_SUBTREE, null, true);
                case 12 -> new Interaction(Action.DETACH_CHILDREN, null, true);
                case 13 -> new Interaction(Action.DELETE, null, true);
                case 14 -> {
                    if (!confirmDeleteSubtree) {
                        confirmDeleteSubtree = true;
                        targetsOpen = false;
                        anchorsOpen = false;
                        yield Interaction.handled();
                    }
                    yield new Interaction(Action.DELETE_SUBTREE, null, true);
                }
                default -> Interaction.handled();
            };
        }
        close();
        return Interaction.handled();
    }

    public boolean keyPressed(int keyCode) {
        if (!isOpen()) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            close();
            return true;
        }
        return false;
    }

    private void renderTargets(VRenderContext context, Font font,
                               VttAttachmentBinding binding, List<CanvasObject> targets) {
        int targetX = targetX(context.screenWidth());
        int height = targetHeight(targets);
        panel(context, targetX, y, TARGET_WIDTH, height);
        if (targets.isEmpty()) {
            context.graphics().drawString(font, "No tokens in scene",
                    targetX + 7, y + 8, MUTED, false);
            return;
        }
        for (int i = 0; i < targets.size(); i++) {
            CanvasObject target = targets.get(i);
            boolean current = binding != null
                    && target.id().equals(binding.getTargetObjectId());
            String label = trim(target.displayName(), current ? 17 : 21)
                    + (current ? "  Attached" : "");
            context.graphics().drawString(font, label, targetX + 7,
                    y + 7 + i * ROW_HEIGHT,
                    current ? EditorHudTheme.selection() : TEXT, false);
        }
    }

    private void renderAnchors(
            VRenderContext context, Font font, VttAttachmentBinding binding
    ) {
        int targetX = targetX(context.screenWidth());
        panel(context, targetX, y, TARGET_WIDTH, anchorHeight());
        VttAttachmentAnchor selected = binding == null
                ? VttAttachmentAnchor.CUSTOM : binding.getAnchor();
        VttAttachmentAnchor[] anchors = VttAttachmentAnchor.values();
        for (int index = 0; index < anchors.length; index++) {
            VttAttachmentAnchor anchor = anchors[index];
            context.graphics().drawString(font, anchor.displayName(), targetX + 7,
                    y + 7 + index * ROW_HEIGHT,
                    anchor == selected ? EditorHudTheme.selection() : TEXT, false);
        }
    }

    private void renderConstraints(VRenderContext context, Font font,
                                   VttAttachmentBinding binding) {
        int left = targetX(context.screenWidth());
        panel(context, left, y, TARGET_WIDTH, constraintsHeight());
        constraintValue(context, font, left, 0, "Offset X", binding.getOffsetX());
        constraintValue(context, font, left, 1, "Offset Y", binding.getOffsetY());
        constraintValue(context, font, left, 2, "Rotation", binding.getRotationOffsetDegrees());
        constraintValue(context, font, left, 3, "Min scale", binding.getMinimumScale());
        constraintValue(context, font, left, 4, "Max scale", binding.getMaximumScale());
        constraintToggle(context, font, left, 5, "Inherit scale X", binding.isInheritScaleX());
        constraintToggle(context, font, left, 6, "Inherit scale Y", binding.isInheritScaleY());
        constraintToggle(context, font, left, 7, "Lock offset X", binding.isLockOffsetX());
        constraintToggle(context, font, left, 8, "Lock offset Y", binding.isLockOffsetY());
        context.graphics().drawString(font, "Reset offset", left + 7,
                y + 7 + 9 * ROW_HEIGHT, TEXT, false);
    }

    private Interaction clickConstraints(double mouseX, double mouseY, int left,
                                         VttAttachmentBinding binding) {
        int row = (int) ((mouseY - y - 5) / ROW_HEIGHT);
        if (row >= 0 && row <= 4) {
            if (mouseX < left + TARGET_WIDTH - 38) return Interaction.handled();
            boolean decrease = mouseX < left + TARGET_WIDTH - 18;
            String direction = decrease ? "-1" : "1";
            Action action = switch (row) {
                case 0 -> Action.ADJUST_OFFSET_X;
                case 1 -> Action.ADJUST_OFFSET_Y;
                case 2 -> Action.ADJUST_ROTATION_OFFSET;
                case 3 -> Action.ADJUST_MIN_SCALE;
                default -> Action.ADJUST_MAX_SCALE;
            };
            return new Interaction(action, direction, true);
        }
        return switch (row) {
            case 5 -> new Interaction(Action.TOGGLE_INHERIT_SCALE_X, null, true);
            case 6 -> new Interaction(Action.TOGGLE_INHERIT_SCALE_Y, null, true);
            case 7 -> new Interaction(Action.TOGGLE_LOCK_OFFSET_X, null, true);
            case 8 -> new Interaction(Action.TOGGLE_LOCK_OFFSET_Y, null, true);
            case 9 -> new Interaction(Action.RESET_OFFSET, null, true);
            default -> Interaction.handled();
        };
    }

    private void constraintValue(VRenderContext context, Font font, int left,
                                 int row, String label, double value) {
        int top = y + 7 + row * ROW_HEIGHT;
        context.graphics().drawString(font, label, left + 7, top, TEXT, false);
        context.graphics().drawString(font, format(value),
                left + TARGET_WIDTH - 70, top, TEXT, false);
        context.graphics().drawString(font, "-", left + TARGET_WIDTH - 34,
                top, EditorHudTheme.selection(), false);
        context.graphics().drawString(font, "+", left + TARGET_WIDTH - 15,
                top, EditorHudTheme.selection(), false);
    }

    private void constraintToggle(VRenderContext context, Font font, int left,
                                  int row, String label, boolean value) {
        int top = y + 7 + row * ROW_HEIGHT;
        context.graphics().drawString(font, label, left + 7, top, TEXT, false);
        context.graphics().drawString(font, value ? "ON" : "OFF",
                left + TARGET_WIDTH - 28, top,
                value ? EditorHudTheme.selection() : MUTED, false);
    }

    private String format(double value) {
        return Math.abs(value - Math.rint(value)) < 0.001
                ? Long.toString(Math.round(value))
                : String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private int constraintsHeight() { return 10 + 10 * ROW_HEIGHT; }

    private int anchorHeight() {
        return 10 + VttAttachmentAnchor.values().length * ROW_HEIGHT;
    }

    private int targetX(int screenWidth) {
        int right = x + WIDTH + 3;
        return right + TARGET_WIDTH <= screenWidth - 4 ? right : x - TARGET_WIDTH - 3;
    }

    private int targetHeight(List<CanvasObject> targets) {
        return Math.max(24, 10 + targets.size() * ROW_HEIGHT);
    }

    private int height() { return 10 + ROWS * ROW_HEIGHT; }

    private void row(VRenderContext context, Font font, int index,
                     String label, boolean enabled) {
        context.graphics().drawString(font, label, x + 7,
                y + 7 + index * ROW_HEIGHT, enabled ? TEXT : MUTED, false);
    }

    private void toggle(VRenderContext context, int row, boolean enabled, boolean clickable) {
        int left = x + WIDTH - 25;
        int top = y + 6 + row * ROW_HEIGHT;
        int color = clickable ? (enabled ? 0xFF42B883 : 0xFF44444A) : 0xFF29292E;
        context.graphics().fill(left, top, left + 17, top + 9, color);
        context.graphics().fill(enabled ? left + 10 : left + 2, top + 2,
                enabled ? left + 15 : left + 7, top + 7,
                clickable ? 0xFFFFFFFF : MUTED);
    }

    private void panel(VRenderContext context, int x, int y, int width, int height) {
        context.graphics().fill(x, y, x + width, y + height, PANEL);
        int color = EditorHudTheme.outline();
        context.graphics().hLine(x, x + width, y, color);
        context.graphics().hLine(x, x + width, y + height, color);
        context.graphics().vLine(x, y, y + height, color);
        context.graphics().vLine(x + width, y, y + height, color);
    }

    private boolean inside(double mouseX, double mouseY,
                           int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private String trim(String value, int max) {
        if (value == null) return "Token";
        return value.length() <= max ? value : value.substring(0, Math.max(1, max - 3)) + "...";
    }

    public enum Action {
        NONE, BIND, TOGGLE_POSITION, TOGGLE_ROTATION, TOGGLE_SCALE, TOGGLE_FLIP, CAPTURE_OFFSET,
        TOGGLE_STATE_SCOPE, SET_ANCHOR,
        TOGGLE_INHERIT_SCALE_X, TOGGLE_INHERIT_SCALE_Y,
        TOGGLE_LOCK_OFFSET_X, TOGGLE_LOCK_OFFSET_Y,
        ADJUST_OFFSET_X, ADJUST_OFFSET_Y, ADJUST_ROTATION_OFFSET,
        ADJUST_MIN_SCALE, ADJUST_MAX_SCALE, RESET_OFFSET,
        DETACH, DUPLICATE, DUPLICATE_SUBTREE, DETACH_CHILDREN, DELETE, DELETE_SUBTREE
    }

    public record Interaction(Action action, String targetObjectId, boolean consumed) {
        public static Interaction none() { return new Interaction(Action.NONE, null, false); }
        public static Interaction handled() { return new Interaction(Action.NONE, null, true); }
    }
}
