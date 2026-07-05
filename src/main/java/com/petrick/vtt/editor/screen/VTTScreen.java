package com.petrick.vtt.editor.screen;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.core.render.RenderState;
import com.petrick.vtt.feature.camera.Camera2D;
import com.petrick.vtt.feature.viewport.Viewport;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Tela principal do Virtual Tabletop.
 *
 * Esta Screen assume o controle visual enquanto o VTT está aberto.
 */
public final class VTTScreen extends Screen {

    private final Camera2D camera;

    private Viewport viewport;

    private RenderState renderState;

    public VTTScreen() {
        super(Component.literal("Virtual Tabletop"));

        this.camera = new Camera2D();
    }

    @Override
    protected void init() {
        this.viewport = Viewport.fullScreen(this.width, this.height);
        this.renderState = new RenderState(camera, viewport);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ensureRenderState();

        VRenderContext context = new VRenderContext(
                graphics,
                renderState,
                mouseX,
                mouseY,
                partialTick,
                this.width,
                this.height
        );

        /*
         * Importante:
         * Não chamamos renderBackground() nem super.render() depois do nosso desenho.
         * Isso evita o blur/fundo padrão dos menus do Minecraft.
         */
        renderOpaqueBackground(context);
        renderDebugInfo(context);
    }

    private void renderOpaqueBackground(VRenderContext context) {
        context.graphics().fill(
                0,
                0,
                context.screenWidth(),
                context.screenHeight(),
                0xFF101014
        );
    }

    private void renderDebugInfo(VRenderContext context) {
        GuiGraphics graphics = context.graphics();

        graphics.drawCenteredString(
                this.font,
                this.title,
                this.width / 2,
                this.height / 2 - 20,
                0xFFFFFFFF
        );

        graphics.drawCenteredString(
                this.font,
                "Sprint 1 - VTT Screen",
                this.width / 2,
                this.height / 2,
                0xFFAAAAAA
        );

        graphics.drawString(
                this.font,
                "Mouse Screen: " + formatVec(context.mouseScreenPosition()),
                10,
                10,
                0xFFFFFFFF
        );

        graphics.drawString(
                this.font,
                "Mouse World: " + formatVec(context.mouseWorldPosition()),
                10,
                22,
                0xFFFFFFFF
        );

        graphics.drawString(
                this.font,
                "Camera: " + formatVec(camera.getPosition()),
                10,
                34,
                0xFFFFFFFF
        );

        graphics.drawString(
                this.font,
                "Zoom: " + String.format(Locale.ROOT, "%.2f", camera.getZoom()),
                10,
                46,
                0xFFFFFFFF
        );

        graphics.drawString(
                this.font,
                "Press ESC to close",
                10,
                58,
                0xFFAAAAAA
        );
    }

    private void ensureRenderState() {
        if (viewport == null || renderState == null) {
            this.viewport = Viewport.fullScreen(this.width, this.height);
            this.renderState = new RenderState(camera, viewport);
        }
    }

    private static String formatVec(Vec2d vec) {
        return String.format(Locale.ROOT, "(%.2f, %.2f)", vec.x(), vec.y());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}