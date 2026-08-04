package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.editor.hud.EditorHudTheme;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;
import org.lwjgl.glfw.GLFW;

/** Context actions opened by right-clicking empty canvas space with the Select tool. */
public final class CanvasEmptyContextMenuOverlay {
    private static final int WIDTH = 116;
    private static final int CHILD_WIDTH = 148;
    private static final int ROW_HEIGHT = 18;
    private static final int PADDING = 4;

    private int x;
    private int y;
    private boolean open;
    private boolean createSubmenuOpen;

    public void open(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        x = Math.max(4, Math.min(screenWidth - WIDTH - 4, mouseX + 8));
        y = Math.max(4, Math.min(screenHeight - height() - 4, mouseY - 8));
        open = true;
        createSubmenuOpen = false;
    }

    public void close() {
        open = false;
        createSubmenuOpen = false;
    }

    public boolean isOpen() { return open; }

    public void render(VRenderContext context, Font font) {
        if (!open) return;
        panel(context, x, y, WIDTH, height());
        row(context, font, x, y, WIDTH, 0, "Create new  >", true);
        row(context, font, x, y, WIDTH, 1, "Paste", false);
        row(context, font, x, y, WIDTH, 2, "Cancel", true);
        if (createSubmenuOpen) {
            int childX = childX(context.screenWidth());
            panel(context, childX, y, CHILD_WIDTH, childHeight());
            row(context, font, childX, y, CHILD_WIDTH, 0, "Create new token", true);
            row(context, font, childX, y, CHILD_WIDTH, 1, "Create new attachment", true);
        }
    }

    public Interaction mouseClicked(
            double mouseX, double mouseY, int button, int screenWidth
    ) {
        if (!open) return Interaction.none();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return Interaction.handled();
        if (createSubmenuOpen) {
            int childX = childX(screenWidth);
            int childRow = rowAt(mouseX, mouseY, childX, y, CHILD_WIDTH, 2);
            if (childRow == 0) {
                close();
                return new Interaction(Action.CREATE_TOKEN, true);
            }
            if (childRow == 1) {
                close();
                return new Interaction(Action.CREATE_ATTACHMENT, true);
            }
        }
        int row = rowAt(mouseX, mouseY, x, y, WIDTH, 3);
        if (row == 0) {
            createSubmenuOpen = true;
            return Interaction.handled();
        }
        if (row == 1) return Interaction.handled(); // Clipboard support is the next step.
        if (row == 2) {
            close();
            return new Interaction(Action.CANCEL, true);
        }
        close();
        return Interaction.handled();
    }

    private int childX(int screenWidth) {
        int right = x + WIDTH + 2;
        return right + CHILD_WIDTH <= screenWidth - 4
                ? right : Math.max(4, x - CHILD_WIDTH - 2);
    }

    private int rowAt(
            double mouseX, double mouseY, int left, int top, int width, int rows
    ) {
        if (mouseX < left || mouseX > left + width
                || mouseY < top + PADDING || mouseY > top + PADDING + rows * ROW_HEIGHT) return -1;
        return (int) ((mouseY - top - PADDING) / ROW_HEIGHT);
    }

    private void row(
            VRenderContext context, Font font, int left, int top, int width,
            int index, String text, boolean enabled
    ) {
        int rowY = top + PADDING + index * ROW_HEIGHT;
        boolean hovered = context.mouseX() >= left && context.mouseX() <= left + width
                && context.mouseY() >= rowY && context.mouseY() <= rowY + ROW_HEIGHT;
        if (hovered && enabled) context.graphics().fill(
                left + 1, rowY, left + width - 1, rowY + ROW_HEIGHT,
                EditorHudTheme.selectionWithAlpha(220));
        context.graphics().drawString(font, text, left + 5, rowY + 5,
                enabled ? 0xFFFFFFFF : 0xFF77777D, false);
    }

    private void panel(VRenderContext context, int left, int top, int width, int height) {
        context.graphics().fill(left, top, left + width, top + height, 0xF018181E);
        int color = EditorHudTheme.outline();
        context.graphics().hLine(left, left + width, top, color);
        context.graphics().hLine(left, left + width, top + height, color);
        context.graphics().vLine(left, top, top + height, color);
        context.graphics().vLine(left + width, top, top + height, color);
    }

    private int height() { return PADDING * 2 + ROW_HEIGHT * 3; }
    private int childHeight() { return PADDING * 2 + ROW_HEIGHT * 2; }

    public enum Action { NONE, CREATE_TOKEN, CREATE_ATTACHMENT, CANCEL }

    public record Interaction(Action action, boolean consumed) {
        public static Interaction none() { return new Interaction(Action.NONE, false); }
        public static Interaction handled() { return new Interaction(Action.NONE, true); }
    }
}
