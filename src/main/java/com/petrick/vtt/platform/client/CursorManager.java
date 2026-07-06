package com.petrick.vtt.platform.client;

import com.petrick.vtt.editor.tool.EditorCursor;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Controla o cursor do mouse dentro da tela do VTT.
 */
public final class CursorManager {

    /*
     * Cursores padrão do GLFW.
     *
     * GLFW_RESIZE_NWSE_CURSOR:
     * canto superior esquerdo / canto inferior direito
     *
     * GLFW_RESIZE_NESW_CURSOR:
     * canto superior direito / canto inferior esquerdo
     */
    private static final int GLFW_RESIZE_NWSE_CURSOR = 0x00036007;
    private static final int GLFW_RESIZE_NESW_CURSOR = 0x00036008;

    private static long arrowCursor;

    private static long resizeNwseCursor;

    private static long resizeNeswCursor;

    private static EditorCursor currentCursor = EditorCursor.DEFAULT;

    private CursorManager() {
    }

    public static void apply(EditorCursor cursor) {
        if (cursor == null) {
            cursor = EditorCursor.DEFAULT;
        }

        if (cursor == currentCursor) {
            return;
        }

        long window = Minecraft.getInstance().getWindow().getWindow();
        long cursorHandle = getCursorHandle(cursor);

        GLFW.glfwSetCursor(window, cursorHandle);
        currentCursor = cursor;
    }

    public static void reset() {
        currentCursor = null;
        apply(EditorCursor.DEFAULT);
    }

    private static long getCursorHandle(EditorCursor cursor) {
        return switch (cursor) {
            case RESIZE_NWSE -> getResizeNwseCursor();
            case RESIZE_NESW -> getResizeNeswCursor();
            case DEFAULT -> getArrowCursor();
        };
    }

    private static long getArrowCursor() {
        if (arrowCursor == 0L) {
            arrowCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR);
        }

        return arrowCursor;
    }

    private static long getResizeNwseCursor() {
        if (resizeNwseCursor == 0L) {
            resizeNwseCursor = GLFW.glfwCreateStandardCursor(GLFW_RESIZE_NWSE_CURSOR);

            if (resizeNwseCursor == 0L) {
                resizeNwseCursor = getArrowCursor();
            }
        }

        return resizeNwseCursor;
    }

    private static long getResizeNeswCursor() {
        if (resizeNeswCursor == 0L) {
            resizeNeswCursor = GLFW.glfwCreateStandardCursor(GLFW_RESIZE_NESW_CURSOR);

            if (resizeNeswCursor == 0L) {
                resizeNeswCursor = getArrowCursor();
            }
        }

        return resizeNeswCursor;
    }
}