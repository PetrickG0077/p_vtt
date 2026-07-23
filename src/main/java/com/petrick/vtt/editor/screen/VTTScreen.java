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
import com.petrick.vtt.editor.hud.EditorHudOverlay;
import com.petrick.vtt.editor.hud.EditorSettingsOverlay;
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
import com.petrick.vtt.editor.token.VttPlayerOption;
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
import com.petrick.vtt.editor.catalog.SceneContextMenu;
import com.petrick.vtt.editor.overlay.TokenCatalogContextMenuOverlay;
import com.petrick.vtt.editor.overlay.SceneContextMenuOverlay;
import com.petrick.vtt.feature.viewport.Viewport;
import com.petrick.vtt.platform.client.CursorManager;
import com.petrick.vtt.platform.client.VttAssetSyncHudOverlay;
import com.petrick.vtt.network.client.VttClientEditorNotice;
import com.petrick.vtt.platform.render.VRenderContext;
import com.petrick.vtt.network.client.VttClientTokenDefinitionSync;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Tela principal do Virtual Tabletop.
 *
 * Esta Screen assume o controle visual enquanto o VTT está aberto.
 */
public final class VTTScreen extends Screen {

    private static final double DEFAULT_TOKEN_VISION_OUTER_RADIUS = 512.0;
    private static final double DEFAULT_TOKEN_VISION_INNER_RADIUS = 256.0;
    private static final double TOKEN_VISION_OUTER_STEP = 64.0;
    private static final double TOKEN_VISION_INNER_STEP = 16.0;

    private final VTTSession session;

    private final Camera2D camera;

    private final AssetRegistry assetRegistry;

    private final TokenDefinitionRegistry tokenDefinitionRegistry;

    private final CanvasScene scene;

    private final SelectionManager selectionManager;

    private final CanvasRenderer canvasRenderer;

    private final InputController inputController;

    private final EditorHudOverlay editorHudOverlay;

    private final EditorSettingsOverlay editorSettingsOverlay;

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

    private final SceneContextMenu sceneContextMenu = new SceneContextMenu();

    private final SceneContextMenuOverlay sceneContextMenuOverlay = new SceneContextMenuOverlay();

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

    private String renamingSceneId;

    private String sceneRenameBuffer;

    private String pendingDeleteSceneId;

    private boolean playerViewPreview;

    private boolean hudPlayersOpen;

    private boolean hudSettingsOpen;

    private boolean hudCreationOpen;

