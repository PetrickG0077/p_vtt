package com.petrick.vtt.editor.screen;

import com.petrick.vtt.VTT;
import com.petrick.vtt.editor.overlay.AssetCatalogOverlay;
import com.petrick.vtt.core.session.VTTSession;
import com.petrick.vtt.core.render.RenderState;
import com.petrick.vtt.editor.input.InputController;
import com.petrick.vtt.editor.overlay.DebugOverlay;
import com.petrick.vtt.editor.overlay.SelectionInspectorOverlay;
import com.petrick.vtt.feature.camera.Camera2D;
import com.petrick.vtt.feature.canvas.CanvasRenderer;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.feature.selection.SelectionManager;
import com.petrick.vtt.feature.viewport.Viewport;
import com.petrick.vtt.platform.client.CursorManager;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import com.petrick.vtt.feature.asset.AssetRegistry;
import net.minecraft.network.chat.Component;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.core.transform.Transform2D;
import com.petrick.vtt.feature.asset.AssetRef;
import com.petrick.vtt.feature.asset.BuiltInTextureAssetRef;
import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.visual.TextureVisual;
import org.lwjgl.glfw.GLFW;

/**
 * Tela principal do Virtual Tabletop.
 *
 * Esta Screen assume o controle visual enquanto o VTT está aberto.
 */
public final class VTTScreen extends Screen {

    private final Camera2D camera;

    private final CanvasScene scene;

    private final SelectionManager selectionManager;

    private final CanvasRenderer canvasRenderer;

    private final InputController inputController;

    private final DebugOverlay debugOverlay;

    private final SelectionInspectorOverlay selectionInspectorOverlay;

    private final AssetRegistry assetRegistry;

    private final AssetCatalogOverlay assetCatalogOverlay;

    private final VTTSession session;

    private Viewport viewport;

    private RenderState renderState;

    private AssetRef draggingAsset;

    public VTTScreen() {
        super(Component.literal("Virtual Tabletop"));

        this.session = VTT.getApplication().getActiveSession();

        this.camera = new Camera2D();
        this.assetRegistry = session.getAssetRegistry();
        this.scene = session.getCanvasScene();

        this.selectionManager = new SelectionManager();
        this.canvasRenderer = new CanvasRenderer();
        this.inputController = new InputController(camera, scene, selectionManager);
        this.debugOverlay = new DebugOverlay();
        this.selectionInspectorOverlay = new SelectionInspectorOverlay();
        this.assetCatalogOverlay = new AssetCatalogOverlay();
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

        updateCursor(mouseX, mouseY);

        renderOpaqueBackground(context);
        canvasRenderer.render(context, scene, selectionManager);
        inputController.renderToolOverlay(context, renderState);
        renderTitle(context);
        debugOverlay.render(
                context,
                this.font,
                camera,
                inputController.getActiveToolId(),
                assetRegistry.size()
        );
        selectionInspectorOverlay.render(context, this.font, scene, selectionManager);
        assetCatalogOverlay.render(context, this.font, assetRegistry);
        if (draggingAsset != null) {
            assetCatalogOverlay.renderDragPreview(
                    context,
                    this.font,
                    draggingAsset,
                    mouseX,
                    mouseY
            );
        }
    }

    private Vec2d getDefaultTokenSize(AssetRef assetRef) {
        if (assetRef instanceof BuiltInTextureAssetRef builtInTexture) {
            return new Vec2d(
                    builtInTexture.textureWidth(),
                    builtInTexture.textureHeight()
            );
        }

        return new Vec2d(96.0, 96.0);
    }

    private void createTokenFromDraggedAsset(double mouseX, double mouseY, AssetRef assetRef) {
        if (renderState == null) {
            return;
        }

        Vec2d worldPosition = renderState.screenToWorld(new Vec2d(mouseX, mouseY));

        Vec2d tokenSize = getDefaultTokenSize(assetRef);

        String objectId = scene.createUniqueObjectId("token");

        CanvasObject token = new CanvasObject(
                objectId,
                new Transform2D(
                        worldPosition,
                        0.0,
                        new Vec2d(1.0, 1.0)
                ),
                tokenSize,
                new TextureVisual(assetRef)
        );

        scene.addObject(token);

        selectionManager.selectOnly(objectId);
        inputController.selectSelectTool();
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

    private void renderTitle(VRenderContext context) {
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
                "Sprint 2 - Tokens & Assets",
                this.width / 2,
                this.height / 2,
                0xFFAAAAAA
        );
    }

    private void ensureRenderState() {
        if (viewport == null || renderState == null) {
            this.viewport = Viewport.fullScreen(this.width, this.height);
            this.renderState = new RenderState(camera, viewport);
        }
    }

    private void updateCursor(int mouseX, int mouseY) {
        if (renderState == null) {
            CursorManager.reset();
            return;
        }

        CursorManager.apply(inputController.getCursor(mouseX, mouseY, renderState));
    }

    private int getKeyboardModifiers() {
        if (this.minecraft == null) {
            return 0;
        }

        long window = this.minecraft.getWindow().getWindow();

        int modifiers = 0;

        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS) {
            modifiers |= GLFW.GLFW_MOD_SHIFT;
        }

        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS) {
            modifiers |= GLFW.GLFW_MOD_CONTROL;
        }

        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS) {
            modifiers |= GLFW.GLFW_MOD_ALT;
        }

        return modifiers;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            var clickedAsset = assetCatalogOverlay.findAssetAt(
                    assetRegistry,
                    this.height,
                    mouseX,
                    mouseY
            );

            if (clickedAsset.isPresent()) {
                this.draggingAsset = clickedAsset.get();
                return true;
            }
        }

        if (renderState != null && inputController.mouseClicked(
                mouseX,
                mouseY,
                button,
                getKeyboardModifiers(),
                renderState
        )) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingAsset != null) {
            createTokenFromDraggedAsset(mouseX, mouseY, draggingAsset);
            this.draggingAsset = null;
            return true;
        }

        if (renderState != null && inputController.mouseReleased(
                mouseX,
                mouseY,
                button,
                getKeyboardModifiers(),
                renderState
        )) {
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
        if (renderState != null && inputController.mouseDragged(
                mouseX,
                mouseY,
                button,
                dragX,
                dragY,
                getKeyboardModifiers(),
                renderState
        )) {
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
        if (renderState != null && inputController.mouseScrolled(
                mouseX,
                mouseY,
                scrollX,
                scrollY,
                renderState
        )) {
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_H) {
            inputController.selectHandTool();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_S) {
            inputController.selectSelectTool();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD) {
            inputController.scaleSelectedObjectsUp();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
            inputController.scaleSelectedObjectsDown();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_Q) {
            inputController.rotateSelectedObjectsLeft();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_E) {
            inputController.rotateSelectedObjectsRight();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            inputController.deleteSelectedObjects();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        CursorManager.reset();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}