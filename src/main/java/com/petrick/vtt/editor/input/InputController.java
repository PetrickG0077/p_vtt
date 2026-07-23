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

    private final EditorTokenHistory tokenHistory = new EditorTokenHistory();

    private boolean globalPanning;

    private Vec2d lastGlobalPanMousePosition;

    public InputController(
            Camera2D camera,
            CanvasScene scene,
            SelectionManager selectionManager,
            Supplier<VttScene> tabletopSceneSupplier,
            Runnable saveTabletopAction,
            Supplier<VttRole> roleSupplier,
            Supplier<String> playerIdSupplier
    ) {
        this.camera = camera;
        this.scene = scene;
        this.selectionManager = selectionManager;
        this.tabletopSceneSupplier = tabletopSceneSupplier;
        this.saveTabletopAction = saveTabletopAction;
        this.toolController = new ToolController(tabletopSceneSupplier, saveTabletopAction,
                roleSupplier, playerIdSupplier);
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

        if (button == 0 && "select".equals(toolController.getActiveToolId())) {
            tokenHistory.begin(activeSceneId(), scene, tabletopSceneSupplier.get());
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
        if (button == 0 && tokenHistory.end(
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
        beginTokenChange();
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
        endTokenChange();
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
        return toolController.toggleCollisionBoxEditor(createToolContext(renderState));
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
        beginTokenChange();
    }

    public void endTokenLifecycleChange() {
        endTokenChange();
    }

    public boolean canUndoTokenAction() {
        return tokenHistory.canUndo(activeSceneId());
    }

    public boolean canRedoTokenAction() {
        return tokenHistory.canRedo(activeSceneId());
    }

    public boolean undoTokenAction() {
        return applyHistoryResult(tokenHistory.undo(
                activeSceneId(), scene, tabletopSceneSupplier.get()));
    }

    public boolean redoTokenAction() {
        return applyHistoryResult(tokenHistory.redo(
                activeSceneId(), scene, tabletopSceneSupplier.get()));
    }

    public void clearTokenHistory() {
        tokenHistory.clear();
    }

    private void performTransform(Runnable mutation) {
        performTokenChange(mutation);
    }

    private void performTokenChange(Runnable mutation) {
        beginTokenChange();
        mutation.run();
        endTokenChange();
    }

    private void beginTokenChange() {
        tokenHistory.begin(activeSceneId(), scene, tabletopSceneSupplier.get());
    }

    private void endTokenChange() {
        saveTabletopAction.run();
        tokenHistory.end(activeSceneId(), scene, tabletopSceneSupplier.get());
    }

    private boolean applyHistoryResult(EditorTokenHistory.Result result) {
        if (!result.changed()) return false;
        VttClientTokenTransformSync.markCollisionBypass(result.affectedObjectIds());
        selectionManager.removeMissingObjects(scene);
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
