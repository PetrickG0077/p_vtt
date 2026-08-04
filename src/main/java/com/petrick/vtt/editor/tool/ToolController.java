package com.petrick.vtt.editor.tool;

import com.petrick.vtt.platform.render.VRenderContext;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.core.session.VttRole;
import com.petrick.vtt.feature.tabletop.VttScene;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.Consumer;

/**
 * Controla qual ferramenta está ativa no editor.
 */
public final class ToolController {

    private final HandTool handTool;

    private final SelectTool selectTool;
    private final WallTool wallTool;
    private final DoorTool doorTool;
    private final FogTool fogTool;
    private final LightTool lightTool;
    private final MeasureTool measureTool;
    private final Supplier<VttRole> roleSupplier;

    private Tool activeTool;

    public ToolController(
            Supplier<VttScene> sceneSupplier, Runnable saveAction,
            Supplier<VttRole> roleSupplier, Supplier<String> playerIdSupplier,
            BooleanSupplier spectatorSupplier,
            Consumer<String> saveDefaultCollisionAction
    ) {
        this.roleSupplier = roleSupplier;
        this.handTool = new HandTool();
        this.selectTool = new SelectTool(
                sceneSupplier, roleSupplier, playerIdSupplier, spectatorSupplier,
                saveAction, saveDefaultCollisionAction);
        this.wallTool = new WallTool(sceneSupplier, saveAction);
        this.doorTool = new DoorTool(sceneSupplier, saveAction);
        this.fogTool = new FogTool(sceneSupplier, saveAction);
        this.lightTool = new LightTool(sceneSupplier, saveAction);
        this.measureTool = new MeasureTool(sceneSupplier);

        this.activeTool = handTool;
    }

    public Tool getActiveTool() {
        return activeTool;
    }

    public String getActiveToolId() {
        return activeTool.getId();
    }

    public void setActiveTool(Tool activeTool) {
        if (activeTool == null) {
            throw new IllegalArgumentException("Active tool cannot be null");
        }

        this.activeTool = activeTool;
    }

    public void selectHandTool() {
        selectTool.closeCollisionBoxEditor();
        wallTool.deactivate();
        doorTool.deactivate();
        fogTool.deactivate();
        lightTool.deactivate();
        measureTool.deactivate();
        setActiveTool(handTool);
    }

    public void selectSelectTool() {
        wallTool.deactivate();
        doorTool.deactivate();
        fogTool.deactivate();
        lightTool.deactivate();
        measureTool.deactivate();
        setActiveTool(selectTool);
    }

    public boolean toggleCollisionBoxEditor(ToolContext context) {
        if (activeTool != selectTool) selectSelectTool();
        return selectTool.toggleCollisionBoxEditor(context);
    }

    public boolean isEditingCollisionBox() {
        return activeTool == selectTool && selectTool.isEditingCollisionBox();
    }

    public boolean closeCollisionBoxEditor() {
        return selectTool.closeCollisionBoxEditor();
    }

    public void selectWallTool() {
        selectTool.closeCollisionBoxEditor();
        doorTool.deactivate();
        fogTool.deactivate();
        lightTool.deactivate();
        measureTool.deactivate();
        setActiveTool(wallTool);
    }

    public void selectDoorTool() {
        selectTool.closeCollisionBoxEditor();
        wallTool.deactivate();
        fogTool.deactivate();
        lightTool.deactivate();
        measureTool.deactivate();
        setActiveTool(doorTool);
    }

    public void selectFogTool() {
        selectTool.closeCollisionBoxEditor();
        wallTool.deactivate();
        doorTool.deactivate();
        lightTool.deactivate();
        measureTool.deactivate();
        fogTool.activateLocalizedMode();
        setActiveTool(fogTool);
    }

    public void selectLightTool() {
        selectTool.closeCollisionBoxEditor();
        wallTool.deactivate();
        doorTool.deactivate();
        fogTool.deactivate();
        measureTool.deactivate();
        setActiveTool(lightTool);
    }

    public boolean selectLight(String lightId) {
        selectLightTool();
        return lightTool.selectLight(lightId);
    }

    public String getSelectedLightId() {
        return activeTool == lightTool ? lightTool.getSelectedLightId() : null;
    }

    public void selectMeasureTool() {
        selectTool.closeCollisionBoxEditor();
        wallTool.deactivate();
        doorTool.deactivate();
        fogTool.deactivate();
        lightTool.deactivate();
        setActiveTool(measureTool);
    }

