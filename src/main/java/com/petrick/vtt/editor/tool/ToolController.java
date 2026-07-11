package com.petrick.vtt.editor.tool;

import com.petrick.vtt.platform.render.VRenderContext;
import com.petrick.vtt.feature.tabletop.VttScene;
import java.util.function.Supplier;

/**
 * Controla qual ferramenta está ativa no editor.
 */
public final class ToolController {

    private final HandTool handTool;

    private final SelectTool selectTool;
    private final WallTool wallTool;

    private Tool activeTool;

    public ToolController(Supplier<VttScene> sceneSupplier, Runnable saveAction) {
        this.handTool = new HandTool();
        this.selectTool = new SelectTool();
        this.wallTool = new WallTool(sceneSupplier, saveAction);

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
        wallTool.deactivate();
        setActiveTool(handTool);
    }

    public void selectSelectTool() {
        wallTool.deactivate();
        setActiveTool(selectTool);
    }

    public void selectWallTool() { setActiveTool(wallTool); }

    public boolean cancelWallDrawing() {
        if (activeTool != wallTool || !wallTool.isDrawing()) return false;
        wallTool.cancel();
        return true;
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
        return activeTool.mouseClicked(context, mouseX, mouseY, button, modifiers);
    }

    public boolean mouseReleased(
            ToolContext context,
            double mouseX,
            double mouseY,
            int button,
            int modifiers
    ) {
        return activeTool.mouseReleased(context, mouseX, mouseY, button, modifiers);
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
