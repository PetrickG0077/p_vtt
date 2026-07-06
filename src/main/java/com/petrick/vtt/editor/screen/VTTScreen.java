package com.petrick.vtt.editor.screen;

import com.petrick.vtt.VTT;
import com.petrick.vtt.editor.overlay.AssetCatalogOverlay;
import com.petrick.vtt.core.session.VTTSession;
import com.petrick.vtt.core.render.RenderState;
import com.petrick.vtt.editor.input.InputController;
import com.petrick.vtt.editor.overlay.DebugOverlay;
import com.petrick.vtt.editor.overlay.SelectionInspectorOverlay;
import com.petrick.vtt.editor.overlay.SceneOutlinerOverlay;
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
import com.petrick.vtt.feature.asset.AssetRef;
import com.petrick.vtt.editor.overlay.TokenCatalogOverlay;
import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.token.TokenDefinitionRegistry;
import com.petrick.vtt.feature.token.TokenDefinition;
import com.petrick.vtt.feature.token.TokenFactory;
import com.petrick.vtt.feature.token.DebugTokenDefinitions;
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

    private final SceneOutlinerOverlay sceneOutlinerOverlay;

    private final VTTSession session;

    private final TokenDefinitionRegistry tokenDefinitionRegistry;

    private final TokenCatalogOverlay tokenCatalogOverlay;

    private TokenDefinition draggingTokenDefinition;

    private Viewport viewport;

    private RenderState renderState;

    private AssetRef draggingAsset;

    private String renamingObjectId;

    private String renameBuffer;

    public VTTScreen() {
        super(Component.literal("Virtual Tabletop"));

        this.session = VTT.getApplication().getActiveSession();

        this.camera = new Camera2D();
        this.assetRegistry = session.getAssetRegistry();
        this.tokenDefinitionRegistry = session.getTokenDefinitionRegistry();
        this.scene = session.getCanvasScene();

        this.selectionManager = new SelectionManager();
        this.canvasRenderer = new CanvasRenderer();
        this.inputController = new InputController(camera, scene, selectionManager);
        this.debugOverlay = new DebugOverlay();
        this.tokenCatalogOverlay = new TokenCatalogOverlay();
        this.sceneOutlinerOverlay = new SceneOutlinerOverlay();
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
                assetRegistry.size(),
                tokenDefinitionRegistry.size()
        );
        selectionInspectorOverlay.render(context, this.font, scene, selectionManager);
        assetCatalogOverlay.render(context, this.font, assetRegistry);
        tokenCatalogOverlay.render(context, this.font, tokenDefinitionRegistry);
        sceneOutlinerOverlay.render(context, this.font, scene, selectionManager);

        if (draggingAsset != null) {
            assetCatalogOverlay.renderDragPreview(
                    context,
                    this.font,
                    draggingAsset,
                    mouseX,
                    mouseY
            );
        }

        if (draggingTokenDefinition != null) {
            tokenCatalogOverlay.renderDragPreview(
                    context,
                    this.font,
                    draggingTokenDefinition,
                    mouseX,
                    mouseY
            );
        }

        if (renamingObjectId != null) {
            renderRenameDialog(context);
        }

        if (renamingObjectId != null) {
            renderRenameDialog(context);
        }
    }

    private void createTokenFromDraggedAsset(double mouseX, double mouseY, AssetRef assetRef) {
        if (renderState == null) {
            return;
        }

        Vec2d worldPosition = renderState.screenToWorld(new Vec2d(mouseX, mouseY));

        String objectId = scene.createUniqueObjectId("token");

        TokenDefinition definition = tokenDefinitionRegistry.getRequired(
                DebugTokenDefinitions.TEST_TOKEN_DEFINITION_ID
        );

        CanvasObject token = TokenFactory.createCanvasObject(
                definition,
                objectId,
                worldPosition
        );

        scene.addObject(token);

        selectionManager.selectOnly(objectId);
        inputController.selectSelectTool();
    }

    private void createTokenFromDraggedTokenDefinition(
            double mouseX,
            double mouseY,
            TokenDefinition definition
    ) {
        if (renderState == null) {
            return;
        }

        Vec2d worldPosition = renderState.screenToWorld(new Vec2d(mouseX, mouseY));

        String objectId = scene.createUniqueObjectId("token");

        CanvasObject token = TokenFactory.createCanvasObject(
                definition,
                objectId,
                worldPosition
        );

        scene.addObject(token);

        selectionManager.selectOnly(objectId);
        inputController.selectSelectTool();
    }

    private void beginRenameSelectedObject() {
        if (selectionManager.getSelectedObjectIds().size() != 1) {
            return;
        }

        String selectedObjectId = selectionManager.getSelectedObjectIds()
                .iterator()
                .next();

        CanvasObject object = scene.findObjectById(selectedObjectId);

        if (object == null) {
            return;
        }

        this.renamingObjectId = object.id();
        this.renameBuffer = object.displayName();
    }

    private void confirmRename() {
        if (renamingObjectId == null || renameBuffer == null) {
            return;
        }

        scene.renameObject(renamingObjectId, renameBuffer);

        this.renamingObjectId = null;
        this.renameBuffer = null;
    }

    private void cancelRename() {
        this.renamingObjectId = null;
        this.renameBuffer = null;
    }

    private boolean isRenaming() {
        return renamingObjectId != null;
    }

    private void renderRenameDialog(VRenderContext context) {
        int width = 300;
        int height = 70;

        int x = context.screenWidth() / 2 - width / 2;
        int y = context.screenHeight() / 2 - height / 2 + 70;

        int background = 0xDD000000;
        int border = 0xFF66CCFF;
        int textColor = 0xFFFFFFFF;
        int mutedColor = 0xFFAAAAAA;

        context.graphics().fill(
                x,
                y,
                x + width,
                y + height,
                background
        );

        context.graphics().hLine(x, x + width, y, border);
        context.graphics().hLine(x, x + width, y + height, border);
        context.graphics().vLine(x, y, y + height, border);
        context.graphics().vLine(x + width, y, y + height, border);

        context.graphics().drawString(
                this.font,
                "Rename Object",
                x + 10,
                y + 10,
                textColor,
                false
        );

        context.graphics().drawString(
                this.font,
                renameBuffer + "_",
                x + 10,
                y + 28,
                textColor,
                false
        );

        context.graphics().drawString(
                this.font,
                "Enter: confirm   Esc: cancel",
                x + 10,
                y + 48,
                mutedColor,
                false
        );
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
            var clickedTokenDefinition = tokenCatalogOverlay.findTokenDefinitionAt(
                    tokenDefinitionRegistry,
                    this.height,
                    mouseX,
                    mouseY
            );

            if (clickedTokenDefinition.isPresent()) {
                this.draggingTokenDefinition = clickedTokenDefinition.get();
                return true;
            }

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

            var clickedObjectId = sceneOutlinerOverlay.findObjectIdAt(
                    scene,
                    mouseX,
                    mouseY
            );

            if (clickedObjectId.isPresent()) {
                if ((getKeyboardModifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
                    selectionManager.toggle(clickedObjectId.get());
                } else {
                    selectionManager.selectOnly(clickedObjectId.get());
                }

                inputController.selectSelectTool();
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
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingTokenDefinition != null) {
            createTokenFromDraggedTokenDefinition(mouseX, mouseY, draggingTokenDefinition);
            this.draggingTokenDefinition = null;
            return true;
        }

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
        if (draggingTokenDefinition != null || draggingAsset != null) {
            return true;
        }

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
        if (isRenaming()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirmRename();
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelRename();
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!renameBuffer.isEmpty()) {
                    renameBuffer = renameBuffer.substring(0, renameBuffer.length() - 1);
                }

                return true;
            }

            return true;
        }

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

        if (keyCode == GLFW.GLFW_KEY_R) {
            inputController.resetSelectedObjectsScaleAndRotation();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_N) {
            beginRenameSelectedObject();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            inputController.deleteSelectedObjects();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_D && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            inputController.duplicateSelectedObjects();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            inputController.bringSelectedObjectsForward();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            inputController.sendSelectedObjectsBackward();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_HOME) {
            inputController.bringSelectedObjectsToFront();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_END) {
            inputController.sendSelectedObjectsToBack();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_V) {
            inputController.toggleSelectedObjectsVisibility();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_1 || keyCode == GLFW.GLFW_KEY_KP_1) {
            inputController.setSelectedObjectsActiveState("1");
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_2 || keyCode == GLFW.GLFW_KEY_KP_2) {
            inputController.setSelectedObjectsActiveState("2");
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_3 || keyCode == GLFW.GLFW_KEY_KP_3) {
            inputController.setSelectedObjectsActiveState("3");
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
    public boolean charTyped(char codePoint, int modifiers) {
        if (isRenaming()) {
            if (isAllowedRenameCharacter(codePoint) && renameBuffer.length() < 48) {
                renameBuffer += codePoint;
            }

            return true;
        }

        return super.charTyped(codePoint, modifiers);
    }

    private boolean isAllowedRenameCharacter(char character) {
        return character >= 32 && character != 127;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}