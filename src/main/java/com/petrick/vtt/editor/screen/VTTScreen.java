package com.petrick.vtt.editor.screen;

import com.petrick.vtt.VTT;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.core.render.RenderState;
import com.petrick.vtt.core.session.VTTSession;
import com.petrick.vtt.core.session.VttRole;
import com.petrick.vtt.editor.catalog.AssetCatalogController;
import com.petrick.vtt.editor.catalog.AssetCatalogItem;
import com.petrick.vtt.editor.catalog.AssetCatalogSelection;
import com.petrick.vtt.editor.catalog.AssetCatalogVisibleRow;
import com.petrick.vtt.editor.catalog.TokenCatalogClickResult;
import com.petrick.vtt.editor.catalog.TokenCatalogController;
import com.petrick.vtt.editor.catalog.TokenCatalogSelection;
import com.petrick.vtt.editor.dialog.TokenCreationDialog;
import com.petrick.vtt.editor.input.InputController;
import com.petrick.vtt.editor.overlay.AssetCatalogOverlay;
import com.petrick.vtt.editor.overlay.DebugOverlay;
import com.petrick.vtt.editor.overlay.HelpOverlay;
import com.petrick.vtt.editor.overlay.SceneOutlinerOverlay;
import com.petrick.vtt.editor.overlay.SceneListOverlay;
import com.petrick.vtt.editor.overlay.SelectionInspectorOverlay;
import com.petrick.vtt.editor.overlay.TokenCatalogOverlay;
import com.petrick.vtt.editor.panel.EditorPanelVisibility;
import com.petrick.vtt.editor.placement.TokenPlacementService;
import com.petrick.vtt.editor.token.TokenCreationDraft;
import com.petrick.vtt.feature.asset.library.AssetLibraryFileType;
import com.petrick.vtt.feature.token.persistence.CreatedTokenStorage;
import com.petrick.vtt.feature.token.CreatedTokenDefinitions;
import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.feature.asset.BuiltInTextureAssetRef;
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnail;
import com.petrick.vtt.feature.camera.Camera2D;
import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasRenderer;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.feature.selection.SelectionManager;
import com.petrick.vtt.feature.token.TokenDefinition;
import com.petrick.vtt.feature.token.TokenDefinitionRegistry;
import com.petrick.vtt.editor.catalog.TokenCatalogContextMenu;
import com.petrick.vtt.editor.overlay.TokenCatalogContextMenuOverlay;
import com.petrick.vtt.feature.token.TokenDefinition;
import com.petrick.vtt.feature.viewport.Viewport;
import com.petrick.vtt.platform.client.CursorManager;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

/**
 * Tela principal do Virtual Tabletop.
 *
 * Esta Screen assume o controle visual enquanto o VTT está aberto.
 */
public final class VTTScreen extends Screen {

    private final VTTSession session;

    private final Camera2D camera;

    private final AssetRegistry assetRegistry;

    private final TokenDefinitionRegistry tokenDefinitionRegistry;

    private final CanvasScene scene;

    private final SelectionManager selectionManager;

    private final CanvasRenderer canvasRenderer;

    private final InputController inputController;

    private final EditorPanelVisibility panelVisibility;

    private final TokenPlacementService tokenPlacementService;

    private final DebugOverlay debugOverlay;

    private final HelpOverlay helpOverlay;

    private final SelectionInspectorOverlay selectionInspectorOverlay;

    private final AssetCatalogOverlay assetCatalogOverlay;

    private final AssetCatalogSelection assetCatalogSelection;

    private final AssetCatalogController assetCatalogController;

    private final TokenCatalogOverlay tokenCatalogOverlay;

    private final TokenCatalogSelection tokenCatalogSelection;

    private final TokenCatalogController tokenCatalogController;

    private final SceneOutlinerOverlay sceneOutlinerOverlay;

    private final SceneListOverlay sceneListOverlay;

    private final TokenCreationDialog tokenCreationDialog;

    private final TokenCatalogContextMenu tokenCatalogContextMenu = new TokenCatalogContextMenu();

    private final TokenCatalogContextMenuOverlay tokenCatalogContextMenuOverlay = new TokenCatalogContextMenuOverlay();

    private TokenCreationDraft tokenCreationDraft;

    private boolean tokenImagePickerActive;

    private long lastTokenImagePickerClickTime;

    private String lastTokenImagePickerClickedItemId;

    private boolean backgroundImagePickerActive;

