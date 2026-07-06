package com.petrick.vtt.editor.input;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.core.render.RenderState;
import com.petrick.vtt.editor.tool.ToolContext;
import com.petrick.vtt.editor.tool.ToolController;
import com.petrick.vtt.feature.camera.Camera2D;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.feature.selection.SelectionManager;
import com.petrick.vtt.platform.render.VRenderContext;
import java.util.Set;

/**
 * Controla os inputs principais do VTT.
 *
 * Responsabilidades:
 * - ações globais de câmera;
 * - delegar ações específicas para a ferramenta ativa.
 */
public final class InputController {

    private static final int MIDDLE_MOUSE_BUTTON = 2;

    private final Camera2D camera;

    private final CanvasScene scene;

    private final SelectionManager selectionManager;

    private final ToolController toolController;

    private boolean globalPanning;

    private Vec2d lastGlobalPanMousePosition;

    public InputController(
            Camera2D camera,
            CanvasScene scene,
            SelectionManager selectionManager
    ) {
        this.camera = camera;
        this.scene = scene;
        this.selectionManager = selectionManager;
        this.toolController = new ToolController();
    }

    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button,
            int modifiers,
            RenderState renderState
    ) {
        if (button == MIDDLE_MOUSE_BUTTON) {
            this.globalPanning = true;
            this.lastGlobalPanMousePosition = new Vec2d(mouseX, mouseY);
            return true;
        }

        ToolContext context = createToolContext(renderState);
        return toolController.mouseClicked(context, mouseX, mouseY, button, modifiers);
    }

    public boolean mouseReleased(
            double mouseX,
            double mouseY,
            int button,
            int modifiers,
            RenderState renderState
    ) {
        if (button == MIDDLE_MOUSE_BUTTON) {
            this.globalPanning = false;
            this.lastGlobalPanMousePosition = null;
            return true;
        }

        ToolContext context = createToolContext(renderState);
        return toolController.mouseReleased(context, mouseX, mouseY, button, modifiers);
    }

    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY,
            int modifiers,
            RenderState renderState
    ) {
        if (button == MIDDLE_MOUSE_BUTTON && globalPanning && lastGlobalPanMousePosition != null) {
            Vec2d currentMousePosition = new Vec2d(mouseX, mouseY);
            Vec2d delta = currentMousePosition.subtract(lastGlobalPanMousePosition);

            camera.moveByScreenDelta(delta);

            lastGlobalPanMousePosition = currentMousePosition;
            return true;
        }

        ToolContext context = createToolContext(renderState);
        return toolController.mouseDragged(context, mouseX, mouseY, button, dragX, dragY, modifiers);
    }

    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY,
            RenderState renderState
    ) {
        double zoomFactor = scrollY > 0 ? 1.1 : 0.9;

        camera.zoomAtScreenPoint(
                zoomFactor,
                new Vec2d(mouseX, mouseY),
                renderState.getViewportBounds()
        );

        return true;
    }

    public void renderToolOverlay(VRenderContext renderContext, RenderState renderState) {
        ToolContext context = createToolContext(renderState);
        toolController.render(renderContext, context);
    }

    public com.petrick.vtt.editor.tool.EditorCursor getCursor(
            double mouseX,
            double mouseY,
            RenderState renderState
    ) {
        ToolContext context = createToolContext(renderState);
        return toolController.getCursor(context, mouseX, mouseY);
    }

    public String getActiveToolId() {
        return toolController.getActiveToolId();
    }

    public void scaleSelectedObjectsUp() {
        scene.scaleObjects(
                selectionManager.getSelectedObjectIds(),
                1.1
        );
    }

    public void scaleSelectedObjectsDown() {
        scene.scaleObjects(
                selectionManager.getSelectedObjectIds(),
                0.9
        );
    }

    public void rotateSelectedObjectsLeft() {
        scene.rotateObjects(
                selectionManager.getSelectedObjectIds(),
                -15.0
        );
    }

    public void rotateSelectedObjectsRight() {
        scene.rotateObjects(
                selectionManager.getSelectedObjectIds(),
                15.0
        );
    }

    public void duplicateSelectedObjects() {
        Set<String> duplicatedIds = scene.duplicateObjects(
                selectionManager.getSelectedObjectIds(),
                new Vec2d(32.0, 32.0)
        );

        if (!duplicatedIds.isEmpty()) {
            selectionManager.clearSelection();

            for (String duplicatedId : duplicatedIds) {
                selectionManager.select(duplicatedId);
            }
        }
    }

    public void bringSelectedObjectsForward() {
        scene.bringObjectsForward(selectionManager.getSelectedObjectIds());
    }

    public void sendSelectedObjectsBackward() {
        scene.sendObjectsBackward(selectionManager.getSelectedObjectIds());
    }

    public void bringSelectedObjectsToFront() {
        scene.bringObjectsToFront(selectionManager.getSelectedObjectIds());
    }

    public void sendSelectedObjectsToBack() {
        scene.sendObjectsToBack(selectionManager.getSelectedObjectIds());
    }

    public void deleteSelectedObjects() {
        scene.removeObjects(selectionManager.getSelectedObjectIds());
        selectionManager.clearSelection();
    }

    public void selectHandTool() {
        toolController.selectHandTool();
    }

    public void selectSelectTool() {
        toolController.selectSelectTool();
    }

    public void toggleSelectedObjectsVisibility() {
        scene.toggleObjectsVisibility(selectionManager.getSelectedObjectIds());
    }

    public void resetSelectedObjectsScaleAndRotation() {
        scene.resetObjectsScaleAndRotation(selectionManager.getSelectedObjectIds());
    }

    public void setSelectedObjectsActiveState(String stateId) {
        scene.setObjectsActiveState(
                selectionManager.getSelectedObjectIds(),
                stateId
        );
    }

    private ToolContext createToolContext(RenderState renderState) {
        return new ToolContext(
                camera,
                renderState,
                scene,
                selectionManager
        );
    }
}