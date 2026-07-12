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
import java.util.function.Supplier;
import com.petrick.vtt.feature.tabletop.VttScene;

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

    private final Supplier<VttScene> tabletopSceneSupplier;

    private final Runnable saveTabletopAction;

    private boolean globalPanning;

    private Vec2d lastGlobalPanMousePosition;

    public InputController(
            Camera2D camera,
            CanvasScene scene,
            SelectionManager selectionManager,
            Supplier<VttScene> tabletopSceneSupplier,
            Runnable saveTabletopAction
    ) {
        this.camera = camera;
        this.scene = scene;
        this.selectionManager = selectionManager;
        this.tabletopSceneSupplier = tabletopSceneSupplier;
        this.saveTabletopAction = saveTabletopAction;
        this.toolController = new ToolController(tabletopSceneSupplier, saveTabletopAction);
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
        VttScene tabletopScene = tabletopSceneSupplier.get();
        boolean deletesVisionSource = tabletopScene != null
                && tabletopScene.getVisionSourceObjectIds().removeIf(
                selectionManager.getSelectedObjectIds()::contains);
        scene.removeObjects(selectionManager.getSelectedObjectIds());
        selectionManager.clearSelection();
        if (deletesVisionSource) {
            saveTabletopAction.run();
        }
    }

    public void selectHandTool() {
        toolController.selectHandTool();
    }

    public void selectSelectTool() {
        toolController.selectSelectTool();
    }

    public void selectWallTool() { toolController.selectWallTool(); }

    public void selectDoorTool() { toolController.selectDoorTool(); }

    public void selectFogTool() { toolController.selectFogTool(); }

    public boolean cancelWallDrawing() { return toolController.cancelWallDrawing(); }

    public boolean cancelDoorEditing() { return toolController.cancelDoorEditing(); }

    public boolean cancelFogDrawing() { return toolController.cancelFogDrawing(); }

    public boolean toggleSelectedFogVisibility() { return toolController.toggleSelectedFogVisibility(); }

    public boolean toggleFogEnabled() { return toolController.toggleFogEnabled(); }

    public boolean deleteSelectedFogArea() { return toolController.deleteSelectedFogArea(); }

    public boolean scaleSelectedFogArea(double factor) { return toolController.scaleSelectedFogArea(factor); }

    public boolean rotateSelectedFogArea(double degrees) { return toolController.rotateSelectedFogArea(degrees); }

    public boolean resetSelectedFogAreaTransform() { return toolController.resetSelectedFogAreaTransform(); }

    public boolean deleteSelectedWall() { return toolController.deleteSelectedWall(); }

    public boolean scaleSelectedWall(double factor) { return toolController.scaleSelectedWall(factor); }

    public boolean rotateSelectedWall(double degrees) { return toolController.rotateSelectedWall(degrees); }

    public boolean resetSelectedWallTransform() { return toolController.resetSelectedWallTransform(); }

    public boolean deleteSelectedDoor() { return toolController.deleteSelectedDoor(); }

    public boolean toggleSelectedDoorOpen() { return toolController.toggleSelectedDoorOpen(); }

    public boolean toggleSelectedDoorLocked() { return toolController.toggleSelectedDoorLocked(); }

    public boolean scaleSelectedDoor(double factor) { return toolController.scaleSelectedDoor(factor); }

    public boolean rotateSelectedDoor(double degrees) { return toolController.rotateSelectedDoor(degrees); }

    public boolean resetSelectedDoorTransform() { return toolController.resetSelectedDoorTransform(); }

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

    public void flipSelectedObjectsHorizontally() {
        scene.flipObjectsHorizontally(
                selectionManager.getSelectedObjectIds()
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
