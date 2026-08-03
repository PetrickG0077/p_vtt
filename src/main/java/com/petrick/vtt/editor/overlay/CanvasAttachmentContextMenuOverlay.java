package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.editor.hud.EditorHudTheme;
import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.tabletop.VttAttachmentBinding;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Context menu for a placed attachment instance. */
public final class CanvasAttachmentContextMenuOverlay {
    private static final int WIDTH = 132;
    private static final int TARGET_WIDTH = 154;
    private static final int ROW_HEIGHT = 18;
    private static final int ROWS = 9;
    private static final int PANEL = 0xF018181E;
    private static final int TEXT = 0xFFF4F4F4;
    private static final int MUTED = 0xFF77777D;

    private String objectId;
    private int x;
    private int y;
    private boolean targetsOpen;

    public void open(String objectId, int mouseX, int mouseY,
                     int screenWidth, int screenHeight) {
        this.objectId = objectId;
        int height = height();
        this.x = Math.max(4, Math.min(screenWidth - WIDTH - 4, mouseX + 8));
        this.y = Math.max(4, Math.min(screenHeight - height - 4, mouseY - height - 8));
        this.targetsOpen = false;
    }

    public void close() {
        objectId = null;
        targetsOpen = false;
    }

    public boolean isOpen() { return objectId != null; }
    public String objectId() { return objectId; }

    public void render(VRenderContext context, Font font, VttAttachmentBinding binding,
                       List<CanvasObject> targets) {
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
        row(context, font, 6, "Detach", bound);
        row(context, font, 7, "Duplicate", true);
        row(context, font, 8, "Delete", true);

        if (targetsOpen) renderTargets(context, font, binding, targets);
    }

    public Interaction mouseClicked(double mouseX, double mouseY, int button,
                                    VttAttachmentBinding binding,
                                    List<CanvasObject> targets, int screenWidth) {
        if (!isOpen()) return Interaction.none();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return Interaction.handled();
        int targetX = targetX(screenWidth);
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
                case 6 -> bound ? new Interaction(Action.DETACH, null, true)
                        : Interaction.handled();
                case 7 -> new Interaction(Action.DUPLICATE, null, true);
                case 8 -> new Interaction(Action.DELETE, null, true);
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
        DETACH, DUPLICATE, DELETE
    }

    public record Interaction(Action action, String targetObjectId, boolean consumed) {
        public static Interaction none() { return new Interaction(Action.NONE, null, false); }
        public static Interaction handled() { return new Interaction(Action.NONE, null, true); }
    }
}
