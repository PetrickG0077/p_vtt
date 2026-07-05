package com.petrick.vtt.editor.tool;

/**
 * Controla qual ferramenta está ativa no editor.
 */
public final class ToolController {

    private Tool activeTool;

    public ToolController() {
        this.activeTool = new HandTool();
    }

    public Tool getActiveTool() {
        return activeTool;
    }

    public void setActiveTool(Tool activeTool) {
        if (activeTool == null) {
            throw new IllegalArgumentException("Active tool cannot be null");
        }

        this.activeTool = activeTool;
    }

    public boolean mouseClicked(ToolContext context, double mouseX, double mouseY, int button) {
        return activeTool.mouseClicked(context, mouseX, mouseY, button);
    }

    public boolean mouseReleased(ToolContext context, double mouseX, double mouseY, int button) {
        return activeTool.mouseReleased(context, mouseX, mouseY, button);
    }

    public boolean mouseDragged(
            ToolContext context,
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        return activeTool.mouseDragged(context, mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseScrolled(
            ToolContext context,
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        return activeTool.mouseScrolled(context, mouseX, mouseY, scrollX, scrollY);
    }
}