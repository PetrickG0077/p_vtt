package com.petrick.vtt.editor.tool;

import com.petrick.vtt.core.math.Rectd;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.platform.render.VRenderContext;
import org.lwjgl.glfw.GLFW;

/**
 * Ferramenta de seleção.
 *
 * - Clique simples seleciona um objeto.
 * - Ctrl + clique adiciona/remove objetos da seleção.
 * - Shift + arrastar cria uma caixa de seleção.
 * - Ctrl + Shift + arrastar adiciona objetos à seleção atual.
 * - Arrastar objeto selecionado move todos os objetos selecionados.
 */
public final class SelectTool implements Tool {

    public static final String ID = "select";

    private static final int LEFT_MOUSE_BUTTON = 0;

    private static final int SELECTION_FILL_COLOR = 0x3355AAFF;

    private static final int SELECTION_BORDER_COLOR = 0xCC3399FF;

    private boolean selecting;

    private boolean additiveSelection;

    private Vec2d selectionStart;

    private Vec2d selectionEnd;

    private boolean draggingSelection;

    private Vec2d lastDragWorldPosition;

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
        if (button != LEFT_MOUSE_BUTTON) {
            return false;
        }

        Vec2d screenPosition = new Vec2d(mouseX, mouseY);
        Vec2d worldPosition = context.renderState().screenToWorld(screenPosition);

        if (isShiftDown(modifiers)) {
            this.selecting = true;
            this.additiveSelection = isControlDown(modifiers);
            this.selectionStart = screenPosition;
            this.selectionEnd = screenPosition;
            return true;
        }

        CanvasObject clickedObject = context.selectionManager()
                .findTopmostObjectAtPoint(context.scene(), worldPosition);

        if (clickedObject == null) {
            if (!isControlDown(modifiers)) {
                context.selectionManager().clearSelection();
            }

            return true;
        }

        if (isControlDown(modifiers)) {
            context.selectionManager().toggle(clickedObject.id());
        } else if (!context.selectionManager().isSelected(clickedObject.id())) {
            context.selectionManager().selectOnly(clickedObject.id());
        }

        this.draggingSelection = context.selectionManager().isSelected(clickedObject.id());
        this.lastDragWorldPosition = worldPosition;

        return true;
    }

    @Override
    public boolean mouseReleased(
            ToolContext context,
            double mouseX,
            double mouseY,
            int button,
            int modifiers
    ) {
        if (button != LEFT_MOUSE_BUTTON) {
            return false;
        }

        if (selecting) {
            this.selectionEnd = new Vec2d(mouseX, mouseY);

            Rectd worldSelectionBounds = getWorldSelectionBounds(context);

            if (additiveSelection) {
                context.selectionManager().addObjectsInside(context.scene(), worldSelectionBounds);
            } else {
                context.selectionManager().selectObjectsInside(context.scene(), worldSelectionBounds);
            }

            this.selecting = false;
            this.additiveSelection = false;
            this.selectionStart = null;
            this.selectionEnd = null;

            return true;
        }

        if (draggingSelection) {
            this.draggingSelection = false;
            this.lastDragWorldPosition = null;
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
        if (button != LEFT_MOUSE_BUTTON) {
            return false;
        }

        if (selecting) {
            this.selectionEnd = new Vec2d(mouseX, mouseY);
            return true;
        }

        if (draggingSelection && lastDragWorldPosition != null) {
            Vec2d currentWorldPosition = context.renderState().screenToWorld(new Vec2d(mouseX, mouseY));
            Vec2d worldDelta = currentWorldPosition.subtract(lastDragWorldPosition);

            context.scene().moveObjects(
                    context.selectionManager().getSelectedObjectIds(),
                    worldDelta
            );

            this.lastDragWorldPosition = currentWorldPosition;
            return true;
        }

        return false;
    }

    @Override
    public void render(VRenderContext renderContext, ToolContext toolContext) {
        if (!selecting || selectionStart == null || selectionEnd == null) {
            return;
        }

        Rectd bounds = getScreenSelectionBounds();

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

    private Rectd getScreenSelectionBounds() {
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

    private Rectd getWorldSelectionBounds(ToolContext context) {
        Rectd screenBounds = getScreenSelectionBounds();

        Vec2d topLeft = context.renderState().screenToWorld(new Vec2d(
                screenBounds.left(),
                screenBounds.top()
        ));

        Vec2d bottomRight = context.renderState().screenToWorld(new Vec2d(
                screenBounds.right(),
                screenBounds.bottom()
        ));

        double x1 = Math.min(topLeft.x(), bottomRight.x());
        double y1 = Math.min(topLeft.y(), bottomRight.y());
        double x2 = Math.max(topLeft.x(), bottomRight.x());
        double y2 = Math.max(topLeft.y(), bottomRight.y());

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

    private static boolean isControlDown(int modifiers) {
        return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
    }
}