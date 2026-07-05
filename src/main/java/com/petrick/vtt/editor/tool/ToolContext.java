package com.petrick.vtt.editor.tool;

import com.petrick.vtt.core.render.RenderState;
import com.petrick.vtt.feature.camera.Camera2D;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.feature.selection.SelectionManager;

/**
 * Contexto entregue para as ferramentas.
 *
 * Ele contém apenas aquilo que uma ferramenta precisa acessar
 * para executar suas ações.
 */
public final class ToolContext {

    private final Camera2D camera;

    private final RenderState renderState;

    private final CanvasScene scene;

    private final SelectionManager selectionManager;

    public ToolContext(
            Camera2D camera,
            RenderState renderState,
            CanvasScene scene,
            SelectionManager selectionManager
    ) {
        this.camera = camera;
        this.renderState = renderState;
        this.scene = scene;
        this.selectionManager = selectionManager;
    }

    public Camera2D camera() {
        return camera;
    }

    public RenderState renderState() {
        return renderState;
    }

    public CanvasScene scene() {
        return scene;
    }

    public SelectionManager selectionManager() {
        return selectionManager;
    }
}