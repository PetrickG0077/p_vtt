package com.petrick.vtt.editor.tool;

import com.petrick.vtt.core.render.RenderState;
import com.petrick.vtt.feature.camera.Camera2D;

/**
 * Contexto entregue para as ferramentas.
 *
 * Ele contém apenas aquilo que uma ferramenta precisa acessar
 * para executar suas ações.
 */
public final class ToolContext {

    private final Camera2D camera;

    private final RenderState renderState;

    public ToolContext(Camera2D camera, RenderState renderState) {
        this.camera = camera;
        this.renderState = renderState;
    }

    public Camera2D camera() {
        return camera;
    }

    public RenderState renderState() {
        return renderState;
    }
}