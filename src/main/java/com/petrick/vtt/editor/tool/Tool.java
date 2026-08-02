package com.petrick.vtt.editor.tool;

import com.petrick.vtt.platform.render.VRenderContext;

/**
 * Interface base para ferramentas do editor VTT.
 */
public interface Tool {

    String getId();

    default EditorCursor getCursor(ToolContext context, double mouseX, double mouseY) {
        return EditorCursor.DEFAULT;
    }

    default void render(VRenderContext renderContext, ToolContext toolContext) {
    }

    default boolean mouseClicked(
            ToolContext context,
            double mouseX,
            double mouseY,
            int button,
            int modifiers
    ) {
        return false;
    }

    default boolean mouseReleased(
            ToolContext context,
            double mouseX,
            double mouseY,
            int button,
            int modifiers
    ) {
        return false;
    }

    default boolean mouseDragged(
            ToolContext context,
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY,
            int modifiers
    ) {
        return false;
    }

    default boolean keyPressed(int keyCode) { return false; }

    default boolean charTyped(char character) { return false; }
}