    private String observedActiveSceneId;

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
                session::getActiveScene, session::saveCanvasSceneToActiveScene,
                session::getLocalRole, session::getLocalPlayerId);
        this.editorHudOverlay = new EditorHudOverlay();
        this.editorSettingsOverlay = new EditorSettingsOverlay();

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
        this.observedActiveSceneId = session.getActiveScene() == null
                ? null : session.getActiveScene().getId();
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
        handleActiveSceneChange();
        selectionManager.removeMissingObjects(scene);

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
        canvasRenderer.render(context, session.getActiveScene(), scene, selectionManager,
                isMasterView(), !inputController.isEditingCollisionBox(), session.isLocalMaster(), panelVisibility.isDebugVisible(),
                session.isLocalMaster() ? null : session.getLocalPlayerId(),
                !session.isLocalMaster() && session.isNetworkAuthorityActive()
                        ? session.getNetworkVisionRegions() : null,
                session.shouldMaskWhenNetworkVisionEmpty(),
                !session.isLocalMaster() && session.isNetworkAuthorityActive()
                        ? session.getNetworkVisibleObjectIds() : null);
        if (session.isLocalMaster()) inputController.renderToolOverlay(context, renderState);
        renderTitle(context);

        if (playerViewPreview) {
            renderEditorNotice(context);
            renderEditorHud(context);
            VttAssetSyncHudOverlay.render(graphics);
            return;
        }

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
                    session.getAssetLibraryScanResult().animatedImageCount(),
                    session.getNetworkReplicationDiagnostics(),
                    session.getNetworkRecoveryDiagnostics()
            );
        }

        if (session.isLocalMaster() && panelVisibility.isSelectionInspectorVisible()) {
            selectionInspectorOverlay.render(context, this.font, scene, selectionManager,
                    session.getActiveScene(), session.getLocalPlayerId());
        }

        if (session.getLocalRole().canUseCatalogs() && panelVisibility.isAssetCatalogVisible()) {
            renderAssetCatalog(context);
        }

        if (session.getLocalRole().canUseCatalogs() && panelVisibility.isTokenCatalogVisible()) {
            tokenCatalogOverlay.render(
                    context,
                    this.font,
                    tokenDefinitionRegistry,
                    tokenCatalogSelection,
                    tokenCatalogController.getScrollOffset()
            );
        }

        if (session.isLocalMaster() && panelVisibility.isSceneOutlinerVisible()) {
            sceneOutlinerOverlay.render(context, this.font, scene, selectionManager);
        }

        if (session.isLocalMaster() && panelVisibility.isSceneListVisible()) {
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
                    tokenCreationDraft,
                    getConnectedPlayerOptions()
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
        sceneContextMenuOverlay.render(context, this.font, sceneContextMenu);
        renderEditorNotice(context);
        renderEditorHud(context);

        if (renamingObjectId != null) {
            renderRenameDialog(context);
        }

        if (newSceneNameBuffer != null) {
            renderNewSceneDialog(context);
        }
        if (renamingSceneId != null) renderSceneRenameDialog(context);
        if (pendingDeleteSceneId != null) renderDeleteSceneConfirmation(context);
        VttAssetSyncHudOverlay.render(graphics);
    }

    private void renderEditorNotice(VRenderContext context) {
        String message = VttClientEditorNotice.current();
        if (message == null) return;
        int textWidth = this.font.width(message);
        int x = Math.max(4, (this.width - textWidth) / 2 - 6);
        int width = Math.min(this.width - x - 4, textWidth + 12);
        context.graphics().fill(x, 50, x + width, 66, 0xDD220000);
        context.graphics().drawCenteredString(
                this.font, message, this.width / 2, 54, 0xFFFF7777);
    }

    private void renderEditorHud(VRenderContext context) {
        boolean master = session.isLocalMaster();
        if (!master) {
            hudCreationOpen = false;
            panelVisibility.hideMasterPanels();
            String activeTool = inputController.getActiveToolId();
            if (!"hand".equals(activeTool) && !"select".equals(activeTool)) {
                inputController.selectHandTool();
            }
        }
        editorHudOverlay.render(context, this.font, editorHudState());
        if (hudSettingsOpen && session.getActiveScene() != null) {
            editorSettingsOverlay.render(
                    context, this.font, session.getActiveScene().getGrid(), master);
        }
    }

    private EditorHudOverlay.State editorHudState() {
        TokenDefinition selectedToken = selectedTokenDefinition();
        boolean canDeleteToken = CreatedTokenStorage.isUserCreatedToken(selectedToken);
        String activeSceneName = session.getActiveScene() == null
                ? "" : session.getActiveScene().getDisplayName();
        boolean canDeleteScene = session.getActiveTabletop() != null
                && session.getActiveTabletop().getSceneIds().size() > 1
                && session.getActiveScene() != null;
        return new EditorHudOverlay.State(
                session.isLocalMaster(), inputController.getActiveToolId(),
                hudPlayersOpen, hudSettingsOpen, hudCreationOpen,
                panelVisibility.isSceneListVisible(), panelVisibility.isTokenCatalogVisible(),
                panelVisibility.isSceneOutlinerVisible(), getConnectedPlayerOptions(),
                session.getLocalPlayerId(), activeSceneName, canDeleteScene,
                selectedToken == null ? "" : selectedToken.displayName(), canDeleteToken);
    }

    private TokenDefinition selectedTokenDefinition() {
        if (!tokenCatalogSelection.hasSelection()) return null;
        return tokenDefinitionRegistry.findById(
                tokenCatalogSelection.getSelectedTokenDefinitionId()).orElse(null);
    }

    private boolean handleEditorHudMouseClicked(double mouseX, double mouseY, int button) {
        EditorHudOverlay.State state = editorHudState();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return editorHudOverlay.containsHud(mouseX, mouseY, width, height, state);
        }
        EditorHudOverlay.Action action = editorHudOverlay.actionAt(
                mouseX, mouseY, width, height, state);
        if (action == EditorHudOverlay.Action.NONE) {
            boolean insideHud = editorHudOverlay.containsHud(mouseX, mouseY, width, height, state);
            if (!insideHud) closeHudPopups();
            return insideHud;
        }
        handleEditorHudAction(action);
        return true;
    }

    private boolean handleEditorSettingsMouseClicked(
            double mouseX, double mouseY, int button
    ) {
        if (!hudSettingsOpen || session.getActiveScene() == null) return false;
        EditorSettingsOverlay.Interaction interaction = editorSettingsOverlay.mouseClicked(
                mouseX, mouseY, button, width, height,
                session.getActiveScene().getGrid(), session.isLocalMaster());
        if (interaction == EditorSettingsOverlay.Interaction.CHANGED) {
            persistGridSettings();
        }
        return interaction != EditorSettingsOverlay.Interaction.NONE;
    }

    private void persistGridSettings() {
        if (!session.isLocalMaster() || session.getActiveScene() == null) return;
        session.getActiveScene().getGrid().normalize();
        session.saveActiveTabletopAndScene();
    }

    private void handleEditorHudAction(EditorHudOverlay.Action action) {
        boolean master = session.isLocalMaster();
        tokenCatalogContextMenu.close();
        tokenCatalogOverlay.allowDetailsPopup();
        sceneContextMenu.close();
        switch (action) {
            case CLOSE -> this.onClose();
            case PLAYERS -> {
                hudPlayersOpen = !hudPlayersOpen;
                hudSettingsOpen = false;
                hudCreationOpen = false;
            }
            case SETTINGS -> {
                boolean closing = hudSettingsOpen;
                hudSettingsOpen = !hudSettingsOpen;
                hudPlayersOpen = false;
                hudCreationOpen = false;
                if (closing) persistGridSettings();
            }
            case OUTLINER -> {
                if (master) panelVisibility.toggleSceneOutliner();
                closeHudPopups();
            }
            case HAND -> {
                inputController.selectHandTool();
                closeHudPopups();
            }
            case SELECT -> {
                inputController.selectSelectTool();
                closeHudPopups();
            }
            case FOG -> {
                if (master) {
                    selectionManager.clearSelection();
                    inputController.selectFogTool();
                }
                closeHudPopups();
            }
            case WALL -> {
                if (master) {
                    selectionManager.clearSelection();
                    inputController.selectWallTool();
                }
                closeHudPopups();
            }
            case DOOR -> {
                if (master) {
                    selectionManager.clearSelection();
                    inputController.selectDoorTool();
                }
                closeHudPopups();
            }
            case MEASURE -> {
                if (master) {
                    selectionManager.clearSelection();
                    inputController.selectMeasureTool();
                }
                closeHudPopups();
            }
            case UNDO -> VttClientEditorNotice.show("Undo history will be added later");
            case REDO -> VttClientEditorNotice.show("Redo history will be added later");
            case SCENES -> {
                if (master) panelVisibility.toggleSceneList();
                closeHudPopups();
            }
            case TOKENS -> {
                if (master) panelVisibility.toggleTokenCatalog();
                closeHudPopups();
            }
            case CREATION -> {
                if (master) {
                    hudCreationOpen = !hudCreationOpen;
                    hudPlayersOpen = false;
                    hudSettingsOpen = false;
                }
            }
            case CREATE_SCENE -> {
                if (master) newSceneNameBuffer = "";
                closeHudPopups();
            }
            case CREATE_TOKEN -> {
                if (master) beginCreateTokenDefinition();
                closeHudPopups();
            }
            case DELETE_ACTIVE_SCENE -> {
                if (master && session.getActiveScene() != null
                        && session.getActiveTabletop() != null
                        && session.getActiveTabletop().getSceneIds().size() > 1) {
                    pendingDeleteSceneId = session.getActiveScene().getId();
                } else {
                    VttClientEditorNotice.show("At least one scene must remain");
                }
                closeHudPopups();
            }
            case DELETE_SELECTED_TOKEN -> {
                if (master) deleteSelectedTokenDefinitionFromHud();
                closeHudPopups();
            }
            case NONE -> {
            }
        }
    }

    private void beginCreateTokenDefinition() {
        tokenCreationDraft = new TokenCreationDraft();
        tokenImagePickerActive = false;
        lastTokenImagePickerClickedItemId = null;
        lastTokenImagePickerClickTime = 0L;
    }

    private void deleteSelectedTokenDefinitionFromHud() {
        TokenDefinition tokenDefinition = selectedTokenDefinition();
        if (tokenDefinition == null) {
            VttClientEditorNotice.show("Select a token in the token list first");
            return;
        }
        if (!CreatedTokenStorage.isUserCreatedToken(tokenDefinition)) {
            VttClientEditorNotice.show("Built-in tokens cannot be deleted");
            return;
        }
        if (session.isNetworkAuthorityActive()) {
            if (VttClientTokenDefinitionSync.sendDelete(tokenDefinition.id())) {
                tokenCatalogSelection.clear();
            }
            return;
        }
        deleteTokenDefinition(tokenDefinition);
    }

    private void closeHudPopups() {
        hudPlayersOpen = false;
        hudSettingsOpen = false;
        hudCreationOpen = false;
        editorSettingsOverlay.cancelDrag();
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

        if (playerViewPreview) {
            graphics.drawCenteredString(this.font, "PLAYER PREVIEW - Ctrl+P to exit",
                    this.width / 2, 70, 0xFFFF6666);
        }
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

        if (editorHudOverlay.containsHud(mouseX, mouseY, this.width, this.height,
                editorHudState())) {
            CursorManager.reset();
            return;
        }
        if (hudSettingsOpen && editorSettingsOverlay.contains(
                mouseX, mouseY, this.width, this.height)) {
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
        if (playerViewPreview) {
            if (handleEditorSettingsMouseClicked(mouseX, mouseY, button)) return true;
            if (handleEditorHudMouseClicked(mouseX, mouseY, button)) return true;
            return renderState == null || inputController.mouseClicked(
                    mouseX, mouseY, button, getKeyboardModifiers(), renderState);
        }
        if (newSceneNameBuffer != null || renamingSceneId != null || pendingDeleteSceneId != null) return true;

        if (handleBackgroundImagePickerMouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (handleTokenCreationMouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (handleEditorSettingsMouseClicked(mouseX, mouseY, button)) return true;
        if (handleEditorHudMouseClicked(mouseX, mouseY, button)) return true;

        if (!session.getLocalRole().canEditTabletop()) {
            if (renderState != null) {
                return inputController.mouseClicked(mouseX, mouseY, button,
                        getKeyboardModifiers(), renderState);
            }
            return true;
        }

        if (sceneContextMenu.isOpen()) {
            SceneContextMenuOverlay.Action action = sceneContextMenuOverlay.getActionAt(
                    sceneContextMenu, mouseX, mouseY);
            if (action != SceneContextMenuOverlay.Action.NONE) {
                handleSceneContextMenuAction(action);
                return true;
            }
            if (!sceneContextMenuOverlay.containsPoint(sceneContextMenu, mouseX, mouseY)) {
                sceneContextMenu.close();
                return true;
            }
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

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && panelVisibility.isSceneListVisible()) {
            Optional<String> clickedSceneId = sceneListOverlay.findSceneIdAt(
                    session.getActiveTabletop(), mouseX, mouseY);
            if (clickedSceneId.isPresent()) {
                tokenCatalogContextMenu.close();
                sceneContextMenu.open((int) mouseX + 8, (int) mouseY, clickedSceneId.get());
                return true;
            }
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && panelVisibility.isTokenCatalogVisible()) {
            TokenDefinition clickedToken = tokenCatalogOverlay.findTokenAt(
                    tokenDefinitionRegistry,
                    this.height,
                    mouseX,
                    mouseY,
                    tokenCatalogController.getScrollOffset()
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
                if (sceneOutlinerOverlay.mouseClickedScrollbar(scene, mouseX, mouseY)) {
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

            if (panelVisibility.isSceneListVisible()) {
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

        if (assetCatalogOverlay.isScrollbarAt(assetRegistry, session.getAssetLibraryScanResult(),
                assetCatalogController.getFilter(), assetCatalogController.getTreeState(),
                assetCatalogController.getSearchQuery(), this.height, mouseX, mouseY)) {
            assetCatalogController.mouseClicked(assetCatalogOverlay, assetRegistry,
                    session.getAssetLibraryScanResult(), true, this.height, mouseX, mouseY);
            return true;
        }

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

            if (isSelectableBackgroundImage(clickedItem) && doubleClick) {
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

        if (session.isNetworkAuthorityActive()) {
            if (!VttClientTokenDefinitionSync.sendUpsert(
                    CreatedTokenStorage.serializeCreatedToken(tokenCreationDraft, createdDefinition))) {
                tokenCreationDraft.setErrorMessage("Could not send token to server");
                return;
            }
        } else {
            CreatedTokenStorage.saveCreatedToken(tokenCreationDraft, createdDefinition);
        }

        tokenCatalogSelection.select(createdDefinition.id());

        closeTokenCreationDialog();
    }

    private void saveEditedTokenDefinitionFromDraft() {
        if (tokenCreationDraft == null) {
            return;
        }

        TokenDefinition updatedDefinition = session.isNetworkAuthorityActive()
                ? CreatedTokenStorage.updateCreatedTokenInMemory(
                        tokenCreationDraft, tokenDefinitionRegistry, assetRegistry)
                : CreatedTokenStorage.updateCreatedToken(
                        tokenCreationDraft, tokenDefinitionRegistry, assetRegistry);

        if (updatedDefinition == null) {
            tokenCreationDraft.setErrorMessage("Could not save token");
            return;
        }

        scene.syncObjectsFromTokenDefinition(updatedDefinition);

        if (session.isNetworkAuthorityActive()) {
            if (!VttClientTokenDefinitionSync.sendUpsert(
                    CreatedTokenStorage.serializeEditedToken(tokenCreationDraft))) {
                tokenCreationDraft.setErrorMessage("Could not send token to server");
                return;
            }
            tokenCatalogSelection.select(updatedDefinition.id());
            closeTokenCreationDialog();
            return;
        }

        if (session.getActiveScene() != null) {
            session.saveCanvasSceneToActiveScene();
            for (var sceneObject : session.getActiveScene().getObjects()) {
                if (sceneObject != null
                        && updatedDefinition.id().equals(sceneObject.getSourceTokenDefinitionId())) {
                    sceneObject.setOwnerId(updatedDefinition.defaultOwnerId());
                }
            }
            session.saveActiveTabletopAndScene();
        }

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
                if (session.isNetworkAuthorityActive()) {
                    VTT.LOGGER.info("Server token files are managed by the dedicated server");
                } else {
                    CreatedTokenStorage.viewCreatedTokenInExplorer(tokenDefinition);
                }
            }

            case DUPLICATE -> {
                if (session.isNetworkAuthorityActive()) {
                    VttClientTokenDefinitionSync.sendDuplicate(tokenDefinition.id());
                } else {
                    duplicateTokenDefinition(tokenDefinition);
                }
            }

            case DELETE -> {
                if (session.isNetworkAuthorityActive()) {
                    if (VttClientTokenDefinitionSync.sendDelete(tokenDefinition.id())) {
                        tokenCatalogSelection.clear();
                    }
                } else {
                    deleteTokenDefinition(tokenDefinition);
                }
            }

            case NONE -> {
            }
        }
    }

    private void beginEditTokenDefinition(TokenDefinition tokenDefinition) {
        if (tokenDefinition == null) {
            return;
        }

        TokenCreationDraft editDraft = session.isNetworkAuthorityActive()
                ? CreatedTokenStorage.createEditDraftFromFolder(
                        tokenDefinition, session.getSyncedServerTokensFolder())
                : CreatedTokenStorage.createEditDraft(tokenDefinition);

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
            if (assetCatalogOverlay.isScrollbarAt(assetRegistry, session.getAssetLibraryScanResult(),
                    assetCatalogController.getFilter(), assetCatalogController.getTreeState(),
                    assetCatalogController.getSearchQuery(), this.height, mouseX, mouseY)) {
                assetCatalogController.mouseClicked(assetCatalogOverlay, assetRegistry,
                        session.getAssetLibraryScanResult(), true, this.height, mouseX, mouseY);
                return true;
            }

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
                button,
                getConnectedPlayerOptions()
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
        if (hudSettingsOpen && session.getActiveScene() != null
                && editorSettingsOverlay.mouseReleased(
                mouseX, button, this.width, this.height,
                session.getActiveScene().getGrid(), session.isLocalMaster())) {
            persistGridSettings();
            return true;
        }
        if (playerViewPreview) {
            return renderState == null || inputController.mouseReleased(
                    mouseX, mouseY, button, getKeyboardModifiers(), renderState);
        }
        if (assetCatalogController.mouseReleased()) return true;
        if (tokenCreationDialog.mouseReleased()) return true;
        if (tokenCatalogController.releaseScrollbar()) return true;
        if (sceneOutlinerOverlay.mouseReleasedScrollbar()) return true;
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
        if (hudSettingsOpen && session.getActiveScene() != null
                && editorSettingsOverlay.mouseDragged(
                mouseX, this.width, this.height,
                session.getActiveScene().getGrid(), session.isLocalMaster())) {
            return true;
        }
        if (playerViewPreview) {
            return renderState == null || inputController.mouseDragged(
                    mouseX, mouseY, button, dragX, dragY, getKeyboardModifiers(), renderState);
        }
        if (assetCatalogController.mouseDragged(assetCatalogOverlay, assetRegistry,
                session.getAssetLibraryScanResult(), this.height, mouseY)) return true;
        if (tokenCreationDialog.mouseDragged(tokenCreationDraft, mouseY, this.width, this.height)) {
            return true;
        }
        if (tokenCatalogController.mouseDragged(tokenCatalogOverlay, tokenDefinitionRegistry,
                this.height, mouseY)) return true;
        if (sceneOutlinerOverlay.mouseDraggedScrollbar(scene, mouseY)) return true;
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
        if (hudSettingsOpen && editorSettingsOverlay.contains(
                mouseX, mouseY, this.width, this.height)) return true;
        if (editorHudOverlay.containsHud(mouseX, mouseY, this.width, this.height,
                editorHudState())) return true;
        if (playerViewPreview) {
            return renderState == null || inputController.mouseScrolled(
                    mouseX, mouseY, scrollX, scrollY, renderState);
        }
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

        if (tokenCatalogController.mouseScrolled(tokenCatalogOverlay, tokenDefinitionRegistry,
                panelVisibility.isTokenCatalogVisible(), this.height,
                mouseX, mouseY, scrollY)) {
            return true;
        }

        if (panelVisibility.isSceneOutlinerVisible()
                && sceneOutlinerOverlay.mouseScrolled(scene, mouseX, mouseY, scrollY)) {
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
        if (keyCode == GLFW.GLFW_KEY_P
                && (getKeyboardModifiers() & GLFW.GLFW_MOD_CONTROL) != 0
                && session.isLocalMaster()) {
            togglePlayerViewPreview();
            return true;
        }

        if (renamingSceneId != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirmSceneRename();
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                renamingSceneId = null;
                sceneRenameBuffer = null;
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !sceneRenameBuffer.isEmpty()) {
                sceneRenameBuffer = sceneRenameBuffer.substring(0, sceneRenameBuffer.length() - 1);
            }
            return true;
        }

        if (pendingDeleteSceneId != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                session.deleteScene(pendingDeleteSceneId);
                pendingDeleteSceneId = null;
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                pendingDeleteSceneId = null;
            }
            return true;
        }

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

        if (sceneContextMenu.isOpen() && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            sceneContextMenu.close();
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

        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                && (hudPlayersOpen || hudSettingsOpen || hudCreationOpen)) {
            closeHudPopups();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                && (inputController.closeCollisionBoxEditor()
                || inputController.cancelWallDrawing()
                || inputController.cancelDoorEditing()
                || inputController.cancelFogDrawing()
                || inputController.cancelMeasurement())) {
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
            if ((getKeyboardModifiers() & GLFW.GLFW_MOD_SHIFT) != 0) {
                if (session.setActiveSceneBackground(null)) {
                    closeBackgroundImagePicker();
                    VTT.LOGGER.info("[VTT Background] Requested background removal for active scene");
                }
                return true;
            }
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
            inputController.selectSelectTool();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_M) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            selectionManager.clearSelection();
            inputController.selectMeasureTool();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_C) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            inputController.toggleCollisionBoxEditor(renderState);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_W) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            selectionManager.clearSelection();
            inputController.selectWallTool();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_F
                && (getKeyboardModifiers() & GLFW.GLFW_MOD_SHIFT) == 0) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            selectionManager.clearSelection();
            inputController.selectFogTool();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_X) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            if (inputController.toggleSelectedFogVisibility()) return true;
        }

        if (keyCode == GLFW.GLFW_KEY_Y) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            if (inputController.toggleFogEnabled()) return true;
        }

        if (keyCode == GLFW.GLFW_KEY_D
                && (getKeyboardModifiers() & GLFW.GLFW_MOD_CONTROL) == 0) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            selectionManager.clearSelection();
            inputController.selectDoorTool();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_O
                && (getKeyboardModifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            toggleSelectedTokenOwnership();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_O) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            if (inputController.toggleSelectedDoorOpen()) return true;
        }

        if (keyCode == GLFW.GLFW_KEY_L) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            if (inputController.toggleSelectedDoorLocked()) return true;
        }

        if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            if (inputController.scaleSelectedWall(1.1)) return true;
            if (inputController.scaleSelectedDoor(1.1)) return true;
            if (inputController.scaleSelectedFogArea(1.1)) return true;
            inputController.scaleSelectedObjectsUp();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            if (inputController.scaleSelectedWall(0.9)) return true;
            if (inputController.scaleSelectedDoor(0.9)) return true;
            if (inputController.scaleSelectedFogArea(0.9)) return true;
            inputController.scaleSelectedObjectsDown();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_Q) {
            if (!canTransformSelectedTokens()) return true;
            if (inputController.rotateSelectedWall(-15.0)) return true;
            if (inputController.rotateSelectedDoor(-15.0)) return true;
            if (inputController.rotateSelectedFogArea(-15.0)) return true;
            inputController.rotateSelectedObjectsLeft();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_E) {
            if (!canTransformSelectedTokens()) return true;
            if (inputController.rotateSelectedWall(15.0)) return true;
            if (inputController.rotateSelectedDoor(15.0)) return true;
            if (inputController.rotateSelectedFogArea(15.0)) return true;
            inputController.rotateSelectedObjectsRight();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_F
                && (getKeyboardModifiers() & GLFW.GLFW_MOD_SHIFT) != 0) {
            if (!canTransformSelectedTokens()) return true;
            inputController.flipSelectedObjectsHorizontally();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_R) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            if (inputController.resetSelectedWallTransform()) return true;
            if (inputController.resetSelectedDoorTransform()) return true;
            if (inputController.resetSelectedFogAreaTransform()) return true;
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
            if (inputController.deleteSelectedDoor()) return true;
            if (inputController.deleteSelectedFogArea()) return true;
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

        if (keyCode == GLFW.GLFW_KEY_V
                && (getKeyboardModifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            toggleSelectedTokenAsVisionSource();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_V) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            inputController.toggleSelectedObjectsVisibility();
            return true;
        }

        String requestedStateId = getStateIdFromNumberKey(keyCode);

        if (requestedStateId != null) {
            if (!canTransformSelectedTokens()) return true;
            inputController.setSelectedObjectsActiveState(requestedStateId);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void toggleSelectedTokenAsVisionSource() {
        if (session.getActiveScene() == null) return;
        if (selectionManager.getSelectedObjectIds().size() != 1) {
            if (!session.getActiveScene().getVisionSourceObjectIds().isEmpty()) {
                session.getActiveScene().clearVisionSourceObjectIds();
                session.saveActiveTabletopAndScene();
                VTT.LOGGER.info("Cleared all scene vision sources");
            }
            return;
        }
        String selectedId = selectionManager.getSelectedObjectIds().iterator().next();
        CanvasObject selected = scene.findObjectById(selectedId);
        if (selected == null || !selected.visible() || !selected.hasSourceTokenDefinition()) return;
        boolean removed = session.getActiveScene().removeVisionSourceObjectId(selectedId);
        if (!removed) session.getActiveScene().addVisionSourceObjectId(selectedId);
        session.saveActiveTabletopAndScene();
        VTT.LOGGER.info("Token {} {} scene vision sources", selectedId,
                removed ? "removed from" : "added to");
    }

    private void toggleSelectedTokenOwnership() {
        if (session.getActiveScene() == null
                || selectionManager.getSelectedObjectIds().size() != 1) return;
        String selectedId = selectionManager.getSelectedObjectIds().iterator().next();
        CanvasObject selected = scene.findObjectById(selectedId);
        if (selected == null || !selected.hasSourceTokenDefinition()) return;
        session.saveCanvasSceneToActiveScene();
        session.getActiveScene().getObjects().stream()
                .filter(object -> object != null && selectedId.equals(object.getId()))
                .findFirst()
                .ifPresent(object -> {
                    String localPlayerId = session.getLocalPlayerId();
                    boolean removeOwnership = localPlayerId.equals(object.getOwnerId());
                    object.setOwnerId(removeOwnership ? null : localPlayerId);
                    session.saveActiveTabletopAndScene();
                    VTT.LOGGER.info("Token {} ownership {}", selectedId,
                            removeOwnership ? "cleared" : "assigned to local player");
                });
    }

    private boolean canTransformSelectedTokens() {
        if (session.isLocalMaster()) return true;
        if (selectionManager.getSelectedObjectIds().isEmpty() || session.getActiveScene() == null) return false;
        String localPlayerId = session.getLocalPlayerId();
        for (String selectedId : selectionManager.getSelectedObjectIds()) {
            boolean owned = session.getActiveScene().getObjects().stream()
                    .filter(object -> object != null && selectedId.equals(object.getId()))
                    .anyMatch(object -> localPlayerId.equals(object.getOwnerId()));
            if (!owned) return false;
        }
        return true;
    }

    private void adjustTokenVisionOuterRadius(double delta) {
        String objectId = resolveVisionRangeTargetId();
        if (objectId == null || session.getActiveScene() == null) return;
        session.saveCanvasSceneToActiveScene();
        session.getActiveScene().getObjects().stream()
                .filter(object -> object != null && objectId.equals(object.getId()))
                .findFirst()
                .ifPresent(object -> {
                    double current = object.getVisionOuterRadius() > 0.0
                            ? object.getVisionOuterRadius() : DEFAULT_TOKEN_VISION_OUTER_RADIUS;
                    double next = Math.max(TOKEN_VISION_OUTER_STEP, current + delta);
                    object.setVisionOuterRadius(next);
                    object.setVisionInnerRadius(Math.min(resolveInnerRadius(object), next));
                    session.saveActiveTabletopAndScene();
                    VTT.LOGGER.info("Token {} outer vision radius changed to {}", objectId, next);
                });
    }

    private void adjustTokenVisionInnerRadius(double delta) {
        String objectId = resolveVisionRangeTargetId();
        if (objectId == null || session.getActiveScene() == null) return;
        session.saveCanvasSceneToActiveScene();
        session.getActiveScene().getObjects().stream()
                .filter(object -> object != null && objectId.equals(object.getId()))
                .findFirst()
                .ifPresent(object -> {
                    double outer = object.getVisionOuterRadius() > 0.0
                            ? object.getVisionOuterRadius() : DEFAULT_TOKEN_VISION_OUTER_RADIUS;
                    double next = Math.max(0.0, Math.min(outer, resolveInnerRadius(object) + delta));
                    object.setVisionOuterRadius(outer);
                    object.setVisionInnerRadius(next);
                    session.saveActiveTabletopAndScene();
                    VTT.LOGGER.info("Token {} inner vision radius changed to {}", objectId, next);
                });
    }

    private double resolveInnerRadius(com.petrick.vtt.feature.tabletop.VttSceneObject object) {
        return object.getVisionInnerRadius();
    }

    private void resetTokenVisionRadii() {
        String objectId = resolveVisionRangeTargetId();
        if (objectId == null || session.getActiveScene() == null) return;
        session.saveCanvasSceneToActiveScene();
        session.getActiveScene().getObjects().stream()
                .filter(object -> object != null && objectId.equals(object.getId()))
                .findFirst()
                .ifPresent(object -> {
                    object.setVisionInnerRadius(DEFAULT_TOKEN_VISION_INNER_RADIUS);
                    object.setVisionOuterRadius(DEFAULT_TOKEN_VISION_OUTER_RADIUS);
                    session.saveActiveTabletopAndScene();
                    VTT.LOGGER.info("Token {} vision radii reset: inner={}, outer={}", objectId,
                            DEFAULT_TOKEN_VISION_INNER_RADIUS, DEFAULT_TOKEN_VISION_OUTER_RADIUS);
                });
    }

    private void toggleSelectedTokenVision() {
        String objectId = resolveVisionRangeTargetId();
        if (objectId == null || session.getActiveScene() == null) return;
        session.saveCanvasSceneToActiveScene();
        session.getActiveScene().getObjects().stream()
                .filter(object -> object != null && objectId.equals(object.getId()))
                .findFirst()
                .ifPresent(object -> {
                    object.setVisionEnabled(!object.isVisionEnabled());
                    session.saveActiveTabletopAndScene();
                    VTT.LOGGER.info("Token {} vision {}", objectId,
                            object.isVisionEnabled() ? "enabled" : "disabled");
                });
    }

    private String resolveVisionRangeTargetId() {
        if (selectionManager.getSelectedObjectIds().size() == 1) {
            String selectedId = selectionManager.getSelectedObjectIds().iterator().next();
            CanvasObject selected = scene.findObjectById(selectedId);
            if (selected != null && selected.hasSourceTokenDefinition()) return selectedId;
        }
        return session.getActiveScene() == null ? null : session.getActiveScene().getVisionSourceObjectId();
    }

    private void toggleLocalRole() {
        if (session.isNetworkAuthorityActive()) {
            VTT.LOGGER.info("Ignored local role toggle because the multiplayer server controls VTT roles");
            return;
        }
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

    private boolean isMasterView() {
        return session.isLocalMaster() && !playerViewPreview;
    }

    private void togglePlayerViewPreview() {
        playerViewPreview = !playerViewPreview;
        if (playerViewPreview) {
            closeBackgroundImagePicker();
            closeTokenCreationDialog();
            tokenCatalogContextMenu.close();
            newSceneNameBuffer = null;
            cancelRename();
            inputController.selectHandTool();
            closeHudPopups();
        }
        VTT.LOGGER.info("Player view preview {}", playerViewPreview ? "enabled" : "disabled");
    }

    private void applySceneBackgroundSelection(AssetCatalogItem item) {
        if (session.getActiveScene() == null) {
            VTT.LOGGER.warn("[VTT Background] There is no active scene");
            return;
        }
        if (!session.setActiveSceneBackground(item.id())) return;
        VTT.LOGGER.info("[VTT Background] Background update requested for scene {}: {}",
                session.getActiveScene().getId(), item.id());
    }

    private void closeBackgroundImagePicker() {
        backgroundImagePickerActive = false;
        lastBackgroundImagePickerClickedItemId = null;
        lastBackgroundImagePickerClickTime = 0L;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (renamingSceneId != null) {
            if (isAllowedRenameCharacter(codePoint) && sceneRenameBuffer.length() < 48) {
                sceneRenameBuffer += codePoint;
            }
            return true;
        }
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

        if (handleVisionShortcutCharacter(codePoint)) {
            return true;
        }

        return super.charTyped(codePoint, modifiers);
    }

    private boolean handleVisionShortcutCharacter(char character) {
        if (!session.getLocalRole().canEditTabletop()) return false;
        return switch (character) {
            case '[' -> { adjustTokenVisionOuterRadius(TOKEN_VISION_OUTER_STEP); yield true; }
            case ']' -> { adjustTokenVisionOuterRadius(-TOKEN_VISION_OUTER_STEP); yield true; }
            case ',' -> { adjustTokenVisionInnerRadius(TOKEN_VISION_INNER_STEP); yield true; }
            case '.' -> { adjustTokenVisionInnerRadius(-TOKEN_VISION_INNER_STEP); yield true; }
            case '/' -> { resetTokenVisionRadii(); yield true; }
            case ';' -> { toggleSelectedTokenVision(); yield true; }
            default -> false;
        };
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

    private List<VttPlayerOption> getConnectedPlayerOptions() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return List.of();
        return connection.getOnlinePlayers().stream()
                .map(info -> new VttPlayerOption(
                        info.getProfile().getId().toString(), info.getProfile().getName()))
                .sorted(Comparator.comparing(VttPlayerOption::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
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

        CanvasObject placedToken = tokenPlacementService.placeToken(definition, worldPosition);
        if (definition.defaultOwnerId() != null && session.getActiveScene() != null) {
            session.saveCanvasSceneToActiveScene();
            session.getActiveScene().getObjects().stream()
                    .filter(object -> object != null && placedToken.id().equals(object.getId()))
                    .findFirst()
                    .ifPresent(object -> {
                        object.setOwnerId(definition.defaultOwnerId());
                        session.saveActiveTabletopAndScene();
                    });
        }
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
        if (session.requestCreateScene(newSceneNameBuffer)) {
            selectionManager.clearSelection();
            inputController.selectHandTool();
            newSceneNameBuffer = null;
        }
    }

    private void handleSceneContextMenuAction(SceneContextMenuOverlay.Action action) {
        String sceneId = sceneContextMenu.getSceneId();
        sceneContextMenu.close();
        if (sceneId == null) return;
        if (action == SceneContextMenuOverlay.Action.RENAME) {
            renamingSceneId = sceneId;
            sceneRenameBuffer = session.getActiveTabletop().getSceneDisplayName(sceneId);
        } else if (action == SceneContextMenuOverlay.Action.DELETE
                && session.getActiveTabletop().getSceneIds().size() > 1) {
            pendingDeleteSceneId = sceneId;
        }
    }

    private void confirmSceneRename() {
        if (renamingSceneId == null || sceneRenameBuffer == null || sceneRenameBuffer.isBlank()) return;
        if (session.renameScene(renamingSceneId, sceneRenameBuffer)) {
            renamingSceneId = null;
            sceneRenameBuffer = null;
        }
    }

    private void renderSceneRenameDialog(VRenderContext context) {
        int width = 300, height = 70;
        int x = context.screenWidth() / 2 - width / 2;
        int y = context.screenHeight() / 2 - height / 2;
        renderSceneDialogFrame(context, x, y, width, height);
        context.graphics().drawString(this.font, "Rename Scene", x + 10, y + 10, 0xFFFFFFFF, false);
        context.graphics().drawString(this.font, sceneRenameBuffer + "_", x + 10, y + 28,
                0xFFFFFFFF, false);
        context.graphics().drawString(this.font, "Enter: rename   Esc: cancel", x + 10, y + 48,
                0xFFAAAAAA, false);
    }

    private void renderDeleteSceneConfirmation(VRenderContext context) {
        int width = 330, height = 70;
        int x = context.screenWidth() / 2 - width / 2;
        int y = context.screenHeight() / 2 - height / 2;
        renderSceneDialogFrame(context, x, y, width, height);
        String name = session.getActiveTabletop().getSceneDisplayName(pendingDeleteSceneId);
        context.graphics().drawString(this.font, "Delete Scene", x + 10, y + 10, 0xFFFF5555, false);
        context.graphics().drawString(this.font, "Delete '" + name + "'?", x + 10, y + 28,
                0xFFFFFFFF, false);
        context.graphics().drawString(this.font, "Enter: delete   Esc: cancel", x + 10, y + 48,
                0xFFAAAAAA, false);
    }

    private void renderSceneDialogFrame(VRenderContext context, int x, int y, int width, int height) {
        context.graphics().fill(x, y, x + width, y + height, 0xEE000000);
        context.graphics().hLine(x, x + width, y, 0xFFFFAA44);
        context.graphics().hLine(x, x + width, y + height, 0xFFFFAA44);
        context.graphics().vLine(x, y, y + height, 0xFFFFAA44);
        context.graphics().vLine(x + width, y, y + height, 0xFFFFAA44);
    }

    private void handleActiveSceneChange() {
        String activeSceneId = session.getActiveScene() == null
                ? null : session.getActiveScene().getId();
        if (java.util.Objects.equals(observedActiveSceneId, activeSceneId)) return;
        observedActiveSceneId = activeSceneId;
        selectionManager.clearSelection();
        inputController.selectHandTool();
        tokenCatalogContextMenu.close();
        sceneContextMenu.close();
        renamingObjectId = null;
        renameBuffer = null;
        newSceneNameBuffer = null;
        tokenCreationDraft = null;
        renamingSceneId = null;
        sceneRenameBuffer = null;
        pendingDeleteSceneId = null;
        backgroundImagePickerActive = false;
        tokenImagePickerActive = false;
        closeHudPopups();
        VTT.LOGGER.info("Editor changed to active scene: {}", activeSceneId);
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

    private boolean isSelectableBackgroundImage(AssetCatalogItem item) {
        if (item instanceof AssetCatalogItem.RegisteredAsset registeredAsset) {
            return registeredAsset.assetRef() instanceof BuiltInTextureAssetRef;
        }
        if (item instanceof AssetCatalogItem.LibraryFile libraryFile) {
            return libraryFile.entry().fileType() == AssetLibraryFileType.IMAGE;
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
        inputController.cancelDoorEditing();
        inputController.cancelFogDrawing();
        CursorManager.reset();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