    private long lastBackgroundImagePickerClickTime;

    private String lastBackgroundImagePickerClickedItemId;

    private Viewport viewport;

    private RenderState renderState;

    private String renamingObjectId;

    private String renameBuffer;

    private String newSceneNameBuffer;

    public VTTScreen() {
        super(Component.literal("Virtual Tabletop"));

        this.session = VTT.getApplication().getActiveSession();

        this.camera = new Camera2D();
        this.assetRegistry = session.getAssetRegistry();
        this.tokenDefinitionRegistry = session.getTokenDefinitionRegistry();
        this.scene = session.getCanvasScene();

        this.selectionManager = new SelectionManager();
        this.tokenPlacementService = new TokenPlacementService(scene, selectionManager);
        this.canvasRenderer = new CanvasRenderer(
                session.getAnimatedTextureService(), assetRegistry, session.getAssetThumbnailRegistry()
        );
        this.inputController = new InputController(camera, scene, selectionManager,
                session::getActiveScene, session::saveCanvasSceneToActiveScene);

        this.panelVisibility = new EditorPanelVisibility();

        this.debugOverlay = new DebugOverlay();
        this.helpOverlay = new HelpOverlay();
        this.selectionInspectorOverlay = new SelectionInspectorOverlay();

        this.assetCatalogOverlay = new AssetCatalogOverlay();
        this.assetCatalogSelection = new AssetCatalogSelection();
        this.assetCatalogController = new AssetCatalogController(assetCatalogSelection);

        this.tokenCatalogOverlay = new TokenCatalogOverlay();
        this.tokenCatalogSelection = new TokenCatalogSelection();
        this.tokenCatalogController = new TokenCatalogController(tokenCatalogSelection);

        this.sceneOutlinerOverlay = new SceneOutlinerOverlay();
        this.sceneListOverlay = new SceneListOverlay();
        this.tokenCreationDialog = new TokenCreationDialog();
    }

    @Override
    protected void init() {
        this.viewport = Viewport.fullScreen(this.width, this.height);
        this.renderState = new RenderState(camera, viewport);

        session.refreshAssetLibrary();
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
        canvasRenderer.render(context, session.getActiveScene(), scene, selectionManager);
        inputController.renderToolOverlay(context, renderState);
        renderTitle(context);

        if (panelVisibility.isHelpVisible()) {
            helpOverlay.render(context, this.font);
        }

        if (panelVisibility.isDebugVisible()) {
            debugOverlay.render(
                    context,
                    this.font,
                    camera,
                    inputController.getActiveToolId(),
                    assetRegistry.size(),
                    tokenDefinitionRegistry.size(),
                    session.getAssetLibraryScanResult().totalCount(),
                    session.getAssetLibraryScanResult().animatedImageCount()
            );
        }

        if (panelVisibility.isSelectionInspectorVisible()) {
            selectionInspectorOverlay.render(context, this.font, scene, selectionManager);
        }

        if (panelVisibility.isAssetCatalogVisible()) {
            renderAssetCatalog(context);
        }

        if (panelVisibility.isTokenCatalogVisible()) {
            tokenCatalogOverlay.render(
                    context,
                    this.font,
                    tokenDefinitionRegistry,
                    tokenCatalogSelection
            );
        }

        if (panelVisibility.isSceneOutlinerVisible()) {
            sceneOutlinerOverlay.render(context, this.font, scene, selectionManager);
        }

        if (panelVisibility.isSceneListVisible()) {
            sceneListOverlay.render(context, this.font, session.getActiveTabletop(),
                    session.getActiveScene());
        }

        TokenDefinition draggingTokenDefinition =
                tokenCatalogController.getDraggingTokenDefinition();

        if (draggingTokenDefinition != null
                && tokenCatalogController.shouldShowDragPreview(mouseX, mouseY)) {
            tokenCatalogOverlay.renderDragPreview(
                    context,
                    this.font,
                    draggingTokenDefinition,
                    mouseX,
                    mouseY
            );
        }

        if (tokenCreationDraft != null) {
            tokenCreationDialog.render(
                    context,
                    this.font,
                    tokenCreationDraft
            );

            if (tokenImagePickerActive) {
                renderAssetCatalog(context);
            }
        }

        if (backgroundImagePickerActive) {
            renderAssetCatalog(context);
        }

        tokenCatalogContextMenuOverlay.render(
                context,
                this.font,
                tokenCatalogContextMenu
        );

        if (renamingObjectId != null) {
            renderRenameDialog(context);
        }

        if (newSceneNameBuffer != null) {
            renderNewSceneDialog(context);
        }
    }

