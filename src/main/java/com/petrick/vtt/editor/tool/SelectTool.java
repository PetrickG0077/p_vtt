package com.petrick.vtt.editor.tool;

import com.petrick.vtt.core.math.Rectd;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.core.transform.Transform2D;
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
 * - Arrastar handles redimensiona o objeto.
 * - Shift + arrastar handle redimensiona proporcionalmente.
 */
public final class SelectTool implements Tool {

    public static final String ID = "select";

    private static final int LEFT_MOUSE_BUTTON = 0;

    private static final int HANDLE_HIT_SIZE = 10;

    private static final double MIN_OBJECT_SIZE = 10.0;

    private static final int SELECTION_FILL_COLOR = 0x3355AAFF;

    private static final int SELECTION_BORDER_COLOR = 0xCC3399FF;

    private boolean selecting;

    private boolean additiveSelection;

    private Vec2d selectionStart;

    private Vec2d selectionEnd;

    private boolean draggingSelection;

    private Vec2d lastDragWorldPosition;

    private boolean resizing;

    private String resizingObjectId;

    private SelectionHandle resizingHandle;

    private Rectd resizeOriginalBounds;

    private Vec2d resizeOriginalSize;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public EditorCursor getCursor(ToolContext context, double mouseX, double mouseY) {
        SelectionHandle handle = findHandleAt(context, mouseX, mouseY);

        if (handle != null) {
            return handle.getCursor();
        }

        return EditorCursor.DEFAULT;
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

        HandleHit handleHit = findHandleHitAt(context, mouseX, mouseY);

        if (handleHit != null) {
            beginResize(context, handleHit);
            return true;
        }

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

        if (resizing) {
            endResize();
            return true;
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

        if (resizing) {
            resizeObject(context, mouseX, mouseY, isShiftDown(modifiers));
            return true;
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

    private void beginResize(ToolContext context, HandleHit handleHit) {
        CanvasObject object = context.scene().findObjectById(handleHit.objectId());

        if (object == null) {
            return;
        }

        this.resizing = true;
        this.resizingObjectId = object.id();
        this.resizingHandle = handleHit.handle();
        this.resizeOriginalBounds = object.bounds();
        this.resizeOriginalSize = object.size();
    }

    private void endResize() {
        this.resizing = false;
        this.resizingObjectId = null;
        this.resizingHandle = null;
        this.resizeOriginalBounds = null;
        this.resizeOriginalSize = null;
    }

    private void resizeObject(ToolContext context, double mouseX, double mouseY, boolean proportional) {
        CanvasObject object = context.scene().findObjectById(resizingObjectId);

        if (object == null || resizeOriginalBounds == null || resizeOriginalSize == null) {
            return;
        }

        Vec2d mouseWorld = context.renderState().screenToWorld(new Vec2d(mouseX, mouseY));

        Vec2d anchor = getOppositeCorner(resizeOriginalBounds, resizingHandle);
        Rectd newBounds = createBoundsFromAnchor(anchor, mouseWorld, resizingHandle, proportional);

        double newWidth = Math.max(MIN_OBJECT_SIZE, newBounds.width());
        double newHeight = Math.max(MIN_OBJECT_SIZE, newBounds.height());

        Vec2d newCenter = new Vec2d(
                newBounds.x() + newWidth / 2.0,
                newBounds.y() + newHeight / 2.0
        );

        Vec2d newScale = new Vec2d(
                newWidth / resizeOriginalSize.x(),
                newHeight / resizeOriginalSize.y()
        );

        Transform2D newTransform = object.transform()
                .withPosition(newCenter)
                .withScale(newScale);

        CanvasObject resizedObject = new CanvasObject(
                object.id(),
                newTransform,
                object.size(),
                object.color()
        );

        context.scene().replaceObjectById(object.id(), resizedObject);
    }

    private Rectd createBoundsFromAnchor(
            Vec2d anchor,
            Vec2d current,
            SelectionHandle handle,
            boolean proportional
    ) {
        double dx = current.x() - anchor.x();
        double dy = current.y() - anchor.y();

        if (proportional) {
            double aspectRatio = resizeOriginalBounds.width() / resizeOriginalBounds.height();

            double absDx = Math.abs(dx);
            double absDy = Math.abs(dy);

            if (absDx / Math.max(absDy, 0.0001) > aspectRatio) {
                dx = Math.signum(dx) * absDy * aspectRatio;
            } else {
                dy = Math.signum(dy) * (absDx / aspectRatio);
            }
        }

        double minX = Math.min(anchor.x(), anchor.x() + dx);
        double minY = Math.min(anchor.y(), anchor.y() + dy);
        double maxX = Math.max(anchor.x(), anchor.x() + dx);
        double maxY = Math.max(anchor.y(), anchor.y() + dy);

        double width = Math.max(MIN_OBJECT_SIZE, maxX - minX);
        double height = Math.max(MIN_OBJECT_SIZE, maxY - minY);

        if (handle == SelectionHandle.TOP_LEFT || handle == SelectionHandle.BOTTOM_LEFT) {
            minX = maxX - width;
        } else {
            maxX = minX + width;
        }

        if (handle == SelectionHandle.TOP_LEFT || handle == SelectionHandle.TOP_RIGHT) {
            minY = maxY - height;
        } else {
            maxY = minY + height;
        }

        return new Rectd(
                minX,
                minY,
                maxX - minX,
                maxY - minY
        );
    }

    private Vec2d getOppositeCorner(Rectd bounds, SelectionHandle handle) {
        return switch (handle) {
            case TOP_LEFT -> new Vec2d(bounds.right(), bounds.bottom());
            case TOP_RIGHT -> new Vec2d(bounds.left(), bounds.bottom());
            case BOTTOM_LEFT -> new Vec2d(bounds.right(), bounds.top());
            case BOTTOM_RIGHT -> new Vec2d(bounds.left(), bounds.top());
        };
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

    private SelectionHandle findHandleAt(ToolContext context, double mouseX, double mouseY) {
        HandleHit hit = findHandleHitAt(context, mouseX, mouseY);
        return hit == null ? null : hit.handle();
    }

    private HandleHit findHandleHitAt(ToolContext context, double mouseX, double mouseY) {
        Vec2d mouse = new Vec2d(mouseX, mouseY);

        for (var object : context.scene().getObjects()) {
            if (!context.selectionManager().isSelected(object.id())) {
                continue;
            }

            Rectd bounds = object.bounds();

            Vec2d topLeft = context.renderState().worldToScreen(bounds.position());
            Vec2d topRight = context.renderState().worldToScreen(new Vec2d(bounds.right(), bounds.top()));
            Vec2d bottomLeft = context.renderState().worldToScreen(new Vec2d(bounds.left(), bounds.bottom()));
            Vec2d bottomRight = context.renderState().worldToScreen(new Vec2d(bounds.right(), bounds.bottom()));

            if (isPointInsideHandle(mouse, topLeft)) {
                return new HandleHit(object.id(), SelectionHandle.TOP_LEFT);
            }

            if (isPointInsideHandle(mouse, topRight)) {
                return new HandleHit(object.id(), SelectionHandle.TOP_RIGHT);
            }

            if (isPointInsideHandle(mouse, bottomLeft)) {
                return new HandleHit(object.id(), SelectionHandle.BOTTOM_LEFT);
            }

            if (isPointInsideHandle(mouse, bottomRight)) {
                return new HandleHit(object.id(), SelectionHandle.BOTTOM_RIGHT);
            }
        }

        return null;
    }

    private static boolean isPointInsideHandle(Vec2d point, Vec2d handleCenter) {
        double half = HANDLE_HIT_SIZE / 2.0;

        return point.x() >= handleCenter.x() - half
                && point.x() <= handleCenter.x() + half
                && point.y() >= handleCenter.y() - half
                && point.y() <= handleCenter.y() + half;
    }

    private static boolean isShiftDown(int modifiers) {
        return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
    }

    private static boolean isControlDown(int modifiers) {
        return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
    }

    private record HandleHit(
            String objectId,
            SelectionHandle handle
    ) {
    }
}