    public boolean cancelWallDrawing() {
        if (activeTool != wallTool || !wallTool.isDrawing()) return false;
        wallTool.cancel();
        return true;
    }

    public boolean cancelDoorEditing() {
        if (activeTool != doorTool || !doorTool.isEditing()) return false;
        doorTool.cancel();
        return true;
    }

    public boolean cancelFogDrawing() {
        if (activeTool != fogTool || !fogTool.isDrawing()) return false;
        fogTool.cancel();
        return true;
    }

    public boolean cancelMeasurement() {
        return activeTool == measureTool && measureTool.cancel();
    }

    public boolean toggleSelectedFogVisibility() {
        return activeTool == fogTool && fogTool.toggleSelectedVisibility();
    }

    public boolean toggleFogEnabled() {
        return activeTool == fogTool && fogTool.toggleEnabled();
    }

    public boolean deleteSelectedFogArea() {
        return activeTool == fogTool && fogTool.deleteSelectedArea();
    }

    public boolean deleteSelectedLight() {
        return activeTool == lightTool && lightTool.deleteSelected();
    }

    public boolean keyPressed(int keyCode) { return activeTool.keyPressed(keyCode); }

    public boolean charTyped(char character) { return activeTool.charTyped(character); }

    public boolean scaleSelectedFogArea(double factor) {
        return activeTool == fogTool && fogTool.scaleSelectedArea(factor);
    }

    public boolean rotateSelectedFogArea(double degrees) {
        return activeTool == fogTool && fogTool.rotateSelectedArea(degrees);
    }

    public boolean resetSelectedFogAreaTransform() {
        return activeTool == fogTool && fogTool.resetSelectedAreaTransform();
    }

    public boolean deleteSelectedWall() {
        return activeTool == wallTool && wallTool.deleteSelectedWall();
    }

    public boolean scaleSelectedWall(double factor) {
        return activeTool == wallTool && wallTool.scaleSelectedWall(factor);
    }

    public boolean rotateSelectedWall(double degrees) {
        return activeTool == wallTool && wallTool.rotateSelectedWall(degrees);
    }

    public boolean resetSelectedWallTransform() {
        return activeTool == wallTool && wallTool.resetSelectedWallTransform();
    }

    public boolean deleteSelectedDoor() {
        return activeTool == doorTool && doorTool.deleteSelectedDoor();
    }

    public boolean toggleSelectedDoorOpen() {
        return activeTool == doorTool && doorTool.toggleSelectedDoorOpen();
    }

    public boolean toggleSelectedDoorLocked() {
        return activeTool == doorTool && doorTool.toggleSelectedDoorLocked();
    }

    public boolean scaleSelectedDoor(double factor) {
        return activeTool == doorTool && doorTool.scaleSelectedDoor(factor);
    }

    public boolean rotateSelectedDoor(double degrees) {
        return activeTool == doorTool && doorTool.rotateSelectedDoor(degrees);
    }

    public boolean resetSelectedDoorTransform() {
        return activeTool == doorTool && doorTool.resetSelectedDoorTransform();
    }

    public EditorCursor getCursor(ToolContext context, double mouseX, double mouseY) {
        return activeTool.getCursor(context, mouseX, mouseY);
    }

    public void render(VRenderContext renderContext, ToolContext toolContext) {
        activeTool.render(renderContext, toolContext);
    }

    public boolean mouseClicked(
            ToolContext context,
            double mouseX,
            double mouseY,
            int button,
            int modifiers
    ) {
        if (activeTool == selectTool && button == 0 && roleSupplier.get() == VttRole.MASTER) {
            Vec2d worldPosition = context.renderState().screenToWorld(new Vec2d(mouseX, mouseY));
            if (doorTool.selectDoorAt(worldPosition)) {
                context.selectionManager().clearSelection();
                setActiveTool(doorTool);
                return true;
            }
        }
        return activeTool.mouseClicked(context, mouseX, mouseY, button, modifiers);
    }

    public boolean mouseReleased(
            ToolContext context,
            double mouseX,
            double mouseY,
            int button,
            int modifiers
    ) {
        boolean handled = activeTool.mouseReleased(context, mouseX, mouseY, button, modifiers);
        if (activeTool == doorTool && doorTool.consumeSelectToolRequest()) {
            selectSelectTool();
            return true;
        }
        return handled;
    }

    public boolean mouseDragged(
            ToolContext context,
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY,
            int modifiers
    ) {
        return activeTool.mouseDragged(context, mouseX, mouseY, button, dragX, dragY, modifiers);
    }
}