    private void renderAssetCatalog(VRenderContext context) {
        assetCatalogOverlay.render(
                context,
                this.font,
                assetRegistry,
                session.getAssetLibraryScanResult(),
                session.getAssetThumbnailRegistry(),
                assetCatalogSelection,
                assetCatalogController.getFilter(),
                assetCatalogController.getTreeState(),
                assetCatalogController.getSearchQuery(),
                assetCatalogController.isSearchActive(),
                assetCatalogController.getScrollOffset()
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
                "Sprint 3 - Tabletop, Scenes & Persistence",
                this.width / 2,
                this.height / 2,
                0xFFAAAAAA
        );

        graphics.drawCenteredString(this.font, "Role: " + session.getLocalRole(),
                this.width / 2, this.height / 2 + 16,
                session.isLocalMaster() ? 0xFFFFCC66 : 0xFF66CCFF);
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

        if (tokenCreationDraft != null) {
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
        if (newSceneNameBuffer != null) return true;

        if (handleBackgroundImagePickerMouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (handleTokenCreationMouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (!session.getLocalRole().canEditTabletop()) {
            if (renderState != null) {
                return inputController.mouseClicked(mouseX, mouseY, button,
                        getKeyboardModifiers(), renderState);
            }
            return true;
        }

        if (tokenCatalogContextMenu.isOpen()) {
            TokenCatalogContextMenuOverlay.Action action = tokenCatalogContextMenuOverlay.getActionAt(
                    tokenCatalogContextMenu,
                    mouseX,
                    mouseY
            );

            if (action != TokenCatalogContextMenuOverlay.Action.NONE) {
                handleTokenCatalogContextMenuAction(action);
                tokenCatalogContextMenu.close();
                tokenCatalogOverlay.allowDetailsPopup();
                return true;
            }

            if (!tokenCatalogContextMenuOverlay.containsPoint(
                    tokenCatalogContextMenu,
                    mouseX,
                    mouseY
            )) {
                tokenCatalogContextMenu.close();
                tokenCatalogOverlay.allowDetailsPopup();
                return true;
            }
        }

        if (panelVisibility.isTokenCatalogVisible()
                && tokenCatalogOverlay.isCreateTokenButtonAt(
                tokenDefinitionRegistry,
                this.height,
                mouseX,
                mouseY
        )) {
            tokenCreationDraft = new TokenCreationDraft();
            tokenImagePickerActive = false;
            lastTokenImagePickerClickedItemId = null;
            lastTokenImagePickerClickTime = 0L;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && panelVisibility.isTokenCatalogVisible()) {
            TokenDefinition clickedToken = tokenCatalogOverlay.findTokenAt(
                    tokenDefinitionRegistry,
                    this.height,
                    mouseX,
                    mouseY
            );

            if (clickedToken != null) {
                tokenCatalogSelection.select(clickedToken.id());

                tokenCatalogOverlay.suppressDetailsPopup();

                tokenCatalogContextMenu.open(
                        (int) mouseX + 12,
                        (int) mouseY - 80,
                        clickedToken
                );

                return true;
            }
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            TokenCatalogClickResult tokenCatalogClickResult =
                    tokenCatalogController.mouseClicked(
                            tokenCatalogOverlay,
                            tokenDefinitionRegistry,
                            panelVisibility.isTokenCatalogVisible(),
                            this.height,
                            mouseX,
                            mouseY
                    );

            if (tokenCatalogClickResult.shouldCreateAtCameraCenter()) {
                createTokenFromDraggedTokenDefinition(
                        this.width / 2.0,
                        this.height / 2.0,
                        tokenCatalogClickResult.tokenToCreateAtCameraCenter()
                );

                return true;
            }

            if (tokenCatalogClickResult.consumesClick()) {
                return true;
            }

            boolean assetCatalogConsumedClick =
                    assetCatalogController.mouseClicked(
                            assetCatalogOverlay,
                            assetRegistry,
                            session.getAssetLibraryScanResult(),
                            panelVisibility.isAssetCatalogVisible(),
                            this.height,
                            mouseX,
                            mouseY
                    );

            if (assetCatalogConsumedClick) {
                return true;
            }

            if (panelVisibility.isSceneOutlinerVisible()) {
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

            if (panelVisibility.isSceneListVisible()) {
                if (sceneListOverlay.isCreateSceneButtonAt(
                        session.getActiveTabletop(), mouseX, mouseY)) {
                    newSceneNameBuffer = "";
                    return true;
                }
                Optional<String> clickedSceneId = sceneListOverlay.findSceneIdAt(
                        session.getActiveTabletop(), mouseX, mouseY);
                if (clickedSceneId.isPresent()) {
                    if (session.switchToScene(clickedSceneId.get())) {
                        selectionManager.clearSelection();
                        inputController.selectHandTool();
                    }
                    return true;
                }
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

    private boolean handleBackgroundImagePickerMouseClicked(double mouseX, double mouseY, int button) {
        if (!backgroundImagePickerActive) return false;

        Optional<AssetCatalogVisibleRow> clickedRow = assetCatalogOverlay.findRowAt(
                assetRegistry, session.getAssetLibraryScanResult(), assetCatalogController.getFilter(),
                assetCatalogController.getTreeState(), assetCatalogController.getSearchQuery(),
                this.height, mouseX, mouseY, assetCatalogController.getScrollOffset()
        );

        if (clickedRow.isPresent() && clickedRow.get().isItem()) {
            AssetCatalogItem clickedItem = clickedRow.get().item().catalogItem();
            assetCatalogController.mouseClicked(assetCatalogOverlay, assetRegistry,
                    session.getAssetLibraryScanResult(), true, this.height, mouseX, mouseY);

            long now = System.currentTimeMillis();
            boolean doubleClick = clickedItem.id().equals(lastBackgroundImagePickerClickedItemId)
                    && now - lastBackgroundImagePickerClickTime <= 350L;

            if (isSelectableTokenImage(clickedItem) && doubleClick) {
                applySceneBackgroundSelection(clickedItem);
                closeBackgroundImagePicker();
            } else {
                lastBackgroundImagePickerClickedItemId = clickedItem.id();
                lastBackgroundImagePickerClickTime = now;
            }
            return true;
        }

        assetCatalogController.mouseClicked(assetCatalogOverlay, assetRegistry,
                session.getAssetLibraryScanResult(), true, this.height, mouseX, mouseY);
        return true;
    }

    private void createTokenDefinitionFromDraft() {
        if (tokenCreationDraft == null) {
            return;
        }

        if (!tokenCreationDraft.hasAnyStateImage()) {
            tokenCreationDraft.setErrorMessage("Choose an image first");
            return;
        }

        if (!tokenCreationDraft.allStatesHaveImages()) {
            tokenCreationDraft.setErrorMessage("Choose an image for every state");
            return;
        }

        if (tokenCreationDraft.isEditing()) {
            saveEditedTokenDefinitionFromDraft();
            return;
        }

        TokenDefinition createdDefinition = CreatedTokenDefinitions.createAndRegister(
                tokenCreationDraft,
                tokenDefinitionRegistry,
                assetRegistry
        );

        CreatedTokenStorage.saveCreatedToken(
                tokenCreationDraft,
                createdDefinition
        );

        tokenCatalogSelection.select(createdDefinition.id());

        closeTokenCreationDialog();
    }

    private void saveEditedTokenDefinitionFromDraft() {
        if (tokenCreationDraft == null) {
            return;
        }

        TokenDefinition updatedDefinition = CreatedTokenStorage.updateCreatedToken(
                tokenCreationDraft,
                tokenDefinitionRegistry,
                assetRegistry
        );

        if (updatedDefinition == null) {
            tokenCreationDraft.setErrorMessage("Could not save token");
            return;
        }

        scene.syncObjectsFromTokenDefinition(updatedDefinition);

        tokenCatalogSelection.select(updatedDefinition.id());

        closeTokenCreationDialog();
    }

    private void closeTokenCreationDialog() {
        tokenCreationDraft = null;
        tokenImagePickerActive = false;
        lastTokenImagePickerClickedItemId = null;
        lastTokenImagePickerClickTime = 0L;
    }

    private void handleTokenCatalogContextMenuAction(
            TokenCatalogContextMenuOverlay.Action action
    ) {
        TokenDefinition tokenDefinition = tokenCatalogContextMenu.getTokenDefinition();

        if (tokenDefinition == null) {
            return;
        }

        switch (action) {
            case EDIT -> {
                beginEditTokenDefinition(tokenDefinition);
            }

            case VIEW_IN_EXPLORER -> {
                CreatedTokenStorage.viewCreatedTokenInExplorer(tokenDefinition);
            }

            case DUPLICATE -> {
                duplicateTokenDefinition(tokenDefinition);
            }

            case DELETE -> {
                deleteTokenDefinition(tokenDefinition);
            }

            case NONE -> {
            }
        }
    }

    private void beginEditTokenDefinition(TokenDefinition tokenDefinition) {
        if (tokenDefinition == null) {
            return;
        }

        TokenCreationDraft editDraft = CreatedTokenStorage.createEditDraft(tokenDefinition);

        if (editDraft == null) {
            return;
        }

        tokenCreationDraft = editDraft;
        tokenImagePickerActive = false;
        lastTokenImagePickerClickedItemId = null;
        lastTokenImagePickerClickTime = 0L;
    }

    private void duplicateTokenDefinition(TokenDefinition tokenDefinition) {
        if (tokenDefinition == null) {
            return;
        }

        TokenDefinition duplicatedDefinition = CreatedTokenStorage.duplicateCreatedToken(
                tokenDefinition,
                tokenDefinitionRegistry,
                assetRegistry
        );

        if (duplicatedDefinition == null) {
            return;
        }

        tokenCatalogSelection.select(duplicatedDefinition.id());
    }

    private void deleteTokenDefinition(TokenDefinition tokenDefinition) {
        if (tokenDefinition == null) {
            return;
        }

        if (!CreatedTokenStorage.isUserCreatedToken(tokenDefinition)) {
            System.out.println("Cannot delete built-in/debug token: " + tokenDefinition.id());
            return;
        }

        CreatedTokenStorage.deleteCreatedToken(tokenDefinition);

        tokenDefinitionRegistry.removeById(tokenDefinition.id());

        if (tokenCatalogSelection.isSelected(tokenDefinition.id())) {
            tokenCatalogSelection.clear();
        }
    }

    private boolean handleTokenCreationMouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (tokenCreationDraft == null) {
            return false;
        }

        if (tokenImagePickerActive) {
            Optional<AssetCatalogVisibleRow> clickedRow = assetCatalogOverlay.findRowAt(
                    assetRegistry,
                    session.getAssetLibraryScanResult(),
                    assetCatalogController.getFilter(),
                    assetCatalogController.getTreeState(),
                    assetCatalogController.getSearchQuery(),
                    this.height,
                    mouseX,
                    mouseY,
                    assetCatalogController.getScrollOffset()
            );

            if (clickedRow.isPresent() && clickedRow.get().isItem()) {
                AssetCatalogItem clickedItem = clickedRow.get()
                        .item()
                        .catalogItem();

                assetCatalogController.mouseClicked(
                        assetCatalogOverlay,
                        assetRegistry,
                        session.getAssetLibraryScanResult(),
                        true,
                        this.height,
                        mouseX,
                        mouseY
                );

                if (isSelectableTokenImage(clickedItem)
                        && isTokenImagePickerDoubleClick(clickedItem)) {
                    applyTokenImageSelection(clickedItem);
                    tokenImagePickerActive = false;
                    lastTokenImagePickerClickedItemId = null;
                    lastTokenImagePickerClickTime = 0L;
                } else {
                    rememberTokenImagePickerClick(clickedItem);
                }

                return true;
            }

            if (assetCatalogController.mouseClicked(
                    assetCatalogOverlay,
                    assetRegistry,
                    session.getAssetLibraryScanResult(),
                    true,
                    this.height,
                    mouseX,
                    mouseY
            )) {
                return true;
            }
        }

        TokenCreationDialog.Action action = tokenCreationDialog.mouseClicked(
                this.width,
                this.height,
                tokenCreationDraft,
                mouseX,
                mouseY,
                button
        );

        if (action == TokenCreationDialog.Action.DISCARD) {
            closeTokenCreationDialog();
            return true;
        }

        if (action == TokenCreationDialog.Action.CHOOSE_IMAGE) {
            tokenImagePickerActive = true;
            return true;
        }

        if (action == TokenCreationDialog.Action.CREATE) {
            createTokenDefinitionFromDraft();
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (backgroundImagePickerActive) {
            return true;
        }

        if (tokenCreationDraft != null) {
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            TokenDefinition releasedTokenDefinition =
                    tokenCatalogController.mouseReleased(mouseX, mouseY);

            if (releasedTokenDefinition != null) {
                createTokenFromDraggedTokenDefinition(
                        mouseX,
                        mouseY,
                        releasedTokenDefinition
                );

                return true;
            }
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
        if (backgroundImagePickerActive) {
            return true;
        }

        if (tokenCreationDraft != null) {
            return true;
        }

        if (tokenCatalogController.getDraggingTokenDefinition() != null) {
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
        if (backgroundImagePickerActive) {
            assetCatalogController.mouseScrolled(assetCatalogOverlay, assetRegistry,
                    session.getAssetLibraryScanResult(), true, this.height, mouseX, mouseY, scrollY);
            return true;
        }

        if (tokenCreationDraft != null && tokenImagePickerActive) {
            if (assetCatalogController.mouseScrolled(
                    assetCatalogOverlay,
                    assetRegistry,
                    session.getAssetLibraryScanResult(),
                    true,
                    this.height,
                    mouseX,
                    mouseY,
                    scrollY
            )) {
                return true;
            }

            return true;
        }

        if (tokenCreationDraft != null) {
            if (tokenCreationDialog.mouseScrolled(
                    tokenCreationDraft,
                    mouseX,
                    mouseY,
                    scrollY,
                    this.width,
                    this.height
            )) {
                return true;
            }

            return true;
        }

        if (assetCatalogController.mouseScrolled(
                assetCatalogOverlay,
                assetRegistry,
                session.getAssetLibraryScanResult(),
                panelVisibility.isAssetCatalogVisible(),
                this.height,
                mouseX,
                mouseY,
                scrollY
        )) {
            return true;
        }

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
        if (newSceneNameBuffer != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirmNewScene();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                newSceneNameBuffer = null;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !newSceneNameBuffer.isEmpty()) {
                newSceneNameBuffer = newSceneNameBuffer.substring(0, newSceneNameBuffer.length() - 1);
            }
            return true;
        }

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

        if (tokenCatalogContextMenu.isOpen() && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            tokenCatalogContextMenu.close();
            tokenCatalogOverlay.allowDetailsPopup();
            return true;
        }

        if (backgroundImagePickerActive) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeBackgroundImagePicker();
                return true;
            }
            if (assetCatalogController.keyPressed(keyCode, getKeyboardModifiers())) return true;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE && inputController.cancelWallDrawing()) {
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_M
                && (getKeyboardModifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            toggleLocalRole();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_F1) {
            panelVisibility.toggleHelp();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_F2) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            panelVisibility.toggleSceneList();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_F3) {
            panelVisibility.toggleDebug();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_F4) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            panelVisibility.toggleSelectionInspector();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_F5) {
            if (!session.getLocalRole().canUseCatalogs()) return true;
            if ((getKeyboardModifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
                assetCatalogController.cycleFilter();
            } else {
                panelVisibility.toggleAssetCatalog();
            }

            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_F6) {
            if (!session.getLocalRole().canUseCatalogs()) return true;
            panelVisibility.toggleTokenCatalog();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_F7) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            panelVisibility.toggleSceneOutliner();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_F9) {
            panelVisibility.hideAllEditorPanels();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_F10
                && (getKeyboardModifiers() & GLFW.GLFW_MOD_SHIFT) != 0) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            session.loadActiveSceneToCanvasScene();
            selectionManager.clearSelection();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_F10) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            panelVisibility.showAllEditorPanels();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_F12) {
            session.refreshAssetLibrary();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_B) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            backgroundImagePickerActive = true;
            assetCatalogSelection.clear();
            lastBackgroundImagePickerClickedItemId = null;
            lastBackgroundImagePickerClickTime = 0L;
            VTT.LOGGER.info("[VTT Background] Background image picker opened");
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_G) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            session.saveCanvasSceneToActiveScene();
            return true;
        }

        if (tokenCreationDraft != null) {
            if (tokenImagePickerActive) {
                if (assetCatalogController.keyPressed(keyCode, getKeyboardModifiers())) {
                    return true;
                }

                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    tokenImagePickerActive = false;
                    return true;
                }
            }

            if (tokenCreationDialog.keyPressed(tokenCreationDraft, keyCode)) {
                return true;
            }
        }

        if (assetCatalogController.keyPressed(keyCode, getKeyboardModifiers())) {
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (!session.getLocalRole().canUseCatalogs()) return true;
            createSelectedTokenAtCameraCenter();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_H) {
            inputController.selectHandTool();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_S) {
            if (!session.getLocalRole().canMoveAnyToken()) return true;
            inputController.selectSelectTool();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_W) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            selectionManager.clearSelection();
            inputController.selectWallTool();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            if (inputController.scaleSelectedWall(1.1)) return true;
            inputController.scaleSelectedObjectsUp();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            if (inputController.scaleSelectedWall(0.9)) return true;
            inputController.scaleSelectedObjectsDown();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_Q) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            if (inputController.rotateSelectedWall(-15.0)) return true;
            inputController.rotateSelectedObjectsLeft();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_E) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            if (inputController.rotateSelectedWall(15.0)) return true;
            inputController.rotateSelectedObjectsRight();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_F) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            inputController.flipSelectedObjectsHorizontally();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_R) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            if (inputController.resetSelectedWallTransform()) return true;
            inputController.resetSelectedObjectsScaleAndRotation();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_N) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            beginRenameSelectedObject();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            if (inputController.deleteSelectedWall()) return true;
            inputController.deleteSelectedObjects();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_D
                && (getKeyboardModifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            inputController.duplicateSelectedObjects();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            inputController.bringSelectedObjectsForward();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            inputController.sendSelectedObjectsBackward();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_HOME) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            inputController.bringSelectedObjectsToFront();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_END) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            inputController.sendSelectedObjectsToBack();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_V) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            inputController.toggleSelectedObjectsVisibility();
            return true;
        }

        String requestedStateId = getStateIdFromNumberKey(keyCode);

        if (requestedStateId != null) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            inputController.setSelectedObjectsActiveState(requestedStateId);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void toggleLocalRole() {
        VttRole nextRole = session.isLocalMaster() ? VttRole.PLAYER : VttRole.MASTER;
        session.setLocalRole(nextRole);
        if (nextRole == VttRole.PLAYER) {
            closeBackgroundImagePicker();
            closeTokenCreationDialog();
            tokenCatalogContextMenu.close();
            panelVisibility.hideMasterPanels();
            selectionManager.clearSelection();
            inputController.selectHandTool();
        }
        VTT.LOGGER.info("Local VTT role changed to {}", nextRole);
    }

    private void applySceneBackgroundSelection(AssetCatalogItem item) {
        if (session.getActiveScene() == null) {
            VTT.LOGGER.warn("[VTT Background] There is no active scene");
            return;
        }
        session.getActiveScene().setBackgroundAssetId(item.id());
        session.saveActiveTabletopAndScene();
        VTT.LOGGER.info("[VTT Background] Background saved for scene {}: {}",
                session.getActiveScene().getId(), item.id());
    }

    private void closeBackgroundImagePicker() {
        backgroundImagePickerActive = false;
        lastBackgroundImagePickerClickedItemId = null;
        lastBackgroundImagePickerClickTime = 0L;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (newSceneNameBuffer != null) {
            if (isAllowedRenameCharacter(codePoint) && newSceneNameBuffer.length() < 48) {
                newSceneNameBuffer += codePoint;
            }
            return true;
        }

        if (backgroundImagePickerActive) {
            assetCatalogController.charTyped(codePoint);
            return true;
        }

        if (isRenaming()) {
            if (isAllowedRenameCharacter(codePoint) && renameBuffer.length() < 48) {
                renameBuffer += codePoint;
            }

            return true;
        }

        if (tokenCreationDraft != null && tokenImagePickerActive) {
            if (assetCatalogController.charTyped(codePoint)) {
                return true;
            }
        }

        if (tokenCreationDraft != null) {
            if (tokenCreationDialog.charTyped(tokenCreationDraft, codePoint)) {
                return true;
            }
        }

        if (assetCatalogController.charTyped(codePoint)) {
            return true;
        }

        return super.charTyped(codePoint, modifiers);
    }

    private String getStateIdFromNumberKey(int keyCode) {
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            int number = keyCode - GLFW.GLFW_KEY_1 + 1;
            return Integer.toString(number);
        }

        if (keyCode >= GLFW.GLFW_KEY_KP_1 && keyCode <= GLFW.GLFW_KEY_KP_9) {
            int number = keyCode - GLFW.GLFW_KEY_KP_1 + 1;
            return Integer.toString(number);
        }

        return null;
    }

    private void createTokenFromDraggedTokenDefinition(
            double mouseX,
            double mouseY,
            TokenDefinition definition
    ) {
        createTokenAtScreenPosition(mouseX, mouseY, definition);
    }

    private void createSelectedTokenAtCameraCenter() {
        if (!tokenCatalogSelection.hasSelection()) {
            return;
        }

        TokenDefinition definition = tokenDefinitionRegistry
                .findById(tokenCatalogSelection.getSelectedTokenDefinitionId())
                .orElse(null);

        if (definition == null) {
            return;
        }

        createTokenAtScreenPosition(
                this.width / 2.0,
                this.height / 2.0,
                definition
        );
    }

    private void createTokenAtScreenPosition(
            double screenX,
            double screenY,
            TokenDefinition definition
    ) {
        if (renderState == null) {
            return;
        }

        Vec2d worldPosition = renderState.screenToWorld(
                new Vec2d(screenX, screenY)
        );

        createTokenAtWorldPosition(worldPosition, definition);
    }

    private void createTokenAtWorldPosition(
            Vec2d worldPosition,
            TokenDefinition definition
    ) {
        if (definition == null) {
            return;
        }

        tokenPlacementService.placeToken(definition, worldPosition);
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

    private boolean isAllowedRenameCharacter(char character) {
        return character >= 32 && character != 127;
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

    private void confirmNewScene() {
        if (newSceneNameBuffer == null || newSceneNameBuffer.isBlank()) return;
        if (session.createScene(newSceneNameBuffer) != null) {
            selectionManager.clearSelection();
            inputController.selectHandTool();
            newSceneNameBuffer = null;
        }
    }

    private void renderNewSceneDialog(VRenderContext context) {
        int width = 300;
        int height = 70;
        int x = context.screenWidth() / 2 - width / 2;
        int y = context.screenHeight() / 2 - height / 2;
        context.graphics().fill(x, y, x + width, y + height, 0xEE000000);
        context.graphics().hLine(x, x + width, y, 0xFFFFAA44);
        context.graphics().hLine(x, x + width, y + height, 0xFFFFAA44);
        context.graphics().vLine(x, y, y + height, 0xFFFFAA44);
        context.graphics().vLine(x + width, y, y + height, 0xFFFFAA44);
        context.graphics().drawString(this.font, "Create Scene", x + 10, y + 10, 0xFFFFFFFF, false);
        context.graphics().drawString(this.font, newSceneNameBuffer + "_", x + 10, y + 28, 0xFFFFFFFF, false);
        context.graphics().drawString(this.font, "Enter: create   Esc: cancel",
                x + 10, y + 48, 0xFFAAAAAA, false);
    }

    private boolean isSelectableTokenImage(AssetCatalogItem item) {
        if (item instanceof AssetCatalogItem.RegisteredAsset registeredAsset) {
            return registeredAsset.assetRef() instanceof BuiltInTextureAssetRef;
        }

        if (item instanceof AssetCatalogItem.LibraryFile libraryFile) {
            return session.getAssetThumbnailRegistry()
                    .findById(libraryFile.entry().id())
                    .isPresent();
        }

        return false;
    }

    private boolean isTokenImagePickerDoubleClick(AssetCatalogItem item) {
        long now = System.currentTimeMillis();

        return item.id().equals(lastTokenImagePickerClickedItemId)
                && now - lastTokenImagePickerClickTime <= 350L;
    }

    private void rememberTokenImagePickerClick(AssetCatalogItem item) {
        lastTokenImagePickerClickedItemId = item.id();
        lastTokenImagePickerClickTime = System.currentTimeMillis();
    }

    private void applyTokenImageSelection(AssetCatalogItem item) {
        if (tokenCreationDraft == null) {
            return;
        }

        if (item instanceof AssetCatalogItem.RegisteredAsset registeredAsset
                && registeredAsset.assetRef() instanceof BuiltInTextureAssetRef builtInTexture) {
            tokenCreationDraft.selectImage(
                    item.id(),
                    item.displayName(),
                    builtInTexture.texture(),
                    builtInTexture.textureWidth(),
                    builtInTexture.textureHeight(),
                    AssetLibraryFileType.IMAGE
            );

            return;
        }

        if (item instanceof AssetCatalogItem.LibraryFile libraryFile) {
            AssetThumbnail thumbnail = session.getAssetThumbnailRegistry()
                    .findById(libraryFile.entry().id())
                    .orElse(null);

            if (thumbnail == null) {
                return;
            }

            tokenCreationDraft.selectImage(
                    item.id(),
                    item.displayName(),
                    thumbnail.texture(),
                    thumbnail.width(),
                    thumbnail.height(),
                    libraryFile.entry().fileType()
            );
        }
    }

    @Override
    public void removed() {
        inputController.cancelWallDrawing();
        CursorManager.reset();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
