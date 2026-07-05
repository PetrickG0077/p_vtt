package com.petrick.vtt.editor.tool;

import com.petrick.vtt.core.math.Rectd;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.platform.render.VRenderContext;
import org.lwjgl.glfw.GLFW;

/**
 * Ferramenta de seleção.
 *
 * Por enquanto:
 * - Shift + arrastar com botão esquerdo cria uma caixa de seleção visual.
 *
 * Futuramente:
 * - Clique simples selecionará objetos.
 * - Ctrl + clique adicionará/removerá objetos da seleção.
 */
public final class SelectTool implements Tool {

    public static final String ID = "select";

    private static final int LEFT_MOUSE_BUTTON = 0;

    private static final int SELECTION_FILL_COLOR = 0x3355AAFF;

    private static final int SELECTION_BORDER_COLOR = 0xAA77CCFF;

    private boolean selecting;

    private Vec2d selectionStart;

    private Vec2d selectionEnd;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean mouseClicked(
            ToolContext context,
            double mouseX,
            double mouseY,
            int button,
            int modifiers
    ) {
        if (button == LEFT_MOUSE_BUTTON && isShiftDown(modifiers)) {
            this.selecting = true;
            this.selectionStart = new Vec2d(mouseX, mouseY);
            this.selectionEnd = new Vec2d(mouseX, mouseY);
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(
            ToolContext context,
            double mouseX,
            double mouseY,
            int button,
            int modifiers
    ) {
        if (button == LEFT_MOUSE_BUTTON && selecting) {
            this.selectionEnd = new Vec2d(mouseX, mouseY);

            Rectd selectionBounds = getSelectionBounds();

            // Futuramente:
            // 1. Converter essa caixa de screen space para world space.
            // 2. Perguntar ao SelectionManager quais objetos estão dentro.
            // 3. Atualizar a seleção ativa.

            this.selecting = false;
            this.selectionStart = null;
            this.selectionEnd = null;

            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(
            ToolContext context,
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY,
            int modifiers
    ) {
        if (button == LEFT_MOUSE_BUTTON && selecting) {
            this.selectionEnd = new Vec2d(mouseX, mouseY);
            return true;
        }

        return false;
    }

    @Override
    public void render(VRenderContext renderContext, ToolContext toolContext) {
        if (!selecting || selectionStart == null || selectionEnd == null) {
            return;
        }

        Rectd bounds = getSelectionBounds();

        int left = (int) Math.round(bounds.left());
        int top = (int) Math.round(bounds.top());
        int right = (int) Math.round(bounds.right());
        int bottom = (int) Math.round(bounds.bottom());

        renderContext.graphics().fill(
                left,
                top,
                right,
                bottom,
                SELECTION_FILL_COLOR
        );

        renderContext.graphics().hLine(left, right, top, SELECTION_BORDER_COLOR);
        renderContext.graphics().hLine(left, right, bottom, SELECTION_BORDER_COLOR);
        renderContext.graphics().vLine(left, top, bottom, SELECTION_BORDER_COLOR);
        renderContext.graphics().vLine(right, top, bottom, SELECTION_BORDER_COLOR);
    }

    private Rectd getSelectionBounds() {
        double x1 = Math.min(selectionStart.x(), selectionEnd.x());
        double y1 = Math.min(selectionStart.y(), selectionEnd.y());
        double x2 = Math.max(selectionStart.x(), selectionEnd.x());
        double y2 = Math.max(selectionStart.y(), selectionEnd.y());

        return new Rectd(
                x1,
                y1,
                x2 - x1,
                y2 - y1
        );
    }

    private static boolean isShiftDown(int modifiers) {
        return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
    }
}