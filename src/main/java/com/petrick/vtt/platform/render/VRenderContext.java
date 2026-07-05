package com.petrick.vtt.platform.render;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.core.render.RenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Contexto de renderização do VTT.
 *
 * Esta classe conecta o sistema de renderização do Minecraft
 * com o estado matemático do nosso VTT.
 */
public final class VRenderContext {

    private final GuiGraphics graphics;

    private final RenderState renderState;

    private final int mouseX;

    private final int mouseY;

    private final float partialTick;

    private final int screenWidth;

    private final int screenHeight;

    private final double guiScale;

    public VRenderContext(
            GuiGraphics graphics,
            RenderState renderState,
            int mouseX,
            int mouseY,
            float partialTick,
            int screenWidth,
            int screenHeight
    ) {
        this.graphics = graphics;
        this.renderState = renderState;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.partialTick = partialTick;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.guiScale = Minecraft.getInstance().getWindow().getGuiScale();
    }

    public GuiGraphics graphics() {
        return graphics;
    }

    public RenderState renderState() {
        return renderState;
    }

    public int mouseX() {
        return mouseX;
    }

    public int mouseY() {
        return mouseY;
    }

    public float partialTick() {
        return partialTick;
    }

    public int screenWidth() {
        return screenWidth;
    }

    public int screenHeight() {
        return screenHeight;
    }

    public double guiScale() {
        return guiScale;
    }

    public Vec2d mouseScreenPosition() {
        return new Vec2d(mouseX, mouseY);
    }

    public Vec2d mouseWorldPosition() {
        return renderState.screenToWorld(mouseScreenPosition());
    }
}