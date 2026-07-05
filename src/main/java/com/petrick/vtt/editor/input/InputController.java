package com.petrick.vtt.editor.input;

import com.petrick.vtt.core.render.RenderState;
import com.petrick.vtt.editor.tool.ToolContext;
import com.petrick.vtt.editor.tool.ToolController;
import com.petrick.vtt.feature.camera.Camera2D;

/**
 * Controla os inputs principais do VTT.
 *
 * A Screen captura eventos brutos do Minecraft
 * e repassa para este controller.
 *
 * Este controller delega as ações para a ferramenta ativa.
 */
public final class InputController {

    private final Camera2D camera;

    private final ToolController toolController;

    public InputController(Camera2D camera) {
        this.camera = camera;
        this.toolController = new ToolController();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, RenderState renderState) {
        ToolContext context = createToolContext(renderState);
        return toolController.mouseClicked(context, mouseX, mouseY, button);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button, RenderState renderState) {
        ToolContext context = createToolContext(renderState);
        return toolController.mouseReleased(context, mouseX, mouseY, button);
    }

    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY,
            RenderState renderState
    ) {
        ToolContext context = createToolContext(renderState);
        return toolController.mouseDragged(context, mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY,
            RenderState renderState
    ) {
        ToolContext context = createToolContext(renderState);
        return toolController.mouseScrolled(context, mouseX, mouseY, scrollX, scrollY);
    }

    public ToolController getToolController() {
        return toolController;
    }

    private ToolContext createToolContext(RenderState renderState) {
        return new ToolContext(camera, renderState);
    }
}