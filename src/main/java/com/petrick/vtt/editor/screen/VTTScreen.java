package com.petrick.vtt.editor.screen;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.core.render.RenderState;
import com.petrick.vtt.feature.camera.Camera2D;
import com.petrick.vtt.feature.canvas.CanvasRenderer;
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

    private final CanvasRenderer canvasRenderer;

    private boolean panning;

    private Vec2d lastMousePosition;

    private Viewport viewport;

    private RenderState renderState;

    public VTTScreen() {
        super(Component.literal("Virtual Tabletop"));

        this.camera = new Camera2D();
        this.canvasRenderer = new CanvasRenderer();
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

        renderOpaqueBackground(context);
        canvasRenderer.render(context);
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 2) {
            this.panning = true;
            this.lastMousePosition = new Vec2d(mouseX, mouseY);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 2) {
            this.panning = false;
            this.lastMousePosition = null;
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (panning && lastMousePosition != null) {
            Vec2d currentMousePosition = new Vec2d(mouseX, mouseY);
            Vec2d delta = currentMousePosition.subtract(lastMousePosition);

            camera.moveByScreenDelta(delta);

            lastMousePosition = currentMousePosition;
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        if (renderState == null) {
            return false;
        }

        double zoomFactor = scrollY > 0 ? 1.1 : 0.9;

        camera.zoomAtScreenPoint(
                zoomFactor,
                new Vec2d(mouseX, mouseY),
                renderState.getViewportBounds()
        );

        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}