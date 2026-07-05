package com.petrick.vtt.editor.tool;

/**
 * Interface base para ferramentas do editor VTT.
 *
 * Exemplos futuros:
 * - HandTool: mover a câmera
 * - SelectTool: selecionar tokens
 * - MeasureTool: medir distância
 * - DrawTool: desenhar no mapa
 */
public interface Tool {

    String getId();

    default boolean mouseClicked(ToolContext context, double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseReleased(ToolContext context, double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseDragged(
            ToolContext context,
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        return false;
    }

    default boolean mouseScrolled(
            ToolContext context,
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        return false;
    }
}