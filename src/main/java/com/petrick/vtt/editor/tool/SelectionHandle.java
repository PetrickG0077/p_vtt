package com.petrick.vtt.editor.tool;

/**
 * Representa os cantos usados para redimensionar uma seleção.
 */
public enum SelectionHandle {

    TOP_LEFT(EditorCursor.RESIZE_NWSE),

    TOP_RIGHT(EditorCursor.RESIZE_NESW),

    BOTTOM_LEFT(EditorCursor.RESIZE_NESW),

    BOTTOM_RIGHT(EditorCursor.RESIZE_NWSE);

    private final EditorCursor cursor;

    SelectionHandle(EditorCursor cursor) {
        this.cursor = cursor;
    }

    public EditorCursor getCursor() {
        return cursor;
    }
}