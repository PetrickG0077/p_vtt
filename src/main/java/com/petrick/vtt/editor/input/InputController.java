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
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.core.session.VttRole;
import com.petrick.vtt.network.client.VttClientTokenTransformSync;

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

    private final Consumer<String> setBackgroundAction;

    private final EditorSceneHistory sceneHistory = new EditorSceneHistory();

    private boolean globalPanning;

    private Vec2d lastGlobalPanMousePosition;

    public InputController(
            Camera2D camera,
            CanvasScene scene,
            SelectionManager selectionManager,
            Supplier<VttScene> tabletopSceneSupplier,
            Runnable saveTabletopAction,
            Consumer<String> setBackgroundAction,
            Supplier<VttRole> roleSupplier,
            Supplier<String> playerIdSupplier,
            BooleanSupplier spectatorSupplier
    ) {
        this.camera = camera;
        this.scene = scene;
        this.selectionManager = selectionManager;
        this.tabletopSceneSupplier = tabletopSceneSupplier;
        this.saveTabletopAction = saveTabletopAction;
        this.setBackgroundAction = setBackgroundAction;
        this.toolController = new ToolController(tabletopSceneSupplier, saveTabletopAction,
                roleSupplier, playerIdSupplier, spectatorSupplier);
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

        if (button == 0) {
            sceneHistory.begin(activeSceneId(), scene, tabletopSceneSupplier.get());
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
        boolean handled = toolController.mouseReleased(
                context, mouseX, mouseY, button, modifiers);
        if (button == 0 && sceneHistory.end(
                activeSceneId(), scene, tabletopSceneSupplier.get())) {
            saveTabletopAction.run();
        }
        return handled;
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
        performTransform(() -> scene.scaleObjects(
                selectionManager.getSelectedObjectIds(),
                1.1
        ));
    }

    public void scaleSelectedObjectsDown() {
        performTransform(() -> scene.scaleObjects(
                selectionManager.getSelectedObjectIds(),
                0.9
        ));
    }

    public void rotateSelectedObjectsLeft() {
        performTransform(() -> scene.rotateObjects(
                selectionManager.getSelectedObjectIds(),
                -15.0
        ));
    }

    public void rotateSelectedObjectsRight() {
        performTransform(() -> scene.rotateObjects(
                selectionManager.getSelectedObjectIds(),
                15.0
        ));
    }

    public void duplicateSelectedObjects() {
        beginSceneChange();
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
        endSceneChange();
    }

    public void bringSelectedObjectsForward() {
        performTransform(() ->
                scene.bringObjectsForward(selectionManager.getSelectedObjectIds()));
    }

    public void sendSelectedObjectsBackward() {
        performTransform(() ->
                scene.sendObjectsBackward(selectionManager.getSelectedObjectIds()));
    }

    public void bringSelectedObjectsToFront() {
        performTransform(() ->
                scene.bringObjectsToFront(selectionManager.getSelectedObjectIds()));
    }

    public void sendSelectedObjectsToBack() {
        performTransform(() ->
                scene.sendObjectsToBack(selectionManager.getSelectedObjectIds()));
    }

    public void deleteSelectedObjects() {
        performTokenChange(() -> {
            VttScene tabletopScene = tabletopSceneSupplier.get();
            if (tabletopScene != null) {
                tabletopScene.getVisionSourceObjectIds().removeIf(
                        selectionManager.getSelectedObjectIds()::contains);
            }
            scene.removeObjects(selectionManager.getSelectedObjectIds());
            selectionManager.clearSelection();
        });
    }

    public void selectHandTool() {
        toolController.selectHandTool();
    }

    public void selectSelectTool() {
        toolController.selectSelectTool();
    }

    public boolean toggleCollisionBoxEditor(RenderState renderState) {
        return performSceneChange(() ->
                toolController.toggleCollisionBoxEditor(createToolContext(renderState)));
    }

    public boolean isEditingCollisionBox() {
        return toolController.isEditingCollisionBox();
    }

    public boolean closeCollisionBoxEditor() {
        return toolController.closeCollisionBoxEditor();
    }

    public void selectWallTool() { toolController.selectWallTool(); }

    public void selectDoorTool() { toolController.selectDoorTool(); }

    public void selectFogTool() { toolController.selectFogTool(); }

    public void selectMeasureTool() { toolController.selectMeasureTool(); }

    public boolean cancelWallDrawing() { return toolController.cancelWallDrawing(); }

    public boolean cancelDoorEditing() { return toolController.cancelDoorEditing(); }

    public boolean cancelFogDrawing() { return toolController.cancelFogDrawing(); }

    public boolean cancelMeasurement() { return toolController.cancelMeasurement(); }

    public boolean toggleSelectedFogVisibility() {
        return performSceneChange(toolController::toggleSelectedFogVisibility);
    }

    public boolean toggleFogEnabled() {
        return performSceneChange(toolController::toggleFogEnabled);
    }

    public boolean deleteSelectedFogArea() {
        return performSceneChange(toolController::deleteSelectedFogArea);
    }

    public boolean scaleSelectedFogArea(double factor) {
        return performSceneChange(() -> toolController.scaleSelectedFogArea(factor));
    }

    public boolean rotateSelectedFogArea(double degrees) {
        return performSceneChange(() -> toolController.rotateSelectedFogArea(degrees));
    }

    public boolean resetSelectedFogAreaTransform() {
        return performSceneChange(toolController::resetSelectedFogAreaTransform);
    }

    public boolean deleteSelectedWall() {
        return performSceneChange(toolController::deleteSelectedWall);
    }

    public boolean scaleSelectedWall(double factor) {
        return performSceneChange(() -> toolController.scaleSelectedWall(factor));
    }

    public boolean rotateSelectedWall(double degrees) {
        return performSceneChange(() -> toolController.rotateSelectedWall(degrees));
    }

    public boolean resetSelectedWallTransform() {
        return performSceneChange(toolController::resetSelectedWallTransform);
    }

    public boolean deleteSelectedDoor() {
        return performSceneChange(toolController::deleteSelectedDoor);
    }

    public boolean toggleSelectedDoorOpen() {
        return performSceneChange(toolController::toggleSelectedDoorOpen);
    }

    public boolean toggleSelectedDoorLocked() {
        return performSceneChange(toolController::toggleSelectedDoorLocked);
    }

    public boolean scaleSelectedDoor(double factor) {
        return performSceneChange(() -> toolController.scaleSelectedDoor(factor));
    }

    public boolean rotateSelectedDoor(double degrees) {
        return performSceneChange(() -> toolController.rotateSelectedDoor(degrees));
    }

    public boolean resetSelectedDoorTransform() {
        return performSceneChange(toolController::resetSelectedDoorTransform);
    }

    public void toggleSelectedObjectsVisibility() {
        performTokenChange(() ->
                scene.toggleObjectsVisibility(selectionManager.getSelectedObjectIds()));
    }

    public void resetSelectedObjectsScaleAndRotation() {
        performTransform(() ->
                scene.resetObjectsScaleAndRotation(selectionManager.getSelectedObjectIds()));
    }

    public void setSelectedObjectsActiveState(String stateId) {
        performTokenChange(() -> scene.setObjectsActiveState(
                selectionManager.getSelectedObjectIds(),
                stateId
        ));
    }

    public void flipSelectedObjectsHorizontally() {
        performTransform(() -> scene.flipObjectsHorizontally(
                selectionManager.getSelectedObjectIds()
        ));
    }

    public void renameObject(String objectId, String displayName) {
        performTokenChange(() -> scene.renameObject(objectId, displayName));
    }

    public void beginTokenLifecycleChange() {
        beginSceneChange();
    }

    public void endTokenLifecycleChange() {
        endSceneChange();
    }

    public void beginEditorAction() {
        beginSceneChange();
    }

    public void endEditorAction() {
        endSceneChange();
    }

    public boolean canUndoEditorAction() {
        return sceneHistory.canUndo(activeSceneId());
    }

    public boolean canRedoEditorAction() {
        return sceneHistory.canRedo(activeSceneId());
    }

    public String nextUndoDescription() {
        return sceneHistory.nextUndoDescription(activeSceneId());
    }

    public String nextRedoDescription() {
        return sceneHistory.nextRedoDescription(activeSceneId());
    }

    public boolean undoEditorAction() {
        return applyHistoryResult(sceneHistory.undo(
                activeSceneId(), scene, tabletopSceneSupplier.get()));
    }

    public boolean redoEditorAction() {
        return applyHistoryResult(sceneHistory.redo(
                activeSceneId(), scene, tabletopSceneSupplier.get()));
    }

    public void clearEditorHistory() {
        sceneHistory.clear();
    }

    private void performTransform(Runnable mutation) {
        performTokenChange(mutation);
    }

    private void performTokenChange(Runnable mutation) {
        beginSceneChange();
        mutation.run();
        endSceneChange();
    }

    private void beginSceneChange() {
        sceneHistory.begin(activeSceneId(), scene, tabletopSceneSupplier.get());
    }

    private void endSceneChange() {
        saveTabletopAction.run();
        sceneHistory.end(activeSceneId(), scene, tabletopSceneSupplier.get());
    }

    private boolean performSceneChange(BooleanSupplier mutation) {
        beginSceneChange();
        boolean handled = mutation.getAsBoolean();
        endSceneChange();
        return handled;
    }

    private boolean applyHistoryResult(EditorSceneHistory.Result result) {
        if (!result.changed()) return false;
        VttClientTokenTransformSync.markCollisionBypass(result.affectedObjectIds());
        selectionManager.removeMissingObjects(scene);
        if (result.backgroundChanged()) {
            setBackgroundAction.accept(result.backgroundAssetId());
        }
        saveTabletopAction.run();
        return true;
    }

    private String activeSceneId() {
        VttScene activeScene = tabletopSceneSupplier.get();
        return activeScene == null ? "" : activeScene.getId();
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
