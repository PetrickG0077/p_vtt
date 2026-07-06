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
 * - Arrastar handles redimensiona o objeto.
 * - Shift + arrastar handle redimensiona proporcionalmente.
 *
 * Agora os handles e o resize respeitam a rotação do objeto.
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

    private CanvasObject resizeOriginalObject;

    private Vec2d resizeAnchorWorld;

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
        this.resizeOriginalObject = object;
        this.resizeAnchorWorld = getOppositeCorner(object, resizingHandle);
    }

    private void endResize() {
        this.resizing = false;
        this.resizingObjectId = null;
        this.resizingHandle = null;
        this.resizeOriginalObject = null;
        this.resizeAnchorWorld = null;
    }

    private void resizeObject(ToolContext context, double mouseX, double mouseY, boolean proportional) {
        if (resizeOriginalObject == null || resizeAnchorWorld == null || resizingHandle == null) {
            return;
        }

        Vec2d mouseWorld = context.renderState().screenToWorld(new Vec2d(mouseX, mouseY));

        double rotation = resizeOriginalObject.transform().rotationDegrees();

        Vec2d localDelta = rotate(
                mouseWorld.subtract(resizeAnchorWorld),
                -rotation
        );

        double newWidth = Math.max(MIN_OBJECT_SIZE, Math.abs(localDelta.x()));
        double newHeight = Math.max(MIN_OBJECT_SIZE, Math.abs(localDelta.y()));

        if (proportional) {
            double aspectRatio = resizeOriginalObject.size().x() / resizeOriginalObject.size().y();

            if (newWidth / newHeight > aspectRatio) {
                newWidth = newHeight * aspectRatio;
            } else {
                newHeight = newWidth / aspectRatio;
            }
        }

        int signX = getHandleSignX(resizingHandle);
        int signY = getHandleSignY(resizingHandle);

        Vec2d centerOffsetLocal = new Vec2d(
                signX * newWidth / 2.0,
                signY * newHeight / 2.0
        );

        Vec2d newCenter = resizeAnchorWorld.add(
                rotate(centerOffsetLocal, rotation)
        );

        Vec2d newScale = new Vec2d(
                newWidth / resizeOriginalObject.size().x(),
                newHeight / resizeOriginalObject.size().y()
        );

        CanvasObject currentObject = context.scene().findObjectById(resizingObjectId);

        if (currentObject == null) {
            return;
        }

        CanvasObject resizedObject = new CanvasObject(
                currentObject.id(),
                currentObject.transform()
                        .withPosition(newCenter)
                        .withScale(newScale),
                currentObject.size(),
                currentObject.visual(),
                currentObject.visible()
        );

        context.scene().replaceObjectById(currentObject.id(), resizedObject);
    }

    private Vec2d getOppositeCorner(CanvasObject object, SelectionHandle handle) {
        return switch (handle) {
            case TOP_LEFT -> object.worldBottomRight();
            case TOP_RIGHT -> object.worldBottomLeft();
            case BOTTOM_LEFT -> object.worldTopRight();
            case BOTTOM_RIGHT -> object.worldTopLeft();
        };
    }

    private static int getHandleSignX(SelectionHandle handle) {
        return switch (handle) {
            case TOP_LEFT, BOTTOM_LEFT -> -1;
            case TOP_RIGHT, BOTTOM_RIGHT -> 1;
        };
    }

    private static int getHandleSignY(SelectionHandle handle) {
        return switch (handle) {
            case TOP_LEFT, TOP_RIGHT -> -1;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> 1;
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

        for (CanvasObject object : context.scene().getObjects()) {
            if (!context.selectionManager().isSelected(object.id())) {
                continue;
            }

            Vec2d topLeft = context.renderState().worldToScreen(object.worldTopLeft());
            Vec2d topRight = context.renderState().worldToScreen(object.worldTopRight());
            Vec2d bottomLeft = context.renderState().worldToScreen(object.worldBottomLeft());
            Vec2d bottomRight = context.renderState().worldToScreen(object.worldBottomRight());

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

    private static Vec2d rotate(Vec2d point, double degrees) {
        double radians = Math.toRadians(degrees);

        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        return new Vec2d(
                point.x() * cos - point.y() * sin,
                point.x() * sin + point.y() * cos
        );
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