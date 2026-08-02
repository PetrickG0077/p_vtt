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
import com.petrick.vtt.editor.catalog.MapCatalogSelection;
import com.petrick.vtt.editor.catalog.MapCatalogContextMenu;
import com.petrick.vtt.editor.catalog.TokenCatalogClickResult;
import com.petrick.vtt.editor.catalog.TokenCatalogController;
import com.petrick.vtt.editor.catalog.TokenCatalogSelection;
import com.petrick.vtt.editor.dialog.TokenCreationDialog;
import com.petrick.vtt.editor.input.InputController;
import com.petrick.vtt.editor.hud.EditorHudOverlay;
import com.petrick.vtt.editor.hud.EditorHudTheme;
import com.petrick.vtt.editor.hud.EditorSettingsOverlay;
import com.petrick.vtt.editor.hud.AssetDeleteConfirmationOverlay;
import com.petrick.vtt.editor.hud.AssetBatchDeleteConfirmationOverlay;
import com.petrick.vtt.editor.hud.AssetFolderDeleteConfirmationOverlay;
import com.petrick.vtt.editor.hud.AssetManagerOverlay;
import com.petrick.vtt.editor.overlay.AssetCatalogOverlay;
import com.petrick.vtt.editor.overlay.DebugOverlay;
import com.petrick.vtt.editor.overlay.HelpOverlay;
import com.petrick.vtt.editor.overlay.MapCatalogOverlay;
import com.petrick.vtt.editor.overlay.MapCatalogContextMenuOverlay;
import com.petrick.vtt.editor.overlay.SceneOutlinerOverlay;
import com.petrick.vtt.editor.overlay.SceneListOverlay;
import com.petrick.vtt.editor.overlay.SelectionInspectorOverlay;
import com.petrick.vtt.editor.overlay.TokenCatalogOverlay;
import com.petrick.vtt.editor.panel.EditorPanelVisibility;
import com.petrick.vtt.editor.placement.TokenPlacementService;
import com.petrick.vtt.editor.scene.SceneBackgroundEditor;
import com.petrick.vtt.editor.token.TokenCreationDraft;
import com.petrick.vtt.editor.token.VttOwnedTokenOption;
import com.petrick.vtt.editor.token.VttPlayerOption;
import com.petrick.vtt.feature.asset.library.AssetLibraryFileType;
import com.petrick.vtt.feature.asset.folder.VttAssetFolderService;
import com.petrick.vtt.feature.token.persistence.CreatedTokenStorage;
import com.petrick.vtt.feature.token.CreatedTokenDefinitions;
import com.petrick.vtt.feature.asset.AssetRegistry;
import com.petrick.vtt.feature.asset.BuiltInTextureAssetRef;
import com.petrick.vtt.feature.asset.thumbnail.AssetThumbnail;
import com.petrick.vtt.feature.camera.Camera2D;
import com.petrick.vtt.feature.canvas.CanvasObject;
import com.petrick.vtt.feature.canvas.CanvasRenderer;
import com.petrick.vtt.feature.canvas.CanvasScene;
import com.petrick.vtt.feature.tabletop.VttSceneCameraView;
import com.petrick.vtt.feature.tabletop.VttSceneMap;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.map.MapDefinition;
import com.petrick.vtt.feature.map.MapDefinitionRegistry;
import com.petrick.vtt.feature.map.MapTextureMode;
import com.petrick.vtt.feature.map.persistence.CreatedMapStorage;
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
import com.petrick.vtt.network.client.VttClientPresentationState;
import com.petrick.vtt.network.client.VttClientEnvironmentCommandSync;
import com.petrick.vtt.network.client.VttClientAssetFolderResultState;
import com.petrick.vtt.network.client.VttClientAssetManagerChangeState;
import com.petrick.vtt.network.client.VttClientMapDefinitionSync;
import com.petrick.vtt.network.client.VttClientMapDefinitionResultState;
import com.petrick.vtt.network.client.VttClientTokenDefinitionResultState;
import com.petrick.vtt.network.client.VttClientSceneCommandResultState;
import com.petrick.vtt.network.client.VttClientSceneHistorySync;
import com.petrick.vtt.network.payload.VttPlayerModeCommandPayload;
import com.petrick.vtt.network.payload.VttPresentationCommandPayload;
import com.petrick.vtt.network.payload.VttAssetFolderCommandPayload;
import com.petrick.vtt.network.payload.VttAssetFolderResultPayload;
import com.petrick.vtt.network.payload.VttAssetManagerChangePayload;
import com.petrick.vtt.network.payload.VttMapDefinitionResultPayload;
import com.petrick.vtt.network.payload.VttTokenDefinitionResultPayload;
import com.petrick.vtt.network.payload.VttSceneCommandPayload;
import com.petrick.vtt.network.payload.VttSceneCommandResultPayload;
import com.petrick.vtt.network.payload.VttSceneHistoryCommandPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
    private static final long FOLLOW_CAMERA_SEND_INTERVAL_MS = 50L;

    private final VTTSession session;

    private final Camera2D camera;

    private final AssetRegistry assetRegistry;

    private final TokenDefinitionRegistry tokenDefinitionRegistry;
    private final MapDefinitionRegistry mapDefinitionRegistry;

    private final CanvasScene scene;

    private final SelectionManager selectionManager;

    private final CanvasRenderer canvasRenderer;

    private final InputController inputController;

    private final EditorHudOverlay editorHudOverlay;

    private final EditorSettingsOverlay editorSettingsOverlay;
    private final AssetManagerOverlay assetManagerOverlay;
    private final AssetDeleteConfirmationOverlay assetDeleteConfirmationOverlay =
            new AssetDeleteConfirmationOverlay();
    private final AssetBatchDeleteConfirmationOverlay assetBatchDeleteConfirmationOverlay =
            new AssetBatchDeleteConfirmationOverlay();
    private final AssetFolderDeleteConfirmationOverlay
            assetFolderDeleteConfirmationOverlay =
            new AssetFolderDeleteConfirmationOverlay();

    private final SceneBackgroundEditor sceneBackgroundEditor;

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
    private final MapCatalogOverlay mapCatalogOverlay;
    private final MapCatalogSelection mapCatalogSelection;
    private int mapCatalogScrollOffset;
    private boolean draggingMapCatalogScrollbar;
    private MapDefinition draggingMapDefinition;
    private double mapDragStartX;
    private double mapDragStartY;
    private final MapCatalogContextMenu mapCatalogContextMenu =
            new MapCatalogContextMenu();
    private final MapCatalogContextMenuOverlay mapCatalogContextMenuOverlay =
            new MapCatalogContextMenuOverlay();

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
    private BackgroundPickerTarget backgroundPickerTarget = BackgroundPickerTarget.NONE;

    private long lastBackgroundImagePickerClickTime;

    private String lastBackgroundImagePickerClickedItemId;

    private Viewport viewport;

    private RenderState renderState;

    private String renamingObjectId;

    private String renameBuffer;

    private String newSceneNameBuffer;
    private String newSceneBackgroundAssetId;
    private String newSceneBackgroundDisplayName;
    private ResourceLocation newSceneBackgroundPreviewTexture;
    private int newSceneBackgroundPreviewWidth;
    private int newSceneBackgroundPreviewHeight;
    private MapDefinition newSceneMapDefinition;

    private boolean mapPickerActive;
    private MapPickerTarget mapPickerTarget = MapPickerTarget.NONE;
    private long lastMapPickerClickTime;
    private String lastMapPickerClickedId;

    private String newMapNameBuffer;
    private String editingMapDefinitionId;
    private String newMapAssetId;
    private String newMapAssetDisplayName;
    private ResourceLocation newMapPreviewTexture;
    private int newMapPreviewWidth;
    private int newMapPreviewHeight;
    private MapTextureMode newMapTextureMode = MapTextureMode.STRETCH;
    private boolean newMapTextureModeListOpen;

    private String renamingSceneId;

    private String sceneRenameBuffer;

    private String pendingDeleteSceneId;

    private boolean playerViewPreview;

    private boolean hudPlayersOpen;

    private boolean hudSettingsOpen;

    private boolean hudCreationOpen;

    private AssetManagerOverlay.Section returnToAssetManagerSection;
    private String returnToAssetManagerFolder;
    private AssetManagerOverlay.Section assetFolderDialogSection;
    private String assetFolderDialogPath;
    private String assetFolderNameBuffer;
    private boolean renamingAssetFolder;
    private PendingAssetDeletion pendingAssetDeletion;
    private PendingAssetBatchDeletion pendingAssetBatchDeletion;
    private PendingAssetFolderDeletion pendingAssetFolderDeletion;
    private String pendingSceneRequestId;
    private String pendingSceneOperation;
    private String pendingSceneCommand;
    private String pendingSceneSuccessMessage;
    private String pendingSceneSelectionId;
    private String pendingSceneRestoreSelectionId;
    private String pendingSceneTargetFolder;
    private boolean pendingSceneReopenAssetManager;
    private long pendingSceneAcknowledgedRevision = -1L;
    private long pendingSceneRequestUntil;
    private String pendingHistoryRequestId;
    private String pendingHistorySuccessMessage;
    private long pendingHistoryAcknowledgedRevision = -1L;
    private long pendingHistoryStartedSnapshotVersion = -1L;
    private long pendingHistoryRequestUntil;
    private boolean pendingHistoryRejected;
    private long observedNetworkSnapshotVersion;
    private String pendingAssetFolderRequestId;
    private String pendingAssetFolderOperation;
    private String pendingAssetFolderSuccessMessage;
    private AssetManagerOverlay.Section pendingAssetFolderSection;
    private List<AssetManagerOverlay.MoveEntry> pendingAssetFolderRestoreSelection = List.of();
    private long pendingAssetFolderRequestUntil;
    private long pendingAssetFolderAcknowledgedRevision = -1L;
    private String pendingMapDefinitionRequestId;
    private String pendingMapDefinitionOperation;
    private String pendingMapDefinitionSuccessMessage;
    private String pendingMapDefinitionSelectionId;
    private String pendingMapDefinitionRestoreSelectionId;
    private long pendingMapDefinitionAcknowledgedRevision = -1L;
    private long pendingMapDefinitionRequestUntil;
    private String pendingTokenDefinitionRequestId;
    private String pendingTokenDefinitionOperation;
    private String pendingTokenDefinitionSuccessMessage;
    private String pendingTokenDefinitionSelectionId;
    private String pendingTokenDefinitionRestoreSelectionId;
    private String pendingTokenDefinitionTargetFolder;
    private long pendingTokenDefinitionAcknowledgedRevision = -1L;
    private long pendingTokenDefinitionRequestUntil;

    private String observedActiveSceneId;
    private boolean initialCameraApplied;
    private long lastFollowCameraSentAt;
    private double lastFollowCameraX = Double.NaN;
    private double lastFollowCameraY = Double.NaN;
    private double lastFollowCameraZoom = Double.NaN;
    private Vec2d followedCameraTarget;
    private double followedCameraZoom = 1.0;

    public VTTScreen() {
        super(Component.literal("Virtual Tabletop"));

        this.session = VTT.getApplication().getActiveSession();
        EditorHudTheme.initialize(Minecraft.getInstance().gameDirectory.toPath());

        this.camera = new Camera2D();
        this.assetRegistry = session.getAssetRegistry();
        this.tokenDefinitionRegistry = session.getTokenDefinitionRegistry();
        this.mapDefinitionRegistry = session.getMapDefinitionRegistry();
        this.scene = session.getCanvasScene();

        this.selectionManager = new SelectionManager();
        this.tokenPlacementService = new TokenPlacementService(scene, selectionManager);
        this.canvasRenderer = new CanvasRenderer(
                session.getAnimatedTextureService(), assetRegistry, session.getAssetThumbnailRegistry()
        );
        this.inputController = new InputController(camera, scene, selectionManager,
                session::getActiveScene, session::saveCanvasSceneToActiveScene,
                this::requestActiveSceneBackground,
                session::getLocalRole, session::getLocalPlayerId, session::isLocalSpectator);
        this.editorHudOverlay = new EditorHudOverlay();
        this.assetManagerOverlay = new AssetManagerOverlay(session.getTabletopStorage());
        this.editorSettingsOverlay = new EditorSettingsOverlay();
        this.sceneBackgroundEditor = new SceneBackgroundEditor(
                assetRegistry, session.getAssetThumbnailRegistry());

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
        this.mapCatalogOverlay = new MapCatalogOverlay();
        this.mapCatalogSelection = new MapCatalogSelection();

        this.sceneOutlinerOverlay = new SceneOutlinerOverlay();
        this.sceneListOverlay = new SceneListOverlay();
        this.tokenCreationDialog = new TokenCreationDialog();
        this.observedActiveSceneId = session.getActiveScene() == null
                ? null : session.getActiveScene().getId();
        this.observedNetworkSnapshotVersion = session.getNetworkSnapshotVersion();
    }

    @Override
    protected void init() {
        this.viewport = Viewport.fullScreen(this.width, this.height);
        this.renderState = new RenderState(camera, viewport);

        session.refreshAssetLibrary();
        if (!initialCameraApplied) {
            applyActiveSceneInitialCamera();
            initialCameraApplied = true;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ensureRenderState();
        handleNetworkHistorySnapshot();
        resolvePendingHistoryResult();
        resolvePendingAssetFolderResult();
        resolvePendingMapDefinitionResult();
        resolvePendingTokenDefinitionResult();
        resolvePendingSceneResult();
        applyRemoteAssetManagerChanges();
        applyPendingPresentationCamera();
        sendFollowCameraIfNeeded();
        handleActiveSceneChange();
        selectionManager.removeMissingObjects(scene);
        if (session.isLocalSpectator()
                && !selectionManager.getSelectedObjectIds().isEmpty()) {
            selectionManager.clearSelection();
        }

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
        boolean masterView = isMasterView();
        boolean spectatorView = session.isLocalSpectator();
        boolean fullTabletopView = masterView || spectatorView;
        boolean authoritativePlayerView = !session.isLocalMaster() && !spectatorView
                && session.isNetworkAuthorityActive();
        canvasRenderer.render(context, session.getActiveScene(), scene, selectionManager,
                masterView, !fullTabletopView, !inputController.isEditingCollisionBox(),
                session.isLocalMaster(), panelVisibility.isDebugVisible(),
                session.isLocalMaster() ? null : session.getLocalPlayerId(),
                authoritativePlayerView
                        ? session.getNetworkVisionRegions() : null,
                session.shouldMaskWhenNetworkVisionEmpty(),
                authoritativePlayerView
                        ? session.getNetworkVisibleObjectIds() : null);
        if (sceneBackgroundEditor.isActive()) {
            sceneBackgroundEditor.render(context, this.font, session.getActiveScene());
            if (panelVisibility.isMapCatalogVisible()) {
                mapCatalogOverlay.render(
                        context, this.font, mapDefinitionRegistry, mapCatalogSelection,
                        assetRegistry, session.getAssetThumbnailRegistry(),
                        mapCatalogScrollOffset);
            }
            if (panelVisibility.isSceneOutlinerVisible()) {
                sceneOutlinerOverlay.render(
                        context, this.font, scene, session.getActiveScene(),
                        selectionManager, sceneBackgroundEditor.getSelectedMapId());
            }
            if (draggingMapDefinition != null
                    && mapDragDistance(mouseX, mouseY) >= 6.0) {
                mapCatalogOverlay.renderDragPreview(
                        context, this.font, draggingMapDefinition, mouseX, mouseY);
            }
            mapCatalogContextMenuOverlay.render(
                    context, this.font, mapCatalogContextMenu);
            renderEditorNotice(context);
            renderEditorHud(context);
            VttAssetSyncHudOverlay.render(graphics);
            renderPresentationCurtain(context);
            return;
        }
        if (session.isLocalMaster()) inputController.renderToolOverlay(context, renderState);
        renderTitle(context);

        if (playerViewPreview || spectatorView) {
            renderEditorNotice(context);
            renderEditorHud(context);
            VttAssetSyncHudOverlay.render(graphics);
            renderPresentationCurtain(context);
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

        if (session.isLocalMaster() && panelVisibility.isMapCatalogVisible()) {
            mapCatalogOverlay.render(
                    context, this.font, mapDefinitionRegistry, mapCatalogSelection,
                    assetRegistry, session.getAssetThumbnailRegistry(), mapCatalogScrollOffset);
        }
        if (mapPickerActive) {
            mapCatalogOverlay.render(
                    context, this.font, mapDefinitionRegistry, mapCatalogSelection,
                    assetRegistry, session.getAssetThumbnailRegistry(), mapCatalogScrollOffset);
        }
        if (draggingMapDefinition != null && mapDragDistance(mouseX, mouseY) >= 6.0) {
            mapCatalogOverlay.renderDragPreview(
                    context, this.font, draggingMapDefinition, mouseX, mouseY);
        }

        if (session.isLocalMaster() && panelVisibility.isSceneOutlinerVisible()) {
            sceneOutlinerOverlay.render(
                    context, this.font, scene, session.getActiveScene(),
                    selectionManager, sceneBackgroundEditor.getSelectedMapId());
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

        if (backgroundImagePickerActive) {
            renderAssetCatalog(context);
        }

        tokenCatalogContextMenuOverlay.render(
                context,
                this.font,
                tokenCatalogContextMenu
        );
        mapCatalogContextMenuOverlay.render(
                context, this.font, mapCatalogContextMenu);
        sceneContextMenuOverlay.render(context, this.font, sceneContextMenu);
        renderEditorNotice(context);
        renderEditorHud(context);
        if (hudCreationOpen && session.isLocalMaster()) {
            context.graphics().fill(
                    0, 0, context.screenWidth(), context.screenHeight(),
                    0x99000000);
            assetManagerOverlay.setPendingOperation(currentAssetManagerPendingOperation());
            assetManagerOverlay.render(
                    context, this.font, session.getActiveTabletop(),
                    session.getActiveScene(),
                    mapDefinitionRegistry, tokenDefinitionRegistry,
                    assetRegistry, session.getAssetThumbnailRegistry());
            if (assetFolderNameBuffer != null) {
                renderAssetFolderDialog(context);
            }
        }

        if (tokenCreationDraft != null) {
            tokenCreationDialog.render(
                    context,
                    this.font,
                    tokenCreationDraft,
                    getConnectedPlayerOptions(),
                    assetManagerTargetFolder(AssetManagerOverlay.Section.TOKENS)
            );

            if (tokenImagePickerActive) {
                renderAssetCatalog(context);
            }
        }

        if (renamingObjectId != null) {
            renderRenameDialog(context);
        }

        if (newSceneNameBuffer != null && !mapPickerActive) {
            renderNewSceneDialog(context);
        }
        if (newMapNameBuffer != null && !backgroundImagePickerActive) {
            renderNewMapDialog(context);
        }
        if (renamingSceneId != null) renderSceneRenameDialog(context);
        if (pendingDeleteSceneId != null) renderDeleteSceneConfirmation(context);
        if (pendingAssetDeletion != null) {
            assetDeleteConfirmationOverlay.render(
                    context, this.font, pendingAssetDeletion.request());
        }
        if (pendingAssetBatchDeletion != null) {
            assetBatchDeleteConfirmationOverlay.render(
                    context, this.font, pendingAssetBatchDeletion.request());
        }
        if (pendingAssetFolderDeletion != null) {
            assetFolderDeleteConfirmationOverlay.render(
                    context, this.font, pendingAssetFolderDeletion.request());
        }
        VttAssetSyncHudOverlay.render(graphics);
        renderPresentationCurtain(context);
    }

    private void applyPendingPresentationCamera() {
        VttClientPresentationState.CameraTarget target =
                VttClientPresentationState.consumeCamera();
        if (target != null) {
            if (target.following() && !session.isLocalMaster()) {
                followedCameraTarget = new Vec2d(target.x(), target.y());
                followedCameraZoom = target.zoom();
            } else {
                followedCameraTarget = null;
                camera.setPosition(new Vec2d(target.x(), target.y()));
                camera.setZoom(target.zoom());
            }
        }
        if (session.isLocalMaster()
                || !VttClientPresentationState.isFollowingMasterCamera()) {
            followedCameraTarget = null;
            return;
        }
        if (followedCameraTarget == null) return;
        camera.setPosition(camera.getPosition().add(
                followedCameraTarget.subtract(camera.getPosition()).multiply(0.35)));
        camera.setZoom(camera.getZoom() + (followedCameraZoom - camera.getZoom()) * 0.35);
    }

    private void sendFollowCameraIfNeeded() {
        if (!session.isLocalMaster()
                || !session.isNetworkAuthorityActive()
                || !VttClientPresentationState.isFollowingMasterCamera()) {
            lastFollowCameraSentAt = 0L;
            lastFollowCameraX = Double.NaN;
            lastFollowCameraY = Double.NaN;
            lastFollowCameraZoom = Double.NaN;
            return;
        }
        Vec2d position = camera.getPosition();
        double zoom = camera.getZoom();
        boolean changed = !Double.isFinite(lastFollowCameraX)
                || Math.abs(position.x() - lastFollowCameraX) > 0.01
                || Math.abs(position.y() - lastFollowCameraY) > 0.01
                || Math.abs(zoom - lastFollowCameraZoom) > 0.0001;
        long now = System.currentTimeMillis();
        if (!changed || now - lastFollowCameraSentAt < FOLLOW_CAMERA_SEND_INTERVAL_MS) return;
        if (sendPresentationCommand(VttPresentationCommandPayload.SYNC_CAMERA)) {
            lastFollowCameraSentAt = now;
            lastFollowCameraX = position.x();
            lastFollowCameraY = position.y();
            lastFollowCameraZoom = zoom;
        }
    }

    private boolean blocksFollowedCameraPointer(int button) {
        if (session.isLocalMaster()
                || !VttClientPresentationState.isFollowingMasterCamera()) return false;
        return button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                || "hand".equals(inputController.getActiveToolId());
    }

    private void renderPresentationCurtain(VRenderContext context) {
        if (session.isLocalMaster()) return;
        float progress = VttClientPresentationState.curtainProgress();
        if (progress <= 0.001F) return;
        int curtainHeight = Math.min(context.screenHeight(),
                Math.max(1, Math.round(context.screenHeight() * progress)));
        context.graphics().fill(0, 0, context.screenWidth(), curtainHeight, 0xFF000000);

        if (curtainHeight < context.screenHeight()) {
            int foldWidth = 32;
            for (int x = 0; x < context.screenWidth(); x += foldWidth * 2) {
                context.graphics().fill(x, 0,
                        Math.min(context.screenWidth(), x + foldWidth),
                        curtainHeight, 0xFF030303);
            }
            int edgeTop = Math.max(0, curtainHeight - 8);
            context.graphics().fill(0, edgeTop, context.screenWidth(),
                    curtainHeight, 0xFF050505);
            for (int x = 0; x < context.screenWidth(); x += 24) {
                context.graphics().fill(x, curtainHeight,
                        Math.min(context.screenWidth(), x + 12),
                        Math.min(context.screenHeight(), curtainHeight + 4), 0xFF000000);
            }
        }
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
        boolean spectator = session.isLocalSpectator();
        if (!master) {
            hudCreationOpen = false;
            pendingAssetDeletion = null;
            pendingAssetFolderDeletion = null;
            pendingAssetBatchDeletion = null;
            panelVisibility.hideMasterPanels();
            String activeTool = inputController.getActiveToolId();
            if (!"hand".equals(activeTool) && !"select".equals(activeTool)) {
                inputController.selectHandTool();
            }
        }
        if (spectator) {
            hudSettingsOpen = false;
            hudCreationOpen = false;
            pendingAssetDeletion = null;
            pendingAssetFolderDeletion = null;
            pendingAssetBatchDeletion = null;
        }
        if (!hudCreationOpen && assetFolderNameBuffer != null) {
            closeAssetFolderDialog();
        }
        editorHudOverlay.render(context, this.font, editorHudState());
        if (hudSettingsOpen && session.getActiveScene() != null
                && mapPickerTarget != MapPickerTarget.ACTIVE_SCENE) {
            editorSettingsOverlay.render(
                    context, this.font, session.getActiveScene(), master);
        }
    }

    private EditorHudOverlay.State editorHudState() {
        CanvasObject selectedSceneToken = selectionManager.getSelectedObjectIds().size() == 1
                ? scene.findObjectById(selectionManager.getSelectedObjectIds().iterator().next())
                : null;
        if (selectedSceneToken != null && !selectedSceneToken.hasSourceTokenDefinition()) {
            selectedSceneToken = null;
        }
        String selectedSceneTokenOwnerId = "";
        if (selectedSceneToken != null && session.getActiveScene() != null) {
            String selectedSceneTokenId = selectedSceneToken.id();
            selectedSceneTokenOwnerId = session.getActiveScene().getObjects().stream()
                    .filter(object -> object != null
                            && selectedSceneTokenId.equals(object.getId()))
                    .map(object -> object.getOwnerId() == null ? "" : object.getOwnerId())
                    .findFirst().orElse("");
        }
        return new EditorHudOverlay.State(
                session.isLocalMaster(), session.isLocalSpectator(),
                sceneBackgroundEditor.isActive(),
                inputController.getActiveToolId(),
                pendingHistoryRequestId == null && inputController.canUndoEditorAction(),
                pendingHistoryRequestId == null && inputController.canRedoEditorAction(),
                inputController.nextUndoDescription(),
                inputController.nextRedoDescription(),
                hudPlayersOpen, hudSettingsOpen, hudCreationOpen,
                panelVisibility.isSceneListVisible(), panelVisibility.isMapCatalogVisible(),
                panelVisibility.isTokenCatalogVisible(),
                panelVisibility.isSceneOutlinerVisible(), getConnectedPlayerOptions(),
                getOwnedTokenOptions(),
                session.getLocalPlayerId(),
                selectedSceneToken == null ? "" : selectedSceneToken.id(),
                selectedSceneToken == null ? "" : selectedSceneToken.displayName(),
                selectedSceneTokenOwnerId);
    }

    private MapDefinition selectedMapDefinition() {
        String selectedId = mapCatalogSelection.getSelectedMapDefinitionId();
        return mapDefinitionRegistry.findById(selectedId).orElse(null);
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

    private boolean handleAssetManagerMouseClicked(
            double mouseX, double mouseY, int button
    ) {
        if (!hudCreationOpen || !session.isLocalMaster()) return false;
        if (assetFolderNameBuffer != null) {
            return handleAssetFolderDialogMouseClicked(mouseX, mouseY, button);
        }
        AssetManagerOverlay.Interaction interaction = assetManagerOverlay.mouseClicked(
                mouseX, mouseY, button, this.width, this.height,
                getKeyboardModifiers(),
                session.getActiveTabletop(), mapDefinitionRegistry,
                tokenDefinitionRegistry);
        if (!interaction.consumed()) return false;
        handleAssetManagerInteraction(interaction);
        return true;
    }

    private void handleAssetManagerInteraction(
            AssetManagerOverlay.Interaction interaction
    ) {
        if (interaction.section() == null) return;
        if ((pendingAssetFolderRequestId != null
                || pendingMapDefinitionRequestId != null
                || pendingTokenDefinitionRequestId != null
                || pendingSceneRequestId != null)
                && interaction.action() != AssetManagerOverlay.Action.NONE
                && interaction.action() != AssetManagerOverlay.Action.SELECT
                && interaction.action() != AssetManagerOverlay.Action.OPEN_FOLDER
                && interaction.action() != AssetManagerOverlay.Action.BACK_FOLDER) {
            VttClientEditorNotice.show("Wait for the current Asset Manager operation");
            return;
        }
        if (interaction.action() == AssetManagerOverlay.Action.REFRESH) {
            refreshAssetManagerFolders();
            return;
        }
        if (interaction.action() == AssetManagerOverlay.Action.BACK_FOLDER) {
            assetManagerOverlay.goBackFolder();
            return;
        }
        if (interaction.action() == AssetManagerOverlay.Action.OPEN_FOLDER) {
            assetManagerOverlay.openFolder(interaction.section(), interaction.value());
            return;
        }
        if (interaction.action() == AssetManagerOverlay.Action.CREATE_FOLDER) {
            beginAssetFolderDialog(
                    interaction.section(), interaction.id(), "", false);
            return;
        }
        if (interaction.action() == AssetManagerOverlay.Action.RENAME_FOLDER) {
            beginAssetFolderDialog(
                    interaction.section(), interaction.value(),
                    folderLeaf(interaction.value()), true);
            return;
        }
        if (interaction.action() == AssetManagerOverlay.Action.DUPLICATE_FOLDER) {
            requestAssetFolderCommand(
                    VttAssetFolderCommandPayload.DUPLICATE_FOLDER,
                    interaction.section(), interaction.value(), "");
            return;
        }
        if (interaction.action() == AssetManagerOverlay.Action.DELETE_FOLDER) {
            beginAssetFolderDeletion(interaction.section(), interaction.value());
            return;
        }
        if (interaction.action() == AssetManagerOverlay.Action.DELETE_SELECTION) {
            beginAssetManagerBatchDeletion(
                    interaction.section(), interaction.moveEntries());
            return;
        }
        if (interaction.action() == AssetManagerOverlay.Action.MOVE_ITEM
                || interaction.action() == AssetManagerOverlay.Action.MOVE_FOLDER) {
            requestAssetFolderCommand(
                    interaction.action() == AssetManagerOverlay.Action.MOVE_FOLDER
                            ? VttAssetFolderCommandPayload.MOVE_FOLDER
                            : VttAssetFolderCommandPayload.MOVE_ITEM,
                    interaction.section(), interaction.id(), interaction.value());
            return;
        }
        if (interaction.action() == AssetManagerOverlay.Action.MOVE_SELECTION) {
            String encodedSources = interaction.moveEntries().stream()
                    .map(entry -> (entry.folder() ? "F:" + entry.path() : "I:" + entry.id()))
                    .collect(java.util.stream.Collectors.joining("\n"));
            requestAssetFolderCommand(
                    VttAssetFolderCommandPayload.MOVE_SELECTION,
                    interaction.section(), encodedSources, interaction.value(),
                    interaction.moveEntries());
            return;
        }
        if (interaction.action() == AssetManagerOverlay.Action.SELECT) {
            selectAssetManagerItem(interaction.section(), interaction.id());
            return;
        }
        if (interaction.action() == AssetManagerOverlay.Action.ADD) {
            rememberAssetManagerReturn(interaction.section());
            hudCreationOpen = false;
            switch (interaction.section()) {
                case SCENES -> beginNewSceneDialog();
                case MAPS -> beginNewMapDialog();
                case TOKENS -> beginCreateTokenDefinition();
            }
            return;
        }
        if (interaction.id() == null) return;
        selectAssetManagerItem(interaction.section(), interaction.id());
        if (interaction.action() == AssetManagerOverlay.Action.DUPLICATE) {
            duplicateAssetManagerItem(interaction.section(), interaction.id());
        } else if (interaction.action() == AssetManagerOverlay.Action.EDIT) {
            rememberAssetManagerReturn(interaction.section());
            hudCreationOpen = false;
            switch (interaction.section()) {
                case SCENES -> {
                    renamingSceneId = interaction.id();
                    sceneRenameBuffer = session.getActiveTabletop()
                            .getSceneDisplayName(interaction.id());
                }
                case MAPS -> beginEditSelectedMapDialog();
                case TOKENS -> tokenDefinitionRegistry.findById(interaction.id())
                        .ifPresent(this::beginEditTokenDefinition);
            }
        } else if (interaction.action() == AssetManagerOverlay.Action.DELETE) {
            beginAssetManagerDeletion(interaction.section(), interaction.id());
        }
    }

    private void beginAssetFolderDialog(
            AssetManagerOverlay.Section section,
            String path,
            String initialName,
            boolean rename
    ) {
        assetManagerOverlay.cancelPointerInteraction();
        assetFolderDialogSection = section;
        assetFolderDialogPath = path == null ? "" : path;
        assetFolderNameBuffer = initialName == null ? "" : initialName;
        renamingAssetFolder = rename;
    }

    private void closeAssetFolderDialog() {
        assetFolderDialogSection = null;
        assetFolderDialogPath = null;
        assetFolderNameBuffer = null;
        renamingAssetFolder = false;
    }

    private void confirmAssetFolderDialog() {
        if (assetFolderDialogSection == null || assetFolderNameBuffer == null
                || assetFolderNameBuffer.isBlank()) return;
        requestAssetFolderCommand(
                renamingAssetFolder
                        ? VttAssetFolderCommandPayload.RENAME_FOLDER
                        : VttAssetFolderCommandPayload.CREATE_FOLDER,
                assetFolderDialogSection, assetFolderDialogPath,
                assetFolderNameBuffer.trim());
        closeAssetFolderDialog();
    }

    private void requestAssetFolderCommand(
            String operation,
            AssetManagerOverlay.Section section,
            String source,
            String value
    ) {
        requestAssetFolderCommand(operation, section, source, value, List.of());
    }

    private void requestAssetFolderCommand(
            String operation,
            AssetManagerOverlay.Section section,
            String source,
            String value,
            List<AssetManagerOverlay.MoveEntry> restoreSelection
    ) {
        if (section == null) return;
        if (session.isNetworkAuthorityActive()
                && (pendingAssetFolderRequestId != null
                || pendingMapDefinitionRequestId != null
                || pendingTokenDefinitionRequestId != null
                || pendingSceneRequestId != null)) {
            VttClientEditorNotice.show("Wait for the current Asset Manager operation");
            return;
        }
        VttAssetFolderService.Section storageSection =
                VttAssetFolderService.Section.valueOf(section.name());
        String requestId = UUID.randomUUID().toString();
        if (session.isNetworkAuthorityActive()) {
            VttClientAssetFolderResultState.reset();
        }
        boolean requested = session.requestAssetFolderCommand(
                requestId, operation, storageSection, source, value);
        if (!requested) {
            VttClientEditorNotice.show(
                    VttAssetFolderCommandPayload.DELETE_FOLDER.equals(operation)
                            ? "Folder must be empty before it can be deleted"
                            : "Could not update asset folder");
            return;
        }
        String message = switch (operation) {
            case VttAssetFolderCommandPayload.CREATE_FOLDER -> "Folder created";
            case VttAssetFolderCommandPayload.REFRESH -> "Asset folders refreshed";
            case VttAssetFolderCommandPayload.RENAME_FOLDER -> "Folder renamed";
            case VttAssetFolderCommandPayload.DUPLICATE_FOLDER -> "Folder duplicated";
            case VttAssetFolderCommandPayload.MOVE_FOLDER -> "Folder moved";
            case VttAssetFolderCommandPayload.MOVE_ITEM -> "Asset moved";
            case VttAssetFolderCommandPayload.MOVE_SELECTION -> "Assets moved";
            case VttAssetFolderCommandPayload.DELETE_SELECTION -> "Selection deleted";
            case VttAssetFolderCommandPayload.DELETE_FOLDER -> "Folder deleted";
            case VttAssetFolderCommandPayload.MOVE_CONTENTS_AND_DELETE_FOLDER ->
                    "Folder contents moved and folder deleted";
            default -> "Asset folders updated";
        };
        if (session.isNetworkAuthorityActive()) {
            pendingAssetFolderRequestId = requestId;
            pendingAssetFolderOperation = operationLabel(operation);
            pendingAssetFolderSuccessMessage = message;
            pendingAssetFolderSection = section;
            pendingAssetFolderRestoreSelection = restoreSelection == null
                    ? List.of() : List.copyOf(restoreSelection);
            pendingAssetFolderRequestUntil = System.currentTimeMillis() + 60_000L;
        } else {
            VttClientEditorNotice.show(message);
        }
    }

    private String operationLabel(String operation) {
        return switch (operation) {
            case VttAssetFolderCommandPayload.CREATE_FOLDER -> "creating folder";
            case VttAssetFolderCommandPayload.REFRESH -> "refreshing folders";
            case VttAssetFolderCommandPayload.RENAME_FOLDER -> "renaming folder";
            case VttAssetFolderCommandPayload.DUPLICATE_FOLDER -> "duplicating folder";
            case VttAssetFolderCommandPayload.MOVE_FOLDER,
                    VttAssetFolderCommandPayload.MOVE_ITEM,
                    VttAssetFolderCommandPayload.MOVE_SELECTION -> "moving assets";
            case VttAssetFolderCommandPayload.DELETE_SELECTION,
                    VttAssetFolderCommandPayload.DELETE_FOLDER,
                    VttAssetFolderCommandPayload.MOVE_CONTENTS_AND_DELETE_FOLDER ->
                    "deleting assets";
            default -> "updating assets";
        };
    }

    private void resolvePendingAssetFolderResult() {
        if (pendingAssetFolderAcknowledgedRevision >= 0L
                && session.getNetworkAuthorityRevision()
                >= pendingAssetFolderAcknowledgedRevision) {
            VttClientEditorNotice.show(pendingAssetFolderSuccessMessage);
            clearPendingAssetFolderRequest();
            return;
        }
        VttAssetFolderResultPayload result = VttClientAssetFolderResultState.consume();
        if (result != null && pendingAssetFolderRequestId != null
                && pendingAssetFolderRequestId.equals(result.requestId())) {
            if (result.success()) {
                pendingAssetFolderSuccessMessage = result.message().isBlank()
                        ? pendingAssetFolderSuccessMessage : result.message();
                pendingAssetFolderAcknowledgedRevision = result.authorityRevision();
                pendingAssetFolderOperation = "synchronizing assets";
                if (session.getNetworkAuthorityRevision()
                        >= pendingAssetFolderAcknowledgedRevision) {
                    VttClientEditorNotice.show(pendingAssetFolderSuccessMessage);
                    clearPendingAssetFolderRequest();
                }
            } else {
                restorePendingAssetFolderSelection();
                VttClientEditorNotice.show(result.message().isBlank()
                        ? "The server rejected the Asset Manager operation"
                        : result.message());
                if (VttAssetFolderResultPayload.STALE_REVISION.equals(result.code())) {
                    session.requestAssetManagerResync();
                }
            }
            if (!result.success()) clearPendingAssetFolderRequest();
            return;
        }
        if (pendingAssetFolderRequestId != null
                && System.currentTimeMillis() > pendingAssetFolderRequestUntil) {
            restorePendingAssetFolderSelection();
            clearPendingAssetFolderRequest();
            VttClientEditorNotice.show("Asset Manager operation timed out");
            session.requestAssetManagerResync();
        }
    }

    private void restorePendingAssetFolderSelection() {
        assetManagerOverlay.restoreSelection(
                pendingAssetFolderSection, pendingAssetFolderRestoreSelection);
    }

    private void clearPendingAssetFolderRequest() {
        pendingAssetFolderRequestId = null;
        pendingAssetFolderOperation = null;
        pendingAssetFolderSuccessMessage = null;
        pendingAssetFolderSection = null;
        pendingAssetFolderRestoreSelection = List.of();
        pendingAssetFolderRequestUntil = 0L;
        pendingAssetFolderAcknowledgedRevision = -1L;
        assetManagerOverlay.setPendingOperation(currentAssetManagerPendingOperation());
    }

    private String beginPendingMapDefinitionRequest(
            String operation, String successMessage,
            String selectionId, String restoreSelectionId
    ) {
        if (pendingMapDefinitionRequestId != null
                || pendingAssetFolderRequestId != null
                || pendingTokenDefinitionRequestId != null
                || pendingSceneRequestId != null) {
            VttClientEditorNotice.show("Wait for the current map operation");
            return null;
        }
        String requestId = UUID.randomUUID().toString();
        VttClientMapDefinitionResultState.reset();
        pendingMapDefinitionRequestId = requestId;
        pendingMapDefinitionOperation = operation;
        pendingMapDefinitionSuccessMessage = successMessage;
        pendingMapDefinitionSelectionId = selectionId;
        pendingMapDefinitionRestoreSelectionId = restoreSelectionId;
        pendingMapDefinitionAcknowledgedRevision = -1L;
        pendingMapDefinitionRequestUntil = System.currentTimeMillis() + 120_000L;
        assetManagerOverlay.setPendingOperation(operation);
        return requestId;
    }

    private void resolvePendingMapDefinitionResult() {
        if (pendingMapDefinitionAcknowledgedRevision >= 0L
                && session.getNetworkAuthorityRevision()
                >= pendingMapDefinitionAcknowledgedRevision) {
            completePendingMapDefinitionRequest();
            return;
        }
        VttMapDefinitionResultPayload result =
                VttClientMapDefinitionResultState.consume();
        if (result != null && pendingMapDefinitionRequestId != null
                && pendingMapDefinitionRequestId.equals(result.requestId())) {
            if (result.success()) {
                pendingMapDefinitionSuccessMessage = result.message().isBlank()
                        ? pendingMapDefinitionSuccessMessage : result.message();
                if (!result.definitionId().isBlank()) {
                    pendingMapDefinitionSelectionId = result.definitionId();
                }
                pendingMapDefinitionAcknowledgedRevision = result.authorityRevision();
                pendingMapDefinitionOperation = "synchronizing maps";
                if (session.getNetworkAuthorityRevision()
                        >= pendingMapDefinitionAcknowledgedRevision) {
                    completePendingMapDefinitionRequest();
                }
            } else {
                restorePendingMapDefinitionSelection();
                VttClientEditorNotice.show(result.message().isBlank()
                        ? "The server rejected the map operation" : result.message());
                if (VttMapDefinitionResultPayload.STALE_REVISION.equals(result.code())) {
                    session.requestAssetManagerResync();
                }
                clearPendingMapDefinitionRequest();
            }
            return;
        }
        if (pendingMapDefinitionRequestId != null
                && System.currentTimeMillis() > pendingMapDefinitionRequestUntil) {
            restorePendingMapDefinitionSelection();
            clearPendingMapDefinitionRequest();
            VttClientEditorNotice.show("Map operation timed out");
            session.requestAssetManagerResync();
        }
    }

    private void completePendingMapDefinitionRequest() {
        String selectionId = pendingMapDefinitionSelectionId;
        String message = pendingMapDefinitionSuccessMessage;
        clearPendingMapDefinitionRequest();
        assetManagerOverlay.reconcileCurrentFolder(session.getActiveTabletop());
        assetManagerOverlay.reconcileSelection(
                session.getActiveTabletop(), mapDefinitionRegistry,
                tokenDefinitionRegistry);
        if (selectionId != null
                && mapDefinitionRegistry.findById(selectionId).isPresent()) {
            mapCatalogSelection.select(selectionId);
            assetManagerOverlay.select(AssetManagerOverlay.Section.MAPS, selectionId);
        } else {
            mapCatalogSelection.clear();
        }
        VttClientEditorNotice.show(message);
    }

    private void restorePendingMapDefinitionSelection() {
        String id = pendingMapDefinitionRestoreSelectionId;
        if (id == null || mapDefinitionRegistry.findById(id).isEmpty()) return;
        mapCatalogSelection.select(id);
        assetManagerOverlay.select(AssetManagerOverlay.Section.MAPS, id);
    }

    private void clearPendingMapDefinitionRequest() {
        pendingMapDefinitionRequestId = null;
        pendingMapDefinitionOperation = null;
        pendingMapDefinitionSuccessMessage = null;
        pendingMapDefinitionSelectionId = null;
        pendingMapDefinitionRestoreSelectionId = null;
        pendingMapDefinitionAcknowledgedRevision = -1L;
        pendingMapDefinitionRequestUntil = 0L;
        assetManagerOverlay.setPendingOperation(currentAssetManagerPendingOperation());
    }

    private String beginPendingTokenDefinitionRequest(
            String operation, String successMessage, String selectionId,
            String restoreSelectionId, String targetFolder
    ) {
        if (pendingTokenDefinitionRequestId != null
                || pendingAssetFolderRequestId != null
                || pendingMapDefinitionRequestId != null
                || pendingSceneRequestId != null) {
            VttClientEditorNotice.show("Wait for the current token operation");
            return null;
        }
        String requestId = UUID.randomUUID().toString();
        VttClientTokenDefinitionResultState.reset();
        pendingTokenDefinitionRequestId = requestId;
        pendingTokenDefinitionOperation = operation;
        pendingTokenDefinitionSuccessMessage = successMessage;
        pendingTokenDefinitionSelectionId = selectionId;
        pendingTokenDefinitionRestoreSelectionId = restoreSelectionId;
        pendingTokenDefinitionTargetFolder = targetFolder;
        pendingTokenDefinitionAcknowledgedRevision = -1L;
        pendingTokenDefinitionRequestUntil = System.currentTimeMillis() + 120_000L;
        assetManagerOverlay.setPendingOperation(operation);
        return requestId;
    }

    private void resolvePendingTokenDefinitionResult() {
        if (pendingTokenDefinitionAcknowledgedRevision >= 0L
                && session.getNetworkAuthorityRevision()
                >= pendingTokenDefinitionAcknowledgedRevision) {
            completePendingTokenDefinitionRequest();
            return;
        }
        VttTokenDefinitionResultPayload result =
                VttClientTokenDefinitionResultState.consume();
        if (result != null && pendingTokenDefinitionRequestId != null
                && pendingTokenDefinitionRequestId.equals(result.requestId())) {
            if (result.success()) {
                pendingTokenDefinitionSuccessMessage = result.message().isBlank()
                        ? pendingTokenDefinitionSuccessMessage : result.message();
                if (!result.definitionId().isBlank()) {
                    pendingTokenDefinitionSelectionId = result.definitionId();
                }
                pendingTokenDefinitionAcknowledgedRevision = result.authorityRevision();
                pendingTokenDefinitionOperation = "synchronizing tokens";
                if (session.getNetworkAuthorityRevision()
                        >= pendingTokenDefinitionAcknowledgedRevision) {
                    completePendingTokenDefinitionRequest();
                }
            } else {
                restorePendingTokenDefinitionSelection();
                VttClientEditorNotice.show(result.message().isBlank()
                        ? "The server rejected the token operation" : result.message());
                clearPendingTokenDefinitionRequest();
                session.requestAssetManagerResync();
            }
            return;
        }
        if (pendingTokenDefinitionRequestId != null
                && System.currentTimeMillis() > pendingTokenDefinitionRequestUntil) {
            restorePendingTokenDefinitionSelection();
            clearPendingTokenDefinitionRequest();
            VttClientEditorNotice.show("Token operation timed out");
            session.requestAssetManagerResync();
        }
    }

    private void completePendingTokenDefinitionRequest() {
        String selectionId = pendingTokenDefinitionSelectionId;
        String targetFolder = pendingTokenDefinitionTargetFolder;
        String message = pendingTokenDefinitionSuccessMessage;
        clearPendingTokenDefinitionRequest();
        assetManagerOverlay.reconcileCurrentFolder(session.getActiveTabletop());
        assetManagerOverlay.reconcileSelection(
                session.getActiveTabletop(), mapDefinitionRegistry,
                tokenDefinitionRegistry);
        if (selectionId != null
                && tokenDefinitionRegistry.findById(selectionId).isPresent()) {
            tokenCatalogSelection.select(selectionId);
            assetManagerOverlay.select(AssetManagerOverlay.Section.TOKENS, selectionId);
            if (targetFolder != null && !targetFolder.isBlank()) {
                moveCreatedAssetToFolder(
                        AssetManagerOverlay.Section.TOKENS, selectionId, targetFolder);
            }
        } else {
            tokenCatalogSelection.clear();
        }
        VttClientEditorNotice.show(message);
    }

    private void restorePendingTokenDefinitionSelection() {
        String id = pendingTokenDefinitionRestoreSelectionId;
        if (id == null || tokenDefinitionRegistry.findById(id).isEmpty()) return;
        tokenCatalogSelection.select(id);
        assetManagerOverlay.select(AssetManagerOverlay.Section.TOKENS, id);
    }

    private void clearPendingTokenDefinitionRequest() {
        pendingTokenDefinitionRequestId = null;
        pendingTokenDefinitionOperation = null;
        pendingTokenDefinitionSuccessMessage = null;
        pendingTokenDefinitionSelectionId = null;
        pendingTokenDefinitionRestoreSelectionId = null;
        pendingTokenDefinitionTargetFolder = null;
        pendingTokenDefinitionAcknowledgedRevision = -1L;
        pendingTokenDefinitionRequestUntil = 0L;
        assetManagerOverlay.setPendingOperation(currentAssetManagerPendingOperation());
    }

    private String currentAssetManagerPendingOperation() {
        if (pendingAssetFolderOperation != null) return pendingAssetFolderOperation;
        if (pendingMapDefinitionOperation != null) return pendingMapDefinitionOperation;
        if (pendingTokenDefinitionOperation != null) return pendingTokenDefinitionOperation;
        return pendingSceneOperation;
    }

    private boolean hasPendingAssetManagerOperation() {
        return pendingAssetFolderRequestId != null
                || pendingMapDefinitionRequestId != null
                || pendingTokenDefinitionRequestId != null
                || pendingSceneRequestId != null
                || pendingHistoryRequestId != null;
    }

    private String beginPendingSceneRequest(
            String command, String operation, String successMessage,
            String selectionId, String restoreSelectionId,
            String targetFolder, boolean reopenAssetManager
    ) {
        if (hasPendingAssetManagerOperation()) {
            VttClientEditorNotice.show("Wait for the current scene operation");
            return null;
        }
        String requestId = UUID.randomUUID().toString();
        VttClientSceneCommandResultState.reset();
        pendingSceneRequestId = requestId;
        pendingSceneCommand = command;
        pendingSceneOperation = operation;
        pendingSceneSuccessMessage = successMessage;
        pendingSceneSelectionId = selectionId;
        pendingSceneRestoreSelectionId = restoreSelectionId;
        pendingSceneTargetFolder = targetFolder;
        pendingSceneReopenAssetManager = reopenAssetManager;
        pendingSceneAcknowledgedRevision = -1L;
        pendingSceneRequestUntil = System.currentTimeMillis() + 120_000L;
        assetManagerOverlay.setPendingOperation(operation);
        return requestId;
    }

    private void resolvePendingSceneResult() {
        if (pendingSceneAcknowledgedRevision >= 0L
                && session.getNetworkAuthorityRevision()
                >= pendingSceneAcknowledgedRevision) {
            completePendingSceneRequest();
            return;
        }
        VttSceneCommandResultPayload result =
                VttClientSceneCommandResultState.consume(pendingSceneRequestId);
        if (result != null && pendingSceneRequestId != null
                && pendingSceneRequestId.equals(result.requestId())) {
            if (result.success()) {
                pendingSceneSuccessMessage = result.message().isBlank()
                        ? pendingSceneSuccessMessage : result.message();
                if (!result.sceneId().isBlank()) pendingSceneSelectionId = result.sceneId();
                pendingSceneAcknowledgedRevision = result.authorityRevision();
                pendingSceneOperation = "synchronizing scenes";
                if (session.getNetworkAuthorityRevision()
                        >= pendingSceneAcknowledgedRevision) {
                    completePendingSceneRequest();
                }
            } else {
                restorePendingSceneSelection();
                VttClientEditorNotice.show(result.message().isBlank()
                        ? "The server rejected the scene operation" : result.message());
                clearPendingSceneRequest();
                session.requestAssetManagerResync();
            }
            return;
        }
        if (pendingSceneRequestId != null
                && System.currentTimeMillis() > pendingSceneRequestUntil) {
            restorePendingSceneSelection();
            clearPendingSceneRequest();
            VttClientEditorNotice.show("Scene operation timed out");
            session.requestAssetManagerResync();
        }
    }

    private void completePendingSceneRequest() {
        String command = pendingSceneCommand;
        String selectionId = pendingSceneSelectionId;
        String targetFolder = pendingSceneTargetFolder;
        boolean reopenManager = pendingSceneReopenAssetManager;
        String message = pendingSceneSuccessMessage;
        clearPendingSceneRequest();
        handleActiveSceneChange();
        assetManagerOverlay.reconcileCurrentFolder(session.getActiveTabletop());
        assetManagerOverlay.reconcileSelection(
                session.getActiveTabletop(), mapDefinitionRegistry,
                tokenDefinitionRegistry);
        if (VttSceneCommandPayload.CREATE.equals(command)
                && selectionId != null && targetFolder != null
                && !targetFolder.isBlank()) {
            moveCreatedAssetToFolder(
                    AssetManagerOverlay.Section.SCENES, selectionId, targetFolder);
        }
        if (reopenManager) {
            hudCreationOpen = true;
            assetManagerOverlay.openFolder(
                    AssetManagerOverlay.Section.SCENES,
                    targetFolder == null ? "" : targetFolder);
        }
        if (selectionId != null
                && session.getActiveTabletop().getSceneIds().contains(selectionId)) {
            assetManagerOverlay.select(AssetManagerOverlay.Section.SCENES, selectionId);
        }
        VttClientEditorNotice.show(message);
    }

    private void restorePendingSceneSelection() {
        String id = pendingSceneRestoreSelectionId;
        if (id != null && session.getActiveTabletop().getSceneIds().contains(id)) {
            assetManagerOverlay.select(AssetManagerOverlay.Section.SCENES, id);
        }
    }

    private void clearPendingSceneRequest() {
        pendingSceneRequestId = null;
        pendingSceneOperation = null;
        pendingSceneCommand = null;
        pendingSceneSuccessMessage = null;
        pendingSceneSelectionId = null;
        pendingSceneRestoreSelectionId = null;
        pendingSceneTargetFolder = null;
        pendingSceneReopenAssetManager = false;
        pendingSceneAcknowledgedRevision = -1L;
        pendingSceneRequestUntil = 0L;
        assetManagerOverlay.setPendingOperation(currentAssetManagerPendingOperation());
    }

    private void applyRemoteAssetManagerChanges() {
        if (!session.isNetworkAuthorityActive() || !session.isLocalMaster()) return;
        List<VttAssetManagerChangePayload> changes =
                VttClientAssetManagerChangeState.consumeThrough(
                        session.getNetworkAuthorityRevision());
        if (changes.isEmpty()) return;

        assetManagerOverlay.reconcileCurrentFolder(session.getActiveTabletop());
        assetManagerOverlay.reconcileSelection(
                session.getActiveTabletop(), mapDefinitionRegistry,
                tokenDefinitionRegistry);

        VttAssetManagerChangePayload latest = changes.get(changes.size() - 1);
        String actor = latest.actorName() == null || latest.actorName().isBlank()
                ? "Another master" : latest.actorName();
        String detail = latest.message() == null || latest.message().isBlank()
                ? "updated the Asset Manager" : latest.message().toLowerCase(java.util.Locale.ROOT);
        VttClientEditorNotice.show(changes.size() == 1
                ? actor + ": " + detail
                : actor + " updated the Asset Manager (" + changes.size() + " changes)");
    }

    private void refreshAssetManagerFolders() {
        pendingAssetFolderDeletion = null;
        requestAssetFolderCommand(
                VttAssetFolderCommandPayload.REFRESH,
                assetManagerOverlay.section(), "", "");
        assetManagerOverlay.reconcileCurrentFolder(session.getActiveTabletop());
    }

    private String folderLeaf(String path) {
        if (path == null || path.isBlank()) return "";
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash < 0 ? normalized : normalized.substring(slash + 1);
    }

    private void duplicateAssetManagerItem(
            AssetManagerOverlay.Section section,
            String id
    ) {
        switch (section) {
            case SCENES -> {
                requestSceneDuplicate(id, true);
            }
            case MAPS -> mapDefinitionRegistry.findById(id).ifPresent(definition -> {
                duplicateMapDefinition(definition);
                assetManagerOverlay.select(
                        AssetManagerOverlay.Section.MAPS,
                        mapCatalogSelection.getSelectedMapDefinitionId());
            });
            case TOKENS -> tokenDefinitionRegistry.findById(id).ifPresent(definition -> {
                if (session.isNetworkAuthorityActive()) {
                    requestServerTokenDuplicate(definition);
                } else {
                    duplicateTokenDefinition(definition);
                    assetManagerOverlay.select(
                            AssetManagerOverlay.Section.TOKENS,
                            tokenCatalogSelection.getSelectedTokenDefinitionId());
                }
            });
        }
    }

    private void requestSceneDuplicate(String sceneId, boolean reopenAssetManager) {
        if (sceneId == null) return;
        String folder = session.getActiveTabletop().getSceneFolder(sceneId);
        if (session.isNetworkAuthorityActive()) {
            String requestId = beginPendingSceneRequest(
                    VttSceneCommandPayload.DUPLICATE, "duplicating scene",
                    "Scene duplicated", null, sceneId, folder,
                    reopenAssetManager);
            if (requestId == null) return;
            if (!session.requestDuplicateScene(sceneId, requestId)) {
                clearPendingSceneRequest();
                VttClientEditorNotice.show("Could not duplicate scene");
            } else {
                VttClientEditorNotice.show("Scene duplication sent to server");
            }
            return;
        }
        if (!session.requestDuplicateScene(sceneId)) {
            VttClientEditorNotice.show("Could not duplicate scene");
            return;
        }
        String duplicateId = session.getActiveTabletop().getActiveSceneId();
        if (reopenAssetManager) {
            hudCreationOpen = true;
            assetManagerOverlay.openFolder(AssetManagerOverlay.Section.SCENES, folder);
            assetManagerOverlay.select(AssetManagerOverlay.Section.SCENES, duplicateId);
        }
    }

    private void requestSceneDelete(String sceneId, boolean reopenAssetManager) {
        if (sceneId == null) return;
        String folder = session.getActiveTabletop().getSceneFolder(sceneId);
        if (session.isNetworkAuthorityActive()) {
            String requestId = beginPendingSceneRequest(
                    VttSceneCommandPayload.DELETE, "deleting scene", "Scene deleted",
                    null, sceneId, folder, reopenAssetManager);
            if (requestId == null) return;
            if (!session.deleteScene(sceneId, requestId)) {
                clearPendingSceneRequest();
                VttClientEditorNotice.show("Could not delete scene");
            } else {
                VttClientEditorNotice.show("Scene deletion sent to server");
            }
            return;
        }
        if (!session.deleteScene(sceneId)) {
            VttClientEditorNotice.show("Could not delete scene");
        }
    }

    private boolean requestActiveSceneBackground(String assetId) {
        if (session.getActiveScene() == null || !session.isLocalMaster()) return false;
        if (!session.isNetworkAuthorityActive()) {
            return session.setActiveSceneBackground(assetId);
        }
        String sceneId = session.getActiveScene().getId();
        boolean removing = assetId == null || assetId.isBlank();
        String requestId = beginPendingSceneRequest(
                VttSceneCommandPayload.SET_BACKGROUND,
                removing ? "removing scene background" : "changing scene background",
                removing ? "Scene background removed" : "Scene background updated",
                sceneId, sceneId,
                session.getActiveTabletop().getSceneFolder(sceneId), false);
        if (requestId == null) return false;
        if (!session.setActiveSceneBackground(assetId, requestId)) {
            clearPendingSceneRequest();
            VttClientEditorNotice.show("Could not update scene background");
            return false;
        }
        VttClientEditorNotice.show(removing
                ? "Background removal sent to server"
                : "Background change sent to server");
        return true;
    }

    private boolean requestClearActiveSceneMaps() {
        if (session.getActiveScene() == null || !session.isLocalMaster()) return false;
        if (!session.isNetworkAuthorityActive()) {
            if (!session.clearActiveSceneMaps(UUID.randomUUID().toString())) return false;
            VttClientEditorNotice.show("Scene maps cleared");
            return true;
        }
        String sceneId = session.getActiveScene().getId();
        String requestId = beginPendingSceneRequest(
                VttSceneCommandPayload.CLEAR_MAPS, "clearing scene maps",
                "Scene maps cleared", sceneId, sceneId,
                session.getActiveTabletop().getSceneFolder(sceneId), false);
        if (requestId == null) return false;
        if (!session.clearActiveSceneMaps(requestId)) {
            clearPendingSceneRequest();
            VttClientEditorNotice.show("Could not clear scene maps");
            return false;
        }
        VttClientEditorNotice.show("Clear maps request sent to server");
        return true;
    }

    private void beginAssetManagerDeletion(
            AssetManagerOverlay.Section section,
            String id
    ) {
        PendingAssetDeletion deletion = createPendingAssetDeletion(section, id);
        if (deletion == null) return;
        pendingAssetDeletion = deletion;
        assetManagerOverlay.cancelPointerInteraction();
    }

    private void beginAssetManagerBatchDeletion(
            AssetManagerOverlay.Section section,
            List<AssetManagerOverlay.MoveEntry> entries
    ) {
        PendingAssetBatchDeletion deletion = createPendingAssetBatchDeletion(section, entries);
        if (deletion == null) return;
        pendingAssetBatchDeletion = deletion;
        assetManagerOverlay.cancelPointerInteraction();
    }

    private PendingAssetBatchDeletion createPendingAssetBatchDeletion(
            AssetManagerOverlay.Section section,
            List<AssetManagerOverlay.MoveEntry> entries
    ) {
        if (section == null || entries == null || entries.size() < 2) return null;
        List<AssetManagerOverlay.MoveEntry> distinct = entries.stream()
                .filter(entry -> entry != null && entry.id() != null)
                .collect(java.util.stream.Collectors.toMap(
                        entry -> (entry.folder() ? "F:" + entry.path() : "I:" + entry.id()),
                        entry -> entry,
                        (first, ignored) -> first,
                        java.util.LinkedHashMap::new))
                .values().stream().toList();
        if (distinct.size() < 2) return null;

        List<String> blocked = new ArrayList<>();
        List<AssetManagerOverlay.MoveEntry> assets = distinct.stream()
                .filter(entry -> !entry.folder()).toList();
        if (section == AssetManagerOverlay.Section.SCENES
                && session.getActiveTabletop().getSceneIds().size() - assets.size() < 1) {
            blocked.add("A tabletop must retain at least one scene.");
        }
        for (AssetManagerOverlay.MoveEntry entry : assets) {
            if (section == AssetManagerOverlay.Section.MAPS) {
                MapDefinition definition = mapDefinitionRegistry.findById(entry.id()).orElse(null);
                if (!CreatedMapStorage.isUserCreatedMap(definition)) {
                    blocked.add("Map cannot be deleted: " + entry.id());
                } else if (!findDefinitionUsages(section, entry.id()).isEmpty()) {
                    blocked.add("Map is used in a scene: " + definition.displayName());
                }
            } else if (section == AssetManagerOverlay.Section.TOKENS) {
                TokenDefinition definition = tokenDefinitionRegistry.findById(entry.id()).orElse(null);
                if (!CreatedTokenStorage.isUserCreatedToken(definition)) {
                    blocked.add("Token cannot be deleted: " + entry.id());
                } else if (!findDefinitionUsages(section, entry.id()).isEmpty()) {
                    blocked.add("Token is used in a scene: " + definition.displayName());
                }
            } else if (!session.getActiveTabletop().getSceneIds().contains(entry.id())) {
                blocked.add("Scene no longer exists: " + entry.id());
            }
        }
        VttAssetFolderService.Section storageSection =
                VttAssetFolderService.Section.valueOf(section.name());
        for (AssetManagerOverlay.MoveEntry entry : distinct) {
            if (!entry.folder()) continue;
            VttAssetFolderService.FolderInspection inspection =
                    session.inspectAssetFolder(storageSection, entry.path());
            if (!inspection.exists()) {
                blocked.add("Folder no longer exists: " + entry.path());
            } else if (inspection.hasConflicts()) {
                blocked.add("Folder has parent name conflicts: " + entry.path());
            }
        }
        AssetBatchDeleteConfirmationOverlay.Request request =
                new AssetBatchDeleteConfirmationOverlay.Request(
                        section.name().toLowerCase(), assets.size(),
                        distinct.size() - assets.size(), blocked);
        return new PendingAssetBatchDeletion(section, distinct, request);
    }

    private void confirmPendingAssetBatchDeletion() {
        if (pendingAssetBatchDeletion == null) return;
        PendingAssetBatchDeletion refreshed = createPendingAssetBatchDeletion(
                pendingAssetBatchDeletion.section(), pendingAssetBatchDeletion.entries());
        if (refreshed == null) {
            pendingAssetBatchDeletion = null;
            return;
        }
        pendingAssetBatchDeletion = refreshed;
        if (refreshed.request().blocked()) return;
        String encoded = refreshed.entries().stream()
                .map(entry -> entry.folder() ? "F:" + entry.path() : "I:" + entry.id())
                .collect(java.util.stream.Collectors.joining("\n"));
        requestAssetFolderCommand(
                VttAssetFolderCommandPayload.DELETE_SELECTION,
                refreshed.section(), encoded, "", refreshed.entries());
        assetManagerOverlay.clearMultiSelection();
        pendingAssetBatchDeletion = null;
    }

    private boolean handleAssetBatchDeleteConfirmationMouseClicked(
            double mouseX, double mouseY, int button
    ) {
        if (pendingAssetBatchDeletion == null) return false;
        AssetBatchDeleteConfirmationOverlay.Action action =
                assetBatchDeleteConfirmationOverlay.mouseClicked(
                        mouseX, mouseY, button, width, height,
                        pendingAssetBatchDeletion.request());
        if (action == AssetBatchDeleteConfirmationOverlay.Action.DELETE) {
            confirmPendingAssetBatchDeletion();
        } else if (action == AssetBatchDeleteConfirmationOverlay.Action.CANCEL) {
            pendingAssetBatchDeletion = null;
        }
        return true;
    }

    private PendingAssetDeletion createPendingAssetDeletion(
            AssetManagerOverlay.Section section,
            String id
    ) {
        if (section == null || id == null || id.isBlank()) return null;
        String typeLabel;
        String displayName;
        List<String> usages = List.of();
        String blockedMessage = "";
        switch (section) {
            case SCENES -> {
                if (session.getActiveTabletop() == null) return null;
                typeLabel = "Scene";
                displayName = session.getActiveTabletop().getSceneDisplayName(id);
                if (session.getActiveTabletop().getSceneIds().size() <= 1) {
                    blockedMessage = "A tabletop must contain at least one scene.";
                }
            }
            case MAPS -> {
                MapDefinition definition =
                        mapDefinitionRegistry.findById(id).orElse(null);
                if (!CreatedMapStorage.isUserCreatedMap(definition)) return null;
                typeLabel = "Map";
                displayName = definition.displayName();
                usages = findDefinitionUsages(section, id);
                if (!usages.isEmpty()) {
                    blockedMessage = "Remove this map from every scene before deleting it.";
                }
            }
            case TOKENS -> {
                TokenDefinition definition =
                        tokenDefinitionRegistry.findById(id).orElse(null);
                if (!CreatedTokenStorage.isUserCreatedToken(definition)) return null;
                typeLabel = "Token";
                displayName = definition.displayName();
                usages = findDefinitionUsages(section, id);
                if (!usages.isEmpty()) {
                    blockedMessage = "Remove every placed token before deleting this definition.";
                }
            }
            default -> {
                return null;
            }
        }
        AssetDeleteConfirmationOverlay.Request request =
                new AssetDeleteConfirmationOverlay.Request(
                        typeLabel, displayName, usages, blockedMessage);
        return new PendingAssetDeletion(section, id, request);
    }

    private List<String> findDefinitionUsages(
            AssetManagerOverlay.Section section,
            String definitionId
    ) {
        if (session.getActiveTabletop() == null) return List.of();
        List<String> usages = new ArrayList<>();
        String activeSceneId = session.getActiveScene() == null
                ? null : session.getActiveScene().getId();
        for (String sceneId : session.getActiveTabletop().getSceneIds()) {
            VttScene candidate = sceneId.equals(activeSceneId)
                    ? session.getActiveScene()
                    : session.getTabletopStorage().loadScene(
                            session.getActiveTabletop().getId(), sceneId);
            if (candidate == null) continue;
            long count;
            if (section == AssetManagerOverlay.Section.MAPS) {
                count = candidate.getMaps().stream()
                        .filter(map -> map != null
                                && definitionId.equals(map.getSourceMapDefinitionId()))
                        .count();
            } else if (section == AssetManagerOverlay.Section.TOKENS
                    && sceneId.equals(activeSceneId)) {
                count = scene.getObjects().stream()
                        .filter(object -> object != null
                                && definitionId.equals(object.sourceTokenDefinitionId()))
                        .count();
            } else {
                count = candidate.getObjects().stream()
                        .filter(object -> object != null
                                && definitionId.equals(object.getSourceTokenDefinitionId()))
                        .count();
            }
            if (count > 0) {
                usages.add(session.getActiveTabletop().getSceneDisplayName(sceneId)
                        + " (" + count + (count == 1 ? " instance)" : " instances)"));
            }
        }
        return usages;
    }

    private void rememberAssetManagerReturn(AssetManagerOverlay.Section section) {
        returnToAssetManagerSection = section;
        returnToAssetManagerFolder = assetManagerOverlay.currentFolderPath();
    }

    private boolean willReturnToAssetManager(AssetManagerOverlay.Section section) {
        return returnToAssetManagerSection == section;
    }

    private String assetManagerTargetFolder(AssetManagerOverlay.Section section) {
        if (returnToAssetManagerSection != section
                || returnToAssetManagerFolder == null) return "";
        return returnToAssetManagerFolder;
    }

    private void moveCreatedAssetToFolder(
            AssetManagerOverlay.Section section,
            String id,
            String folder
    ) {
        if (section == null || id == null || id.isBlank()
                || folder == null || folder.isBlank()) return;
        requestAssetFolderCommand(
                VttAssetFolderCommandPayload.MOVE_ITEM,
                section, id, folder);
    }

    private void returnToAssetManagerIfRequested() {
        AssetManagerOverlay.Section target = returnToAssetManagerSection;
        if (target == null) {
            returnToAssetManagerFolder = null;
            return;
        }
        String targetFolder = returnToAssetManagerFolder;
        returnToAssetManagerSection = null;
        returnToAssetManagerFolder = null;
        String selectedId = switch (target) {
            case SCENES -> session.getActiveTabletop() == null
                    ? null : session.getActiveTabletop().getActiveSceneId();
            case MAPS -> mapCatalogSelection.getSelectedMapDefinitionId();
            case TOKENS -> tokenCatalogSelection.getSelectedTokenDefinitionId();
        };
        assetManagerOverlay.openFolder(target, targetFolder);
        assetManagerOverlay.select(target, selectedId);
        hudCreationOpen = true;
        hudPlayersOpen = false;
        hudSettingsOpen = false;
        panelVisibility.hideBottomCatalogs();
    }

    private void selectAssetManagerItem(
            AssetManagerOverlay.Section section, String id
    ) {
        if (id == null) return;
        switch (section) {
            case SCENES -> {
            }
            case MAPS -> mapCatalogSelection.select(id);
            case TOKENS -> tokenCatalogSelection.select(id);
        }
    }

    private void syncAssetManagerSelection() {
        selectAssetManagerItem(
                assetManagerOverlay.section(), assetManagerOverlay.selectedId());
    }

    private boolean handleAssetDeleteConfirmationMouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (pendingAssetDeletion == null) return false;
        AssetDeleteConfirmationOverlay.Action action =
                assetDeleteConfirmationOverlay.mouseClicked(
                        mouseX, mouseY, button, this.width, this.height,
                        pendingAssetDeletion.request());
        if (action == AssetDeleteConfirmationOverlay.Action.DELETE) {
            confirmPendingAssetDeletion();
        } else if (action == AssetDeleteConfirmationOverlay.Action.CANCEL) {
            pendingAssetDeletion = null;
        }
        return true;
    }

    private void beginAssetFolderDeletion(
            AssetManagerOverlay.Section section,
            String folder
    ) {
        PendingAssetFolderDeletion deletion =
                createPendingAssetFolderDeletion(section, folder);
        if (deletion == null) return;
        pendingAssetFolderDeletion = deletion;
        assetManagerOverlay.cancelPointerInteraction();
    }

    private PendingAssetFolderDeletion createPendingAssetFolderDeletion(
            AssetManagerOverlay.Section section,
            String folder
    ) {
        if (section == null || folder == null || folder.isBlank()) return null;
        VttAssetFolderService.Section storageSection =
                VttAssetFolderService.Section.valueOf(section.name());
        VttAssetFolderService.FolderInspection inspection =
                session.inspectAssetFolder(storageSection, folder);
        AssetFolderDeleteConfirmationOverlay.Request request =
                new AssetFolderDeleteConfirmationOverlay.Request(
                        inspection.exists(),
                        inspection.folder(),
                        inspection.parentFolder(),
                        inspection.folderCount(),
                        inspection.itemCount(),
                        inspection.conflicts());
        return new PendingAssetFolderDeletion(section, folder, request);
    }

    private boolean handleAssetFolderDeleteConfirmationMouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (pendingAssetFolderDeletion == null) return false;
        AssetFolderDeleteConfirmationOverlay.Action action =
                assetFolderDeleteConfirmationOverlay.mouseClicked(
                        mouseX, mouseY, button, this.width, this.height,
                        pendingAssetFolderDeletion.request());
        if (action == AssetFolderDeleteConfirmationOverlay.Action.DELETE
                || action
                == AssetFolderDeleteConfirmationOverlay.Action.MOVE_AND_DELETE) {
            confirmPendingAssetFolderDeletion();
        } else if (action == AssetFolderDeleteConfirmationOverlay.Action.CANCEL) {
            pendingAssetFolderDeletion = null;
        }
        return true;
    }

    private void confirmPendingAssetFolderDeletion() {
        if (pendingAssetFolderDeletion == null) return;
        PendingAssetFolderDeletion refreshed = createPendingAssetFolderDeletion(
                pendingAssetFolderDeletion.section(),
                pendingAssetFolderDeletion.folder());
        if (refreshed == null) {
            pendingAssetFolderDeletion = null;
            return;
        }
        pendingAssetFolderDeletion = refreshed;
        if (refreshed.request().blocked()) return;

        requestAssetFolderCommand(
                refreshed.request().empty()
                        ? VttAssetFolderCommandPayload.DELETE_FOLDER
                        : VttAssetFolderCommandPayload.MOVE_CONTENTS_AND_DELETE_FOLDER,
                refreshed.section(), refreshed.folder(), "");
        pendingAssetFolderDeletion = null;
    }

    private void confirmPendingAssetDeletion() {
        if (pendingAssetDeletion == null) return;
        PendingAssetDeletion refreshed = createPendingAssetDeletion(
                pendingAssetDeletion.section(), pendingAssetDeletion.id());
        if (refreshed == null) {
            pendingAssetDeletion = null;
            return;
        }
        pendingAssetDeletion = refreshed;
        if (refreshed.request().blocked()) return;

        switch (refreshed.section()) {
            case SCENES -> requestSceneDelete(refreshed.id(), true);
            case MAPS -> deleteMapDefinition(
                    mapDefinitionRegistry.findById(refreshed.id()).orElse(null));
            case TOKENS -> tokenDefinitionRegistry.findById(refreshed.id())
                    .ifPresent(this::deleteTokenDefinitionFromManager);
        }
        pendingAssetDeletion = null;
    }

    private void deleteTokenDefinitionFromManager(TokenDefinition definition) {
        if (!CreatedTokenStorage.isUserCreatedToken(definition)) return;
        if (session.isNetworkAuthorityActive()) {
            requestServerTokenDelete(definition);
        } else {
            deleteTokenDefinition(definition);
            VttClientEditorNotice.show("Token deleted");
        }
    }

    private void requestServerTokenDuplicate(TokenDefinition definition) {
        if (definition == null) return;
        String requestId = beginPendingTokenDefinitionRequest(
                "duplicating token", "Token duplicated", null,
                definition.id(), null);
        if (requestId == null) return;
        if (VttClientTokenDefinitionSync.sendDuplicate(
                requestId, session.getNetworkAuthorityRevision(), definition.id())) {
            VttClientEditorNotice.show("Token duplication sent to server");
        } else {
            clearPendingTokenDefinitionRequest();
            VttClientEditorNotice.show("Could not duplicate token on server");
        }
    }

    private void requestServerTokenDelete(TokenDefinition definition) {
        if (definition == null) return;
        String requestId = beginPendingTokenDefinitionRequest(
                "deleting token", "Token deleted", null,
                definition.id(), null);
        if (requestId == null) return;
        if (VttClientTokenDefinitionSync.sendDelete(
                requestId, session.getNetworkAuthorityRevision(), definition.id())) {
            VttClientEditorNotice.show("Token deletion sent to server");
        } else {
            clearPendingTokenDefinitionRequest();
            VttClientEditorNotice.show("Could not delete token on server");
        }
    }

    private boolean handleEditorSettingsMouseClicked(
            double mouseX, double mouseY, int button
    ) {
        if (!hudSettingsOpen || session.getActiveScene() == null) return false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && editorSettingsOverlay.isEditSceneButtonAt(
                mouseX, mouseY, width, height,
                session.getActiveScene(), session.isLocalMaster())) {
            beginSceneBackgroundEdit();
            return true;
        }
        inputController.beginEditorAction();
        EditorSettingsOverlay.Interaction interaction = editorSettingsOverlay.mouseClicked(
                mouseX, mouseY, button, width, height,
                session.getActiveScene(), session.isLocalMaster());
        if (interaction == EditorSettingsOverlay.Interaction.HUD_THEME_CHANGED) {
            inputController.endEditorAction();
            VttClientEditorNotice.show("HUD theme saved");
            return true;
        } else if (interaction == EditorSettingsOverlay.Interaction.CHANGED) {
            persistGridSettings();
        } else if (interaction == EditorSettingsOverlay.Interaction.APPLY_LIGHTING_COLOR) {
            inputController.endEditorAction();
            VttClientEnvironmentCommandSync.sendLightingColor(session);
            return true;
        } else if (interaction == EditorSettingsOverlay.Interaction.CHOOSE_BACKGROUND) {
            inputController.endEditorAction();
            openMapPicker(MapPickerTarget.ACTIVE_SCENE);
            return true;
        } else if (interaction == EditorSettingsOverlay.Interaction.REMOVE_BACKGROUND) {
            if (!session.getActiveScene().getMaps().isEmpty()) {
                requestClearActiveSceneMaps();
            } else if (requestActiveSceneBackground(null)) {
                if (!session.isNetworkAuthorityActive()) {
                    VttClientEditorNotice.show("Legacy background removed");
                }
            }
            inputController.endEditorAction();
            return true;
        } else if (interaction == EditorSettingsOverlay.Interaction.EDIT_SCENE) {
            inputController.endEditorAction();
            beginSceneBackgroundEdit();
            return true;
        } else if (interaction == EditorSettingsOverlay.Interaction.SET_INITIAL_VIEW) {
            setCurrentCameraAsInitialView();
            inputController.endEditorAction();
            syncInitialCameraView();
            VttClientEditorNotice.show("Current camera saved as initial scene view");
            return true;
        } else if (interaction == EditorSettingsOverlay.Interaction.RESET_INITIAL_VIEW) {
            session.getActiveScene().clearInitialCameraView();
            inputController.endEditorAction();
            syncInitialCameraView();
            VttClientEditorNotice.show("Initial scene view reset");
            return true;
        }
        if (!editorSettingsOverlay.isDraggingOpacity()) {
            inputController.endEditorAction();
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
        mapCatalogContextMenu.close();
        sceneContextMenu.close();
        switch (action) {
            case CLOSE -> this.onClose();
            case PLAYERS -> {
                hudPlayersOpen = !hudPlayersOpen;
                hudSettingsOpen = false;
                hudCreationOpen = false;
            }
            case ASSIGN_SELECTED_TOKEN_OWNER ->
                    setSelectedSceneTokenOwner(editorHudOverlay.getSelectedPlayerId());
            case CLEAR_SELECTED_TOKEN_OWNER -> setSelectedSceneTokenOwner(null);
            case TOGGLE_SELECTED_PLAYER_SPECTATOR -> toggleSelectedPlayerSpectator();
            case FOCUS_SELECTED_PLAYER_TOKEN -> focusSelectedPlayerToken();
            case SETTINGS -> {
                boolean closing = hudSettingsOpen;
                if (closing) {
                    finishGridSettingsDrag();
                    editorSettingsOverlay.cancelDrag();
                }
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
            case UNDO -> {
                requestHistoryAction(false);
            }
            case REDO -> {
                requestHistoryAction(true);
            }
            case SCENES -> {
                if (master) panelVisibility.toggleSceneList();
                closeHudPopups();
            }
            case MAPS -> {
                if (master) panelVisibility.toggleMapCatalog();
                closeHudPopups();
            }
            case TOKENS -> {
                if (master) panelVisibility.toggleTokenCatalog();
                closeHudPopups();
            }
            case CREATION -> {
                if (master) {
                    hudCreationOpen = !hudCreationOpen;
                    if (!hudCreationOpen) {
                        closeAssetFolderDialog();
                        assetManagerOverlay.cancelPointerInteraction();
                    }
                    if (hudCreationOpen) panelVisibility.hideBottomCatalogs();
                    hudPlayersOpen = false;
                    hudSettingsOpen = false;
                }
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

    private void setSelectedSceneTokenOwner(String ownerId) {
        if (!session.isLocalMaster()
                || selectionManager.getSelectedObjectIds().size() != 1) return;
        String objectId = selectionManager.getSelectedObjectIds().iterator().next();
        CanvasObject selected = scene.findObjectById(objectId);
        if (selected == null || !selected.hasSourceTokenDefinition()) return;
        inputController.beginEditorAction();
        try {
            session.setActiveSceneTokenOwner(objectId, ownerId);
        } finally {
            inputController.endEditorAction();
        }
    }

    private void toggleSelectedPlayerSpectator() {
        if (!session.isLocalMaster() || !session.isNetworkAuthorityActive()) return;
        String playerId = editorHudOverlay.getSelectedPlayerId();
        if (playerId == null || playerId.isBlank()) return;
        getConnectedPlayerOptions().stream()
                .filter(player -> playerId.equals(player.id())
                        && player.role() != VttRole.MASTER)
                .findFirst()
                .ifPresent(player -> PacketDistributor.sendToServer(
                        new VttPlayerModeCommandPayload(
                                session.getNetworkAuthorityRevision(),
                                player.id(), !player.spectator())));
    }

    private void focusSelectedPlayerToken() {
        if (!session.isLocalMaster()
                && VttClientPresentationState.isFollowingMasterCamera()) {
            VttClientEditorNotice.show("Camera is following the master");
            return;
        }
        String objectId = editorHudOverlay.getSelectedOwnedTokenId();
        if (objectId == null || objectId.isBlank()) return;
        CanvasObject object = scene.findObjectById(objectId);
        if (object == null) {
            VttClientEditorNotice.show("Token is not available in the active scene");
            return;
        }
        camera.setPosition(object.transform().position());
        if (session.isLocalMaster()) {
            inputController.selectSelectTool();
            selectionManager.selectOnly(object.id());
        }
    }

    private boolean sendPresentationCommand(String operation) {
        if (!session.isLocalMaster() || !session.isNetworkAuthorityActive()) {
            VttClientEditorNotice.show("Presentation controls require server authority");
            return false;
        }
        PacketDistributor.sendToServer(new VttPresentationCommandPayload(
                session.getNetworkAuthorityRevision(),
                operation,
                camera.getPosition().x(),
                camera.getPosition().y(),
                camera.getZoom()));
        return true;
    }

    private void closeHudPopups() {
        finishGridSettingsDrag();
        hudPlayersOpen = false;
        hudSettingsOpen = false;
        hudCreationOpen = false;
        pendingAssetDeletion = null;
        pendingAssetFolderDeletion = null;
        pendingAssetBatchDeletion = null;
        editorSettingsOverlay.cancelDrag();
    }

    private void finishGridSettingsDrag() {
        if (!editorSettingsOverlay.isDraggingOpacity()) return;
        persistGridSettings();
        inputController.endEditorAction();
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
        } else if (session.isLocalSpectator()) {
            graphics.drawCenteredString(this.font, "SPECTATOR - full view / read only",
                    this.width / 2, 70, 0xFF66DDEE);
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

        if (sceneBackgroundEditor.isActive()) {
            CursorManager.reset();
            return;
        }

        if (tokenCreationDraft != null) {
            CursorManager.reset();
            return;
        }

        if (hudCreationOpen && assetManagerOverlay.contains(
                mouseX, mouseY, this.width, this.height)) {
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
        if (handleAssetBatchDeleteConfirmationMouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (handleAssetFolderDeleteConfirmationMouseClicked(
                mouseX, mouseY, button)) {
            return true;
        }
        if (handleAssetDeleteConfirmationMouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (sceneBackgroundEditor.isActive()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && renderState != null) {
                return inputController.mouseClicked(
                        mouseX, mouseY, button, getKeyboardModifiers(), renderState);
            }
            if (handleMapCatalogContextMouseClicked(mouseX, mouseY, button)) return true;
            if (handleEditorHudMouseClicked(mouseX, mouseY, button)) return true;
            if (handleMapCatalogPlacementMouseClicked(mouseX, mouseY, button)) return true;
            if (handleSceneEditOutlinerMouseClicked(mouseX, mouseY, button)) return true;
            return sceneBackgroundEditor.mouseClicked(
                    session.getActiveScene(), renderState, mouseX, mouseY, button);
        }
        if (playerViewPreview) {
            if (handleEditorSettingsMouseClicked(mouseX, mouseY, button)) return true;
            if (handleEditorHudMouseClicked(mouseX, mouseY, button)) return true;
            return renderState == null || inputController.mouseClicked(
                    mouseX, mouseY, button, getKeyboardModifiers(), renderState);
        }
        if (newSceneNameBuffer != null) {
            if (handleMapPickerMouseClicked(mouseX, mouseY, button)) return true;
            return handleNewSceneDialogMouseClicked(mouseX, mouseY, button);
        }
        if (newMapNameBuffer != null) {
            if (handleBackgroundImagePickerMouseClicked(mouseX, mouseY, button)) return true;
            return handleNewMapDialogMouseClicked(mouseX, mouseY, button);
        }
        if (renamingSceneId != null || pendingDeleteSceneId != null) return true;

        if (handleBackgroundImagePickerMouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (handleMapPickerMouseClicked(mouseX, mouseY, button)) return true;

        if (handleTokenCreationMouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (handleAssetManagerMouseClicked(mouseX, mouseY, button)) return true;
        if (handleEditorSettingsMouseClicked(mouseX, mouseY, button)) return true;
        if (handleEditorHudMouseClicked(mouseX, mouseY, button)) return true;

        if (blocksFollowedCameraPointer(button)) return true;
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

        if (mapCatalogContextMenu.isOpen()) {
            MapCatalogContextMenuOverlay.Action action =
                    mapCatalogContextMenuOverlay.getActionAt(
                            mapCatalogContextMenu, mouseX, mouseY);
            if (action != MapCatalogContextMenuOverlay.Action.NONE) {
                handleMapCatalogContextMenuAction(action);
                mapCatalogContextMenu.close();
                return true;
            }
            if (!mapCatalogContextMenuOverlay.containsPoint(
                    mapCatalogContextMenu, mouseX, mouseY)) {
                mapCatalogContextMenu.close();
                return true;
            }
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && panelVisibility.isSceneListVisible()) {
            Optional<String> clickedSceneId = sceneListOverlay.findSceneIdAt(
                    session.getActiveTabletop(), this.width, this.height, mouseX, mouseY);
            if (clickedSceneId.isPresent()) {
                tokenCatalogContextMenu.close();
                mapCatalogContextMenu.close();
                sceneContextMenu.open((int) mouseX + 8, (int) mouseY, clickedSceneId.get());
                return true;
            }
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && panelVisibility.isTokenCatalogVisible()) {
            TokenDefinition clickedToken = tokenCatalogOverlay.findTokenAt(
                    tokenDefinitionRegistry,
                    this.width,
                    this.height,
                    mouseX,
                    mouseY,
                    tokenCatalogController.getScrollOffset()
            );

            if (clickedToken != null) {
                tokenCatalogSelection.select(clickedToken.id());
                mapCatalogContextMenu.close();

                tokenCatalogOverlay.suppressDetailsPopup();

                tokenCatalogContextMenu.open(
                        (int) mouseX + 12,
                        (int) mouseY - 80,
                        clickedToken
                );

                return true;
            }
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && panelVisibility.isMapCatalogVisible()) {
            MapDefinition clickedMap = mapCatalogOverlay.findMapAt(
                    mapDefinitionRegistry, this.width, this.height,
                    mouseX, mouseY, mapCatalogScrollOffset);
            if (clickedMap != null) {
                mapCatalogSelection.select(clickedMap.id());
                tokenCatalogContextMenu.close();
                sceneContextMenu.close();
                mapCatalogContextMenu.open(
                        (int) mouseX + 10, (int) mouseY - 45, clickedMap);
                return true;
            }
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (handleMapCatalogPlacementMouseClicked(mouseX, mouseY, button)) return true;

            TokenCatalogClickResult tokenCatalogClickResult =
                    tokenCatalogController.mouseClicked(
                            tokenCatalogOverlay,
                            tokenDefinitionRegistry,
                            panelVisibility.isTokenCatalogVisible(),
                            this.width,
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
                if (sceneOutlinerOverlay.mouseClickedScrollbar(
                        scene, session.getActiveScene(), mouseX, mouseY)) {
                    return true;
                }
                var clickedMapId = sceneOutlinerOverlay.findMapIdAt(
                        session.getActiveScene(), scene, mouseX, mouseY);
                if (clickedMapId.isPresent()) {
                    beginSceneBackgroundEdit(clickedMapId.get());
                    return true;
                }
                var clickedObjectId = sceneOutlinerOverlay.findObjectIdAt(
                        scene, session.getActiveScene(),
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
                if (sceneListOverlay.openFolderAt(
                        session.getActiveTabletop(), this.width, this.height,
                        mouseX, mouseY)) {
                    return true;
                }
                Optional<String> clickedSceneId = sceneListOverlay.findSceneIdAt(
                        session.getActiveTabletop(), this.width, this.height, mouseX, mouseY);
                if (clickedSceneId.isPresent()) {
                    String sceneId = clickedSceneId.get();
                    boolean requested;
                    if (session.isNetworkAuthorityActive()) {
                        String requestId = beginPendingSceneRequest(
                                VttSceneCommandPayload.SWITCH, "switching scene",
                                "Scene activated", sceneId,
                                session.getActiveTabletop().getActiveSceneId(),
                                session.getActiveTabletop().getSceneFolder(sceneId), false);
                        requested = requestId != null
                                && session.switchToScene(sceneId, requestId);
                        if (requestId != null && !requested) clearPendingSceneRequest();
                    } else {
                        requested = session.switchToScene(sceneId);
                    }
                    if (requested) {
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
                if (applyBackgroundImageSelection(clickedItem)) {
                    closeBackgroundImagePicker();
                }
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

        if (session.isNetworkAuthorityActive() && hasPendingAssetManagerOperation()) {
            tokenCreationDraft.setErrorMessage("Wait for the current Asset Manager operation");
            return;
        }

        String targetFolder =
                assetManagerTargetFolder(AssetManagerOverlay.Section.TOKENS);
        TokenDefinition createdDefinition = CreatedTokenDefinitions.createAndRegister(
                tokenCreationDraft,
                tokenDefinitionRegistry,
                assetRegistry
        );

        if (session.isNetworkAuthorityActive()) {
            String requestId = beginPendingTokenDefinitionRequest(
                    "creating token", "Token created", createdDefinition.id(), null,
                    targetFolder);
            if (requestId == null) return;
            if (!VttClientTokenDefinitionSync.sendUpsert(
                    requestId, session.getNetworkAuthorityRevision(),
                    CreatedTokenStorage.serializeCreatedToken(
                            tokenCreationDraft, createdDefinition))) {
                clearPendingTokenDefinitionRequest();
                tokenCreationDraft.setErrorMessage("Could not send token to server");
                session.requestAssetManagerResync();
                return;
            }
        } else {
            CreatedTokenStorage.saveCreatedToken(tokenCreationDraft, createdDefinition);
            moveCreatedAssetToFolder(
                    AssetManagerOverlay.Section.TOKENS,
                    createdDefinition.id(), targetFolder);
        }

        tokenCatalogSelection.select(createdDefinition.id());

        closeTokenCreationDialog();
    }

    private void saveEditedTokenDefinitionFromDraft() {
        if (tokenCreationDraft == null) {
            return;
        }
        if (session.isNetworkAuthorityActive() && hasPendingAssetManagerOperation()) {
            tokenCreationDraft.setErrorMessage("Wait for the current Asset Manager operation");
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
            String requestId = beginPendingTokenDefinitionRequest(
                    "updating token", "Token updated", updatedDefinition.id(),
                    updatedDefinition.id(), null);
            if (requestId == null) return;
            if (!VttClientTokenDefinitionSync.sendUpsert(
                    requestId, session.getNetworkAuthorityRevision(),
                    CreatedTokenStorage.serializeEditedToken(tokenCreationDraft))) {
                clearPendingTokenDefinitionRequest();
                tokenCreationDraft.setErrorMessage("Could not send token to server");
                session.requestAssetManagerResync();
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
        returnToAssetManagerIfRequested();
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
                    requestServerTokenDuplicate(tokenDefinition);
                } else {
                    duplicateTokenDefinition(tokenDefinition);
                }
            }

            case DELETE -> {
                if (session.isNetworkAuthorityActive()) {
                    requestServerTokenDelete(tokenDefinition);
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
        if (pendingAssetDeletion != null || pendingAssetFolderDeletion != null
                || pendingAssetBatchDeletion != null) return true;
        if (sceneBackgroundEditor.isActive()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && renderState != null) {
                return inputController.mouseReleased(
                        mouseX, mouseY, button, getKeyboardModifiers(), renderState);
            }
            if (releaseMapCatalogInteraction(mouseX, mouseY, button)) return true;
            if (sceneOutlinerOverlay.mouseReleasedScrollbar()) return true;
            return sceneBackgroundEditor.mouseReleased(button);
        }
        if (hudCreationOpen) {
            if (assetFolderNameBuffer != null) return true;
            AssetManagerOverlay.Interaction interaction =
                    assetManagerOverlay.mouseReleased(
                            mouseX, mouseY, button, this.width, this.height,
                            session.getActiveTabletop(), mapDefinitionRegistry,
                            tokenDefinitionRegistry);
            handleAssetManagerInteraction(interaction);
            return true;
        }
        if (hudSettingsOpen && session.getActiveScene() != null
                && editorSettingsOverlay.mouseReleased(
                mouseX, button, this.width, this.height,
                session.getActiveScene(), session.isLocalMaster())) {
            persistGridSettings();
            inputController.endEditorAction();
            return true;
        }
        if (playerViewPreview) {
            return renderState == null || inputController.mouseReleased(
                    mouseX, mouseY, button, getKeyboardModifiers(), renderState);
        }
        if (assetCatalogController.mouseReleased()) return true;
        if (tokenCreationDialog.mouseReleased()) return true;
        if (tokenCatalogController.releaseScrollbar()) return true;
        if (releaseMapCatalogInteraction(mouseX, mouseY, button)) return true;
        if (sceneOutlinerOverlay.mouseReleasedScrollbar()) return true;
        if (backgroundImagePickerActive) {
            return true;
        }
        if (mapPickerActive) return true;
        if (newSceneNameBuffer != null) return true;
        if (newMapNameBuffer != null) return true;

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
        if (pendingAssetDeletion != null || pendingAssetFolderDeletion != null
                || pendingAssetBatchDeletion != null) return true;
        if (sceneBackgroundEditor.isActive()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && renderState != null) {
                return inputController.mouseDragged(
                        mouseX, mouseY, button, dragX, dragY,
                        getKeyboardModifiers(), renderState);
            }
            if (draggingMapCatalogScrollbar) {
                mapCatalogScrollOffset = mapCatalogOverlay.scrollOffsetFromMouse(
                        mapDefinitionRegistry, this.height, mouseY);
                return true;
            }
            if (draggingMapDefinition != null) return true;
            if (sceneOutlinerOverlay.mouseDraggedScrollbar(
                    scene, session.getActiveScene(), mouseY)) return true;
            return sceneBackgroundEditor.mouseDragged(
                    session.getActiveScene(), renderState, mouseX, mouseY,
                    getKeyboardModifiers());
        }
        if (hudCreationOpen) {
            if (assetFolderNameBuffer != null) return true;
            assetManagerOverlay.mouseDragged(
                    mouseX, mouseY, button, this.width, this.height,
                    session.getActiveTabletop(), mapDefinitionRegistry,
                    tokenDefinitionRegistry);
            return true;
        }
        if (hudSettingsOpen && session.getActiveScene() != null
                && editorSettingsOverlay.mouseDragged(
                mouseX, this.width, this.height,
                session.getActiveScene(), session.isLocalMaster())) {
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
        if (draggingMapCatalogScrollbar) {
            mapCatalogScrollOffset = mapCatalogOverlay.scrollOffsetFromMouse(
                    mapDefinitionRegistry, this.height, mouseY);
            return true;
        }
        if (draggingMapDefinition != null) return true;
        if (sceneOutlinerOverlay.mouseDraggedScrollbar(
                scene, session.getActiveScene(), mouseY)) return true;
        if (backgroundImagePickerActive) {
            return true;
        }
        if (newSceneNameBuffer != null) return true;
        if (newMapNameBuffer != null) return true;

        if (tokenCreationDraft != null) {
            return true;
        }

        if (tokenCatalogController.getDraggingTokenDefinition() != null) {
            return true;
        }

        if (blocksFollowedCameraPointer(button)) return true;
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
        if (pendingAssetDeletion != null || pendingAssetFolderDeletion != null
                || pendingAssetBatchDeletion != null) return true;
        if (sceneBackgroundEditor.isActive()) {
            if (panelVisibility.isMapCatalogVisible()
                    && mapCatalogOverlay.contains(
                    mapDefinitionRegistry, this.width, this.height, mouseX, mouseY)) {
                mapCatalogScrollOffset = mapCatalogOverlay.clampScrollOffset(
                        mapDefinitionRegistry,
                        mapCatalogScrollOffset
                                + (scrollY < 0 ? 1 : scrollY > 0 ? -1 : 0));
                return true;
            }
            if (editorHudOverlay.containsHud(
                    mouseX, mouseY, this.width, this.height, editorHudState())) return true;
            if (panelVisibility.isSceneOutlinerVisible()
                    && sceneOutlinerOverlay.mouseScrolled(
                    scene, session.getActiveScene(), mouseX, mouseY, scrollY)) return true;
            return renderState == null || inputController.mouseScrolled(
                    mouseX, mouseY, scrollX, scrollY, renderState);
        }
        if (hudCreationOpen) {
            assetManagerOverlay.mouseScrolled(
                    mouseX, mouseY, scrollY, this.width, this.height,
                    session.getActiveTabletop(), mapDefinitionRegistry,
                    tokenDefinitionRegistry);
            return true;
        }
        if (!backgroundImagePickerActive && !mapPickerActive
                && hudSettingsOpen && editorSettingsOverlay.contains(
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
        if (mapPickerActive) {
            mapCatalogScrollOffset = mapCatalogOverlay.clampScrollOffset(
                    mapDefinitionRegistry,
                    mapCatalogScrollOffset + (scrollY < 0 ? 1 : scrollY > 0 ? -1 : 0));
            return true;
        }
        if (newSceneNameBuffer != null) return true;
        if (newMapNameBuffer != null) return true;

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
                panelVisibility.isTokenCatalogVisible(), this.width, this.height,
                mouseX, mouseY, scrollY)) {
            return true;
        }

        if (panelVisibility.isMapCatalogVisible()
                && mapCatalogOverlay.contains(
                mapDefinitionRegistry, this.width, this.height, mouseX, mouseY)) {
            mapCatalogScrollOffset = mapCatalogOverlay.clampScrollOffset(
                    mapDefinitionRegistry,
                    mapCatalogScrollOffset + (scrollY < 0 ? 1 : scrollY > 0 ? -1 : 0));
            return true;
        }

        if (panelVisibility.isSceneOutlinerVisible()
                && sceneOutlinerOverlay.mouseScrolled(
                scene, session.getActiveScene(), mouseX, mouseY, scrollY)) {
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

        if (!session.isLocalMaster()
                && VttClientPresentationState.isFollowingMasterCamera()) return true;
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
        if (pendingAssetBatchDeletion != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (!pendingAssetBatchDeletion.request().blocked()) {
                    confirmPendingAssetBatchDeletion();
                }
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                pendingAssetBatchDeletion = null;
            }
            return true;
        }
        if (pendingAssetFolderDeletion != null) {
            if (keyCode == GLFW.GLFW_KEY_F5) {
                refreshAssetManagerFolders();
            } else if (keyCode == GLFW.GLFW_KEY_ENTER
                    || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (!pendingAssetFolderDeletion.request().blocked()) {
                    confirmPendingAssetFolderDeletion();
                }
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                pendingAssetFolderDeletion = null;
            }
            return true;
        }
        if (pendingAssetDeletion != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (!pendingAssetDeletion.request().blocked()) {
                    confirmPendingAssetDeletion();
                }
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                pendingAssetDeletion = null;
            }
            return true;
        }
        if (sceneBackgroundEditor.isActive()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirmSceneBackgroundEdit();
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelSceneBackgroundEdit();
            } else if (keyCode == GLFW.GLFW_KEY_R) {
                sceneBackgroundEditor.reset(session.getActiveScene());
            } else if (keyCode == GLFW.GLFW_KEY_DELETE
                    || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (sceneBackgroundEditor.deleteSelectedMap(session.getActiveScene())) {
                    VttClientEditorNotice.show("Scene map removed");
                }
            } else if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
                sceneBackgroundEditor.moveSelectedLayer(
                        session.getActiveScene(), SceneBackgroundEditor.LayerMove.UP);
            } else if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
                sceneBackgroundEditor.moveSelectedLayer(
                        session.getActiveScene(), SceneBackgroundEditor.LayerMove.DOWN);
            } else if (keyCode == GLFW.GLFW_KEY_HOME) {
                sceneBackgroundEditor.moveSelectedLayer(
                        session.getActiveScene(), SceneBackgroundEditor.LayerMove.TOP);
            } else if (keyCode == GLFW.GLFW_KEY_END) {
                sceneBackgroundEditor.moveSelectedLayer(
                        session.getActiveScene(), SceneBackgroundEditor.LayerMove.BOTTOM);
            }
            return true;
        }
        if (hudCreationOpen) {
            if (assetFolderNameBuffer != null) {
                if (keyCode == GLFW.GLFW_KEY_ENTER
                        || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    confirmAssetFolderDialog();
                } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    closeAssetFolderDialog();
                } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE
                        && !assetFolderNameBuffer.isEmpty()) {
                    assetFolderNameBuffer = assetFolderNameBuffer.substring(
                            0, assetFolderNameBuffer.length() - 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_F5) {
                refreshAssetManagerFolders();
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (!assetManagerOverlay.cancelActiveDrag()
                        && !assetManagerOverlay.clearMultiSelection()) {
                    hudCreationOpen = false;
                }
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                assetManagerOverlay.backspace(
                        session.getActiveTabletop(), mapDefinitionRegistry,
                        tokenDefinitionRegistry);
                syncAssetManagerSelection();
            }
            return true;
        }
        if (hudSettingsOpen && editorSettingsOverlay.keyPressed(
                keyCode, session.getActiveScene(), session.isLocalMaster())) {
            persistGridSettings();
            return true;
        }
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
                returnToAssetManagerIfRequested();
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !sceneRenameBuffer.isEmpty()) {
                sceneRenameBuffer = sceneRenameBuffer.substring(0, sceneRenameBuffer.length() - 1);
            }
            return true;
        }

        if (pendingDeleteSceneId != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                requestSceneDelete(
                        pendingDeleteSceneId,
                        returnToAssetManagerSection == AssetManagerOverlay.Section.SCENES);
                pendingDeleteSceneId = null;
                if (!session.isNetworkAuthorityActive()) returnToAssetManagerIfRequested();
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                pendingDeleteSceneId = null;
                returnToAssetManagerIfRequested();
            }
            return true;
        }

        if (newSceneNameBuffer != null && mapPickerActive) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeMapPicker();
                return true;
            }
            return true;
        }

        if (newSceneNameBuffer != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirmNewScene();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeNewSceneDialog();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !newSceneNameBuffer.isEmpty()) {
                newSceneNameBuffer = newSceneNameBuffer.substring(0, newSceneNameBuffer.length() - 1);
            }
            return true;
        }

        if (newMapNameBuffer != null && backgroundImagePickerActive) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeBackgroundImagePicker();
                return true;
            }
            if (assetCatalogController.keyPressed(
                    keyCode, getKeyboardModifiers())) return true;
            return true;
        }

        if (newMapNameBuffer != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirmNewMap();
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeNewMapDialog();
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !newMapNameBuffer.isEmpty()) {
                newMapNameBuffer = newMapNameBuffer.substring(0, newMapNameBuffer.length() - 1);
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
        if (mapPickerActive) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) closeMapPicker();
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
            hudCreationOpen = false;
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
            hudCreationOpen = false;
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
            inputController.clearEditorHistory();
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

        if (keyCode == GLFW.GLFW_KEY_B && tokenCreationDraft == null
                && !assetCatalogController.isSearchActive()) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            if (sendPresentationCommand(VttPresentationCommandPayload.TOGGLE_BLACKOUT)) {
                VttClientEditorNotice.show("Player blackout toggled");
            }
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
            return true;
        }

        if (assetCatalogController.keyPressed(keyCode, getKeyboardModifiers())) {
            return true;
        }

        boolean controlDown = (getKeyboardModifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        if (controlDown && keyCode == GLFW.GLFW_KEY_Z) {
            if ((getKeyboardModifiers() & GLFW.GLFW_MOD_SHIFT) != 0) {
                requestHistoryAction(true);
            } else {
                requestHistoryAction(false);
            }
            return true;
        }

        if (controlDown && keyCode == GLFW.GLFW_KEY_Y) {
            requestHistoryAction(true);
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
            if ((getKeyboardModifiers() & GLFW.GLFW_MOD_SHIFT) != 0) {
                inputController.toggleCollisionBoxEditor(renderState);
            } else if ((getKeyboardModifiers() & GLFW.GLFW_MOD_ALT) != 0) {
                if (sendPresentationCommand(
                        VttPresentationCommandPayload.TOGGLE_CAMERA_FOLLOW)) {
                    VttClientEditorNotice.show("Player camera follow toggled");
                }
            } else {
                if (sendPresentationCommand(VttPresentationCommandPayload.SYNC_CAMERA)) {
                    VttClientEditorNotice.show("Camera position sent to players");
                }
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_W) {
            if (!session.getLocalRole().canEditTabletop()) return true;
            selectionManager.clearSelection();
            inputController.selectWallTool();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_F) {
            if (hasFlippableSelectedTokens()) {
                inputController.flipSelectedObjectsHorizontally();
            } else if (session.getLocalRole().canEditTabletop()) {
                selectionManager.clearSelection();
                inputController.selectFogTool();
            }
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
        inputController.beginEditorAction();
        try {
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
        } finally {
            inputController.endEditorAction();
        }
    }

    private void toggleSelectedTokenOwnership() {
        inputController.beginEditorAction();
        try {
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
        } finally {
            inputController.endEditorAction();
        }
    }

    private boolean canTransformSelectedTokens() {
        if (session.isLocalSpectator()) return false;
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

    private boolean hasFlippableSelectedTokens() {
        if (selectionManager.getSelectedObjectIds().isEmpty()) return false;
        for (String selectedId : selectionManager.getSelectedObjectIds()) {
            CanvasObject selected = scene.findObjectById(selectedId);
            if (selected == null || !selected.hasSourceTokenDefinition()) return false;
        }
        return canTransformSelectedTokens();
    }

    private void adjustTokenVisionOuterRadius(double delta) {
        inputController.beginEditorAction();
        try {
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
        } finally {
            inputController.endEditorAction();
        }
    }

    private void adjustTokenVisionInnerRadius(double delta) {
        inputController.beginEditorAction();
        try {
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
        } finally {
            inputController.endEditorAction();
        }
    }

    private double resolveInnerRadius(com.petrick.vtt.feature.tabletop.VttSceneObject object) {
        return object.getVisionInnerRadius();
    }

    private void resetTokenVisionRadii() {
        inputController.beginEditorAction();
        try {
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
        } finally {
            inputController.endEditorAction();
        }
    }

    private void toggleSelectedTokenVision() {
        inputController.beginEditorAction();
        try {
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
        } finally {
            inputController.endEditorAction();
        }
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
            closeNewSceneDialog();
            closeNewMapDialog();
            cancelRename();
            inputController.selectHandTool();
            closeHudPopups();
        }
        VTT.LOGGER.info("Player view preview {}", playerViewPreview ? "enabled" : "disabled");
    }

    private boolean applyNewSceneBackgroundSelection(AssetCatalogItem item) {
        if (newSceneNameBuffer == null || item == null
                || !isSelectableBackgroundImage(item)) return false;
        ResourceLocation texture = null;
        int textureWidth = 0;
        int textureHeight = 0;
        if (item instanceof AssetCatalogItem.RegisteredAsset registered
                && registered.assetRef() instanceof BuiltInTextureAssetRef builtIn) {
            texture = builtIn.texture();
            textureWidth = builtIn.textureWidth();
            textureHeight = builtIn.textureHeight();
        } else {
            AssetThumbnail thumbnail = session.getAssetThumbnailRegistry()
                    .findById(item.id()).orElse(null);
            if (thumbnail != null) {
                texture = thumbnail.texture();
                textureWidth = thumbnail.width();
                textureHeight = thumbnail.height();
            }
        }
        if (texture == null || textureWidth <= 0 || textureHeight <= 0) return false;
        newSceneBackgroundAssetId = item.id();
        newSceneBackgroundDisplayName = item.displayName();
        newSceneBackgroundPreviewTexture = texture;
        newSceneBackgroundPreviewWidth = textureWidth;
        newSceneBackgroundPreviewHeight = textureHeight;
        VTT.LOGGER.info("[VTT Background] Selected background for new scene: {}", item.id());
        return true;
    }

    private boolean applyBackgroundImageSelection(AssetCatalogItem item) {
        if (backgroundPickerTarget == BackgroundPickerTarget.NEW_SCENE) {
            return applyNewSceneBackgroundSelection(item);
        }
        if (backgroundPickerTarget == BackgroundPickerTarget.NEW_MAP) {
            return applyNewMapImageSelection(item);
        }
        if (backgroundPickerTarget == BackgroundPickerTarget.ACTIVE_SCENE
                && isSelectableBackgroundImage(item)) {
            inputController.beginEditorAction();
            boolean changed = requestActiveSceneBackground(item.id());
            inputController.endEditorAction();
            if (changed) {
                if (!session.isNetworkAuthorityActive()) {
                    VttClientEditorNotice.show("Scene background changed");
                }
                VTT.LOGGER.info(
                        "[VTT Background] Active scene background changed to {}", item.id());
            }
            return changed;
        }
        return false;
    }

    private void beginSceneBackgroundEdit() {
        beginSceneBackgroundEdit(null);
    }

    private void beginSceneBackgroundEdit(String selectedMapId) {
        if (!session.isLocalMaster()) {
            VTT.LOGGER.warn("[VTT Scene Edit] Rejected because local user is not master");
            VttClientEditorNotice.show("Only masters can edit scene maps");
            return;
        }
        if (session.getActiveScene() == null
                || session.getActiveScene().getMaps().isEmpty()
                && session.getActiveScene().getBackgroundAssetId() == null) {
            VTT.LOGGER.warn("[VTT Scene Edit] Rejected because the scene has no maps");
            VttClientEditorNotice.show("Add a map to the scene first");
            return;
        }
        closeHudPopups();
        selectionManager.clearSelection();
        if (!sceneBackgroundEditor.begin(session.getActiveScene())) {
            VttClientEditorNotice.show("Could not resolve the selected map size");
            return;
        }
        if (selectedMapId != null) {
            sceneBackgroundEditor.selectMap(session.getActiveScene(), selectedMapId);
        }
        if (session.isNetworkAuthorityActive()) {
            VttClientEnvironmentCommandSync.setMapPreviewActive(true);
        }
        inputController.beginEditorAction();
        VttClientEditorNotice.show("Scene map edit mode enabled");
    }

    private void confirmSceneBackgroundEdit() {
        if (!sceneBackgroundEditor.isActive()) return;
        sceneBackgroundEditor.confirm();
        VttClientEnvironmentCommandSync.setMapPreviewActive(false);
        inputController.endEditorAction();
        syncSceneMetadata();
        if (session.isNetworkAuthorityActive()) {
            VttClientEnvironmentCommandSync.flushSceneMaps(session);
            VttClientEditorNotice.show("Scene map changes sent to server");
        } else {
            VttClientEditorNotice.show("Scene map changes applied");
        }
    }

    private void cancelSceneBackgroundEdit() {
        if (!sceneBackgroundEditor.isActive()) return;
        sceneBackgroundEditor.cancel(session.getActiveScene());
        VttClientEnvironmentCommandSync.setMapPreviewActive(false);
        inputController.endEditorAction();
        VttClientEditorNotice.show("Scene map edit cancelled");
    }

    private void requestHistoryAction(boolean redo) {
        if (!session.isLocalMaster() || session.getActiveScene() == null) return;
        if (sceneBackgroundEditor.isActive()) {
            VttClientEditorNotice.show("Finish Scene Edit before using undo/redo");
            return;
        }
        if (!session.isNetworkAuthorityActive()) {
            boolean changed = redo
                    ? inputController.redoEditorAction()
                    : inputController.undoEditorAction();
            if (changed) syncSceneMetadata();
            return;
        }
        if (pendingHistoryRequestId != null || hasPendingAssetManagerOperation()) {
            VttClientEditorNotice.show("Wait for the current server operation");
            return;
        }

        var prepared = redo
                ? inputController.prepareRedoEditorAction()
                : inputController.prepareUndoEditorAction();
        if (prepared == null) return;
        String targetSceneJson = prepared.targetSceneJson();
        if (targetSceneJson.length() > VttSceneHistoryCommandPayload.MAX_SCENE_JSON_LENGTH) {
            VttClientEditorNotice.show("Scene is too large for network undo/redo");
            return;
        }
        VttClientSceneHistorySync.begin(session.getNetworkSnapshotVersion());
        boolean changed = redo
                ? inputController.redoEditorAction()
                : inputController.undoEditorAction();
        if (!changed) {
            VttClientSceneHistorySync.finish();
            return;
        }
        String operation = redo
                ? VttSceneHistoryCommandPayload.REDO
                : VttSceneHistoryCommandPayload.UNDO;
        pendingHistoryRequestId = UUID.randomUUID().toString();
        pendingHistorySuccessMessage = redo ? "Redo applied" : "Undo applied";
        pendingHistoryAcknowledgedRevision = -1L;
        pendingHistoryStartedSnapshotVersion = session.getNetworkSnapshotVersion();
        pendingHistoryRequestUntil = System.currentTimeMillis() + 120_000L;
        pendingHistoryRejected = false;
        PacketDistributor.sendToServer(new VttSceneHistoryCommandPayload(
                pendingHistoryRequestId, session.getNetworkAuthorityRevision(), operation,
                session.getActiveScene().getId(), prepared.expectedFingerprint(), targetSceneJson));
        VttClientEditorNotice.show((redo ? "Redo" : "Undo") + " sent to server");
    }

    private void handleNetworkHistorySnapshot() {
        long version = session.getNetworkSnapshotVersion();
        if (version == observedNetworkSnapshotVersion) return;
        observedNetworkSnapshotVersion = version;
        if (session.isNetworkAuthorityActive() && pendingHistoryRequestId == null) {
            inputController.clearEditorHistory();
        }
    }

    private void resolvePendingHistoryResult() {
        if (pendingHistoryRequestId == null) return;
        VttSceneCommandResultPayload result =
                VttClientSceneCommandResultState.consume(pendingHistoryRequestId);
        if (result != null) {
            if (result.success()) {
                pendingHistoryAcknowledgedRevision = result.authorityRevision();
                if (!result.message().isBlank()) pendingHistorySuccessMessage = result.message();
            } else {
                pendingHistoryRejected = true;
                pendingHistorySuccessMessage = result.message().isBlank()
                        ? "The server rejected undo/redo" : result.message();
                session.requestSceneHistoryResync();
            }
        }

        boolean receivedCorrection = session.getNetworkSnapshotVersion()
                > pendingHistoryStartedSnapshotVersion;
        boolean successReady = pendingHistoryAcknowledgedRevision >= 0L
                && session.getNetworkAuthorityRevision()
                >= pendingHistoryAcknowledgedRevision;
        if (receivedCorrection && (pendingHistoryRejected || successReady)) {
            finishPendingHistoryRequest();
            return;
        }
        if (System.currentTimeMillis() > pendingHistoryRequestUntil) {
            if (!pendingHistoryRejected) {
                pendingHistoryRejected = true;
                pendingHistorySuccessMessage = "Undo/redo timed out; resynchronizing";
                pendingHistoryRequestUntil = Long.MAX_VALUE;
                session.requestSceneHistoryResync();
            }
        }
    }

    private void finishPendingHistoryRequest() {
        boolean rejected = pendingHistoryRejected;
        String message = pendingHistorySuccessMessage;
        pendingHistoryRequestId = null;
        pendingHistorySuccessMessage = null;
        pendingHistoryAcknowledgedRevision = -1L;
        pendingHistoryStartedSnapshotVersion = -1L;
        pendingHistoryRequestUntil = 0L;
        pendingHistoryRejected = false;
        VttClientSceneHistorySync.finish();
        if (rejected) inputController.clearEditorHistory();
        selectionManager.removeMissingObjects(scene);
        VttClientEditorNotice.show(message == null || message.isBlank()
                ? (rejected ? "Undo/redo rejected" : "Undo/redo applied") : message);
    }

    private void syncSceneMetadata() {
        if (!session.isNetworkAuthorityActive()) {
            session.saveActiveTabletopAndScene();
            return;
        }
        VttClientEnvironmentCommandSync.sendBackgroundTransform(session);
        VttClientEnvironmentCommandSync.sendInitialCameraView(session);
    }

    private void syncInitialCameraView() {
        if (!session.isNetworkAuthorityActive()) {
            session.saveActiveTabletopAndScene();
            return;
        }
        VttClientEnvironmentCommandSync.sendInitialCameraView(session);
    }

    private void setCurrentCameraAsInitialView() {
        if (!session.isLocalMaster() || session.getActiveScene() == null) return;
        Vec2d position = camera.getPosition();
        session.getActiveScene().setInitialCameraView(new VttSceneCameraView(
                position.x(), position.y(), camera.getZoom()));
    }

    private void applyActiveSceneInitialCamera() {
        if (session.getActiveScene() == null
                || (!session.isLocalMaster()
                && VttClientPresentationState.isFollowingMasterCamera())) return;
        VttSceneCameraView view = session.getActiveScene().getInitialCameraView();
        if (view == null) return;
        camera.setPosition(new Vec2d(view.getX(), view.getY()));
        camera.setZoom(view.getZoom());
    }

    private void openBackgroundImagePicker(BackgroundPickerTarget target) {
        backgroundPickerTarget = target;
        backgroundImagePickerActive = true;
        assetCatalogSelection.clear();
        lastBackgroundImagePickerClickedItemId = null;
        lastBackgroundImagePickerClickTime = 0L;
    }

    private void openMapPicker(MapPickerTarget target) {
        mapPickerTarget = target;
        mapPickerActive = true;
        panelVisibility.hideBottomCatalogs();
        mapCatalogSelection.clear();
        lastMapPickerClickedId = null;
        lastMapPickerClickTime = 0L;
        draggingMapDefinition = null;
        draggingMapCatalogScrollbar = false;
    }

    private void closeMapPicker() {
        mapPickerActive = false;
        mapPickerTarget = MapPickerTarget.NONE;
        lastMapPickerClickedId = null;
        lastMapPickerClickTime = 0L;
        draggingMapDefinition = null;
        draggingMapCatalogScrollbar = false;
    }

    private boolean handleMapPickerMouseClicked(double mouseX, double mouseY, int button) {
        if (!mapPickerActive) return false;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        if (mapCatalogOverlay.isScrollbarAt(
                mapDefinitionRegistry, this.width, this.height, mouseX, mouseY)) {
            draggingMapCatalogScrollbar = true;
            mapCatalogScrollOffset = mapCatalogOverlay.scrollOffsetFromMouse(
                    mapDefinitionRegistry, this.height, mouseY);
            return true;
        }
        if (mapCatalogOverlay.openFolderAt(
                mapDefinitionRegistry, this.width, this.height,
                mouseX, mouseY, mapCatalogScrollOffset)) {
            mapCatalogScrollOffset = 0;
            mapCatalogSelection.clear();
            return true;
        }
        MapDefinition clicked = mapCatalogOverlay.findMapAt(
                mapDefinitionRegistry, this.width, this.height,
                mouseX, mouseY, mapCatalogScrollOffset);
        if (clicked == null) return true;
        mapCatalogSelection.select(clicked.id());
        long now = System.currentTimeMillis();
        boolean doubleClick = clicked.id().equals(lastMapPickerClickedId)
                && now - lastMapPickerClickTime <= 350L;
        lastMapPickerClickedId = clicked.id();
        lastMapPickerClickTime = now;
        if (!doubleClick) return true;
        if (mapPickerTarget == MapPickerTarget.NEW_SCENE) {
            selectInitialSceneMap(clicked);
        } else if (mapPickerTarget == MapPickerTarget.ACTIVE_SCENE) {
            placeMapDefinitionAtWorld(clicked, new Vec2d(0.0, 0.0));
        }
        closeMapPicker();
        return true;
    }

    private void selectInitialSceneMap(MapDefinition definition) {
        newSceneMapDefinition = definition;
        newSceneBackgroundAssetId = definition.assetId();
        newSceneBackgroundDisplayName = definition.displayName();
        MapPreview preview = resolveMapPreview(definition);
        newSceneBackgroundPreviewTexture = preview == null ? null : preview.texture();
        newSceneBackgroundPreviewWidth = preview == null ? 0 : preview.width();
        newSceneBackgroundPreviewHeight = preview == null ? 0 : preview.height();
    }

    private MapPreview resolveMapPreview(MapDefinition definition) {
        if (definition == null) return null;
        AssetThumbnail thumbnail = session.getAssetThumbnailRegistry()
                .findById(definition.assetId()).orElse(null);
        if (thumbnail == null && definition.assetId().startsWith("library:")) {
            thumbnail = session.getAssetThumbnailRegistry()
                    .findById(definition.assetId().substring("library:".length())).orElse(null);
        }
        if (thumbnail != null) {
            return new MapPreview(thumbnail.texture(), thumbnail.width(), thumbnail.height());
        }
        String assetId = definition.assetId().startsWith("registered:")
                ? definition.assetId().substring("registered:".length()) : definition.assetId();
        if (assetRegistry.findById(assetId).orElse(null) instanceof BuiltInTextureAssetRef builtIn) {
            return new MapPreview(
                    builtIn.texture(), builtIn.textureWidth(), builtIn.textureHeight());
        }
        return null;
    }

    private void closeBackgroundImagePicker() {
        backgroundImagePickerActive = false;
        backgroundPickerTarget = BackgroundPickerTarget.NONE;
        lastBackgroundImagePickerClickedItemId = null;
        lastBackgroundImagePickerClickTime = 0L;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (pendingAssetDeletion != null || pendingAssetFolderDeletion != null
                || pendingAssetBatchDeletion != null) return true;
        if (sceneBackgroundEditor.isActive()) return true;
        if (hudCreationOpen) {
            if (assetFolderNameBuffer != null) {
                if (isAllowedRenameCharacter(codePoint)
                        && assetFolderNameBuffer.length() < 64
                        && codePoint != '/' && codePoint != '\\'
                        && codePoint != ':' && codePoint != '*'
                        && codePoint != '?' && codePoint != '"'
                        && codePoint != '<' && codePoint != '>'
                        && codePoint != '|') {
                    assetFolderNameBuffer += codePoint;
                }
                return true;
            }
            if (assetManagerOverlay.charTyped(
                    codePoint, session.getActiveTabletop(),
                    mapDefinitionRegistry, tokenDefinitionRegistry)) {
                syncAssetManagerSelection();
            }
            return true;
        }
        if (hudSettingsOpen && editorSettingsOverlay.charTyped(
                codePoint, session.getActiveScene(), session.isLocalMaster())) {
            persistGridSettings();
            return true;
        }
        if (renamingSceneId != null) {
            if (isAllowedRenameCharacter(codePoint) && sceneRenameBuffer.length() < 48) {
                sceneRenameBuffer += codePoint;
            }
            return true;
        }
        if (newSceneNameBuffer != null && mapPickerActive) {
            return true;
        }
        if (newSceneNameBuffer != null) {
            if (isAllowedRenameCharacter(codePoint) && newSceneNameBuffer.length() < 48) {
                newSceneNameBuffer += codePoint;
            }
            return true;
        }
        if (newMapNameBuffer != null && backgroundImagePickerActive) {
            assetCatalogController.charTyped(codePoint);
            return true;
        }
        if (newMapNameBuffer != null) {
            if (isAllowedRenameCharacter(codePoint) && newMapNameBuffer.length() < 48) {
                newMapNameBuffer += codePoint;
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
        Map<String, Integer> ownedTokens = new HashMap<>();
        if (session.getActiveScene() != null) {
            session.getActiveScene().getObjects().stream()
                    .filter(object -> object != null && object.getOwnerId() != null)
                    .forEach(object -> ownedTokens.merge(object.getOwnerId(), 1, Integer::sum));
        }
        if (session.isNetworkAuthorityActive()
                && !session.getNetworkPlayerRoster().isEmpty()) {
            return session.getNetworkPlayerRoster().stream()
                    .map(entry -> new VttPlayerOption(
                            entry.id(), entry.displayName(), entry.role(),
                            ownedTokens.getOrDefault(entry.id(), 0), entry.spectator()))
                    .sorted(Comparator.comparing(
                            VttPlayerOption::displayName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return List.of();
        return connection.getOnlinePlayers().stream()
                .map(info -> {
                    String id = info.getProfile().getId().toString();
                    VttRole role = id.equals(session.getLocalPlayerId())
                            ? session.getLocalRole() : VttRole.PLAYER;
                    return new VttPlayerOption(id, info.getProfile().getName(), role,
                            ownedTokens.getOrDefault(id, 0), false);
                })
                .sorted(Comparator.comparing(VttPlayerOption::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<VttOwnedTokenOption> getOwnedTokenOptions() {
        if (session.getActiveScene() == null) return List.of();
        String sceneId = session.getActiveScene().getId();
        String sceneName = session.getActiveScene().getDisplayName();
        return session.getActiveScene().getObjects().stream()
                .filter(object -> object != null
                        && object.getOwnerId() != null
                        && object.getSourceTokenDefinitionId() != null)
                .map(object -> new VttOwnedTokenOption(
                        object.getId(),
                        object.getDisplayName(),
                        object.getOwnerId(),
                        sceneId,
                        sceneName,
                        object.getState().isVisible(),
                        object.isVisionEnabled()))
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

        inputController.beginTokenLifecycleChange();
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
        inputController.endTokenLifecycleChange();
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

        inputController.renameObject(renamingObjectId, renameBuffer);

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
        int border = EditorHudTheme.outline();
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
        String targetFolder =
                assetManagerTargetFolder(AssetManagerOverlay.Section.SCENES);
        if (session.isNetworkAuthorityActive()) {
            boolean reopenManager =
                    willReturnToAssetManager(AssetManagerOverlay.Section.SCENES);
            String requestId = beginPendingSceneRequest(
                    VttSceneCommandPayload.CREATE, "creating scene", "Scene created",
                    null, session.getActiveTabletop().getActiveSceneId(),
                    targetFolder, reopenManager);
            if (requestId == null) return;
            if (!session.requestCreateScene(
                    newSceneNameBuffer, newSceneMapDefinition, requestId)) {
                clearPendingSceneRequest();
                VttClientEditorNotice.show("Could not create scene");
                return;
            }
            selectionManager.clearSelection();
            inputController.selectHandTool();
            closeNewSceneDialog();
            VttClientEditorNotice.show("Scene creation sent to server");
            return;
        }
        if (session.requestCreateScene(newSceneNameBuffer, newSceneMapDefinition)) {
            String createdSceneId = session.getActiveTabletop().getActiveSceneId();
            moveCreatedAssetToFolder(
                    AssetManagerOverlay.Section.SCENES, createdSceneId, targetFolder);
            selectionManager.clearSelection();
            inputController.selectHandTool();
            closeNewSceneDialog();
        }
    }

    private void beginNewMapDialog() {
        editingMapDefinitionId = null;
        newMapNameBuffer = "";
        newMapTextureMode = MapTextureMode.STRETCH;
        newMapTextureModeListOpen = false;
        clearNewMapImage();
    }

    private void beginEditSelectedMapDialog() {
        MapDefinition definition = selectedMapDefinition();
        if (!CreatedMapStorage.isUserCreatedMap(definition)) {
            VttClientEditorNotice.show("Select a user-created map first");
            return;
        }
        editingMapDefinitionId = definition.id();
        newMapNameBuffer = definition.displayName();
        newMapTextureMode = definition.textureMode();
        newMapTextureModeListOpen = false;
        newMapAssetId = definition.assetId();
        newMapAssetDisplayName = definition.assetId();
        MapPreview preview = resolveMapPreview(definition);
        newMapPreviewTexture = preview == null ? null : preview.texture();
        newMapPreviewWidth = definition.imageWidth();
        newMapPreviewHeight = definition.imageHeight();
    }

    private void confirmNewMap() {
        if (newMapNameBuffer == null || newMapNameBuffer.isBlank()) return;
        if (newMapAssetId == null || newMapPreviewWidth <= 0 || newMapPreviewHeight <= 0) {
            VttClientEditorNotice.show("Choose a static image for the map");
            return;
        }
        try {
            String targetFolder =
                    assetManagerTargetFolder(AssetManagerOverlay.Section.MAPS);
            MapDefinition existing = editingMapDefinitionId == null ? null
                    : mapDefinitionRegistry.findById(editingMapDefinitionId).orElse(null);
            MapDefinition definition;
            if (existing == null) {
                definition = session.isNetworkAuthorityActive()
                        ? CreatedMapStorage.createDefinition(
                        newMapNameBuffer, newMapAssetId, newMapPreviewWidth,
                        newMapPreviewHeight, newMapTextureMode)
                        : CreatedMapStorage.createAndSave(
                        newMapNameBuffer, newMapAssetId, newMapPreviewWidth,
                        newMapPreviewHeight, newMapTextureMode);
            } else {
                definition = session.isNetworkAuthorityActive()
                        ? CreatedMapStorage.updateDefinition(
                        existing, newMapNameBuffer, newMapAssetId,
                        newMapPreviewWidth, newMapPreviewHeight, newMapTextureMode)
                        : CreatedMapStorage.updateAndSave(
                        existing, newMapNameBuffer, newMapAssetId,
                        newMapPreviewWidth, newMapPreviewHeight, newMapTextureMode);
            }
            if (session.isNetworkAuthorityActive()) {
                String folder = existing == null
                        ? targetFolder : mapDefinitionRegistry.folderOf(existing.id());
                String requestId = beginPendingMapDefinitionRequest(
                        existing == null ? "creating map" : "updating map",
                        existing == null ? "Map created" : "Map updated",
                        definition.id(), existing == null ? null : existing.id());
                if (requestId == null) return;
                if (!VttClientMapDefinitionSync.sendUpsert(
                        requestId, session.getNetworkAuthorityRevision(),
                        definition, folder)) {
                    clearPendingMapDefinitionRequest();
                    VttClientEditorNotice.show("Could not send map to server");
                    return;
                }
                boolean returningToManager =
                        willReturnToAssetManager(AssetManagerOverlay.Section.MAPS);
                closeNewMapDialog();
                if (!returningToManager && !panelVisibility.isMapCatalogVisible()) {
                    panelVisibility.toggleMapCatalog();
                }
                VttClientEditorNotice.show(existing == null
                        ? "Map creation sent to server" : "Map update sent to server");
                return;
            }
            mapDefinitionRegistry.register(
                    definition, CreatedMapStorage.folderOf(definition));
            if (existing == null) {
                moveCreatedAssetToFolder(
                        AssetManagerOverlay.Section.MAPS,
                        definition.id(), targetFolder);
            }
            if (existing != null && session.getActiveScene() != null) {
                session.getActiveScene().getMaps().stream()
                        .filter(map -> map != null
                                && definition.id().equals(map.getSourceMapDefinitionId()))
                        .forEach(map -> {
                            map.getTransform().setScaleX(
                                    map.getTransform().getScaleX()
                                            * existing.imageWidth() / definition.imageWidth());
                            map.getTransform().setScaleY(
                                    map.getTransform().getScaleY()
                                            * existing.imageHeight() / definition.imageHeight());
                            map.setDisplayName(definition.displayName());
                            map.setAssetId(definition.assetId());
                            map.setTextureMode(definition.textureMode());
                        });
                if (!session.isNetworkAuthorityActive()) {
                    session.saveActiveTabletopAndScene();
                }
            }
            mapCatalogSelection.select(definition.id());
            boolean returningToManager =
                    willReturnToAssetManager(AssetManagerOverlay.Section.MAPS);
            closeNewMapDialog();
            if (!returningToManager && !panelVisibility.isMapCatalogVisible()) {
                panelVisibility.toggleMapCatalog();
            }
            VttClientEditorNotice.show((existing == null ? "Map created: " : "Map updated: ")
                    + definition.displayName());
        } catch (RuntimeException exception) {
            VTT.LOGGER.error("Failed to create VTT map", exception);
            VttClientEditorNotice.show("Could not create map");
        }
    }

    private double mapDragDistance(double mouseX, double mouseY) {
        return Math.hypot(mouseX - mapDragStartX, mouseY - mapDragStartY);
    }

    private boolean handleMapCatalogPlacementMouseClicked(
            double mouseX, double mouseY, int button
    ) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT
                || !panelVisibility.isMapCatalogVisible()) return false;
        if (mapCatalogOverlay.isScrollbarAt(
                mapDefinitionRegistry, this.width, this.height, mouseX, mouseY)) {
            draggingMapCatalogScrollbar = true;
            mapCatalogScrollOffset = mapCatalogOverlay.scrollOffsetFromMouse(
                    mapDefinitionRegistry, this.height, mouseY);
            return true;
        }
        if (mapCatalogOverlay.openFolderAt(
                mapDefinitionRegistry, this.width, this.height,
                mouseX, mouseY, mapCatalogScrollOffset)) {
            mapCatalogScrollOffset = 0;
            mapCatalogSelection.clear();
            return true;
        }
        MapDefinition clickedMap = mapCatalogOverlay.findMapAt(
                mapDefinitionRegistry, this.width, this.height,
                mouseX, mouseY, mapCatalogScrollOffset);
        if (clickedMap != null) {
            mapCatalogSelection.select(clickedMap.id());
            draggingMapDefinition = clickedMap;
            mapDragStartX = mouseX;
            mapDragStartY = mouseY;
            return true;
        }
        return mapCatalogOverlay.contains(
                mapDefinitionRegistry, this.width, this.height, mouseX, mouseY);
    }

    private boolean handleMapCatalogContextMouseClicked(
            double mouseX, double mouseY, int button
    ) {
        if (mapCatalogContextMenu.isOpen()) {
            MapCatalogContextMenuOverlay.Action action =
                    mapCatalogContextMenuOverlay.getActionAt(
                            mapCatalogContextMenu, mouseX, mouseY);
            if (action != MapCatalogContextMenuOverlay.Action.NONE) {
                handleMapCatalogContextMenuAction(action);
                mapCatalogContextMenu.close();
                return true;
            }
            if (!mapCatalogContextMenuOverlay.containsPoint(
                    mapCatalogContextMenu, mouseX, mouseY)) {
                mapCatalogContextMenu.close();
                return true;
            }
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT
                || !panelVisibility.isMapCatalogVisible()) return false;
        MapDefinition clicked = mapCatalogOverlay.findMapAt(
                mapDefinitionRegistry, this.width, this.height,
                mouseX, mouseY, mapCatalogScrollOffset);
        if (clicked == null) return mapCatalogOverlay.contains(
                mapDefinitionRegistry, this.width, this.height, mouseX, mouseY);
        mapCatalogSelection.select(clicked.id());
        tokenCatalogContextMenu.close();
        sceneContextMenu.close();
        mapCatalogContextMenu.open(
                (int) mouseX + 10, (int) mouseY - 45, clicked);
        return true;
    }

    private boolean handleSceneEditOutlinerMouseClicked(
            double mouseX, double mouseY, int button
    ) {
        if (!panelVisibility.isSceneOutlinerVisible()) return false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && sceneOutlinerOverlay.mouseClickedScrollbar(
                scene, session.getActiveScene(), mouseX, mouseY)) return true;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            Optional<String> mapId = sceneOutlinerOverlay.findMapIdAt(
                    session.getActiveScene(), scene, mouseX, mouseY);
            if (mapId.isPresent()) {
                sceneBackgroundEditor.selectMap(session.getActiveScene(), mapId.get());
                return true;
            }
        }
        return sceneOutlinerOverlay.contains(
                scene, session.getActiveScene(), mouseX, mouseY);
    }

    private boolean releaseMapCatalogInteraction(
            double mouseX, double mouseY, int button
    ) {
        if (draggingMapCatalogScrollbar) {
            draggingMapCatalogScrollbar = false;
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || draggingMapDefinition == null) {
            return false;
        }
        MapDefinition definition = draggingMapDefinition;
        boolean place = mapDragDistance(mouseX, mouseY) >= 6.0
                && !mapCatalogOverlay.contains(
                mapDefinitionRegistry, this.width, this.height, mouseX, mouseY)
                && !editorHudOverlay.containsHud(
                mouseX, mouseY, this.width, this.height, editorHudState());
        draggingMapDefinition = null;
        if (place) placeMapDefinition(definition, mouseX, mouseY);
        return true;
    }

    private void placeMapDefinition(
            MapDefinition definition, double screenX, double screenY
    ) {
        if (renderState == null) return;
        placeMapDefinitionAtWorld(
                definition, renderState.screenToWorld(new Vec2d(screenX, screenY)));
    }

    private void placeMapDefinitionAtWorld(MapDefinition definition, Vec2d worldPosition) {
        if (!session.isLocalMaster() || session.getActiveScene() == null
                || definition == null || worldPosition == null
                || session.getActiveScene().getMaps().size()
                >= com.petrick.vtt.feature.tabletop.VttSceneLimits.MAX_MAPS) {
            VttClientEditorNotice.show("Could not place map");
            return;
        }
        String id = "map_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        boolean sceneEditActive = sceneBackgroundEditor.isActive();
        if (!sceneEditActive) inputController.beginEditorAction();
        VttSceneMap map = new VttSceneMap(
                id, definition.displayName(), definition.id(), definition.assetId(),
                definition.textureMode());
        map.getTransform().setX(worldPosition.x());
        map.getTransform().setY(worldPosition.y());
        int topLayer = session.getActiveScene().getMaps().stream()
                .filter(value -> value != null)
                .mapToInt(VttSceneMap::getLayerIndex)
                .max().orElse(-1) + 1;
        map.setLayerIndex(topLayer);
        session.getActiveScene().addMap(map);
        if (sceneEditActive) {
            sceneBackgroundEditor.selectMap(session.getActiveScene(), map.getId());
        } else {
            inputController.endEditorAction();
            if (session.isNetworkAuthorityActive()) {
                VttClientEnvironmentCommandSync.flushSceneMaps(session);
            } else {
                session.saveActiveTabletopAndScene();
            }
        }
        VttClientEditorNotice.show("Map placed: " + definition.displayName());
    }

    private void closeNewMapDialog() {
        newMapNameBuffer = null;
        editingMapDefinitionId = null;
        newMapTextureModeListOpen = false;
        clearNewMapImage();
        returnToAssetManagerIfRequested();
        closeBackgroundImagePicker();
    }

    private void handleMapCatalogContextMenuAction(
            MapCatalogContextMenuOverlay.Action action
    ) {
        MapDefinition definition = mapCatalogContextMenu.getMapDefinition();
        if (definition == null) return;
        switch (action) {
            case EDIT -> {
                mapCatalogSelection.select(definition.id());
                if (sceneBackgroundEditor.isActive()) {
                    confirmSceneBackgroundEdit();
                }
                beginEditSelectedMapDialog();
            }
            case DUPLICATE -> duplicateMapDefinition(definition);
            case DELETE -> deleteMapDefinition(definition);
            case NONE -> {
            }
        }
    }

    private void duplicateMapDefinition(MapDefinition definition) {
        if (session.isNetworkAuthorityActive()) {
            String requestId = beginPendingMapDefinitionRequest(
                    "duplicating map", "Map duplicated", null, definition.id());
            if (requestId == null) return;
            if (VttClientMapDefinitionSync.sendDuplicate(
                    requestId, session.getNetworkAuthorityRevision(), definition.id())) {
                VttClientEditorNotice.show("Map duplication sent to server");
            } else {
                clearPendingMapDefinitionRequest();
                VttClientEditorNotice.show("Could not duplicate map on server");
            }
            return;
        }
        try {
            MapDefinition duplicate = CreatedMapStorage.duplicate(definition);
            mapDefinitionRegistry.register(
                    duplicate, CreatedMapStorage.folderOf(duplicate));
            mapCatalogSelection.select(duplicate.id());
            VttClientEditorNotice.show("Map duplicated: " + duplicate.displayName());
        } catch (RuntimeException exception) {
            VTT.LOGGER.error("Failed to duplicate VTT map", exception);
            VttClientEditorNotice.show("Could not duplicate map");
        }
    }

    private void deleteMapDefinition(MapDefinition definition) {
        if (!CreatedMapStorage.isUserCreatedMap(definition)) {
            VttClientEditorNotice.show("Select a user-created map first");
            return;
        }
        if (session.isNetworkAuthorityActive()) {
            String requestId = beginPendingMapDefinitionRequest(
                    "deleting map", "Map deleted", null, definition.id());
            if (requestId == null) return;
            if (VttClientMapDefinitionSync.sendDelete(
                    requestId, session.getNetworkAuthorityRevision(), definition.id())) {
                VttClientEditorNotice.show("Map deletion sent to server");
            } else {
                clearPendingMapDefinitionRequest();
                VttClientEditorNotice.show("Could not delete map on server");
            }
            return;
        }
        if (!CreatedMapStorage.delete(definition)) {
            VttClientEditorNotice.show("Could not delete map");
            return;
        }
        mapDefinitionRegistry.removeById(definition.id());
        mapCatalogSelection.clear();
        VttClientEditorNotice.show(
                "Map deleted from catalog; placed scene maps were preserved");
    }

    private void clearNewMapImage() {
        newMapAssetId = null;
        newMapAssetDisplayName = null;
        newMapPreviewTexture = null;
        newMapPreviewWidth = 0;
        newMapPreviewHeight = 0;
    }

    private boolean applyNewMapImageSelection(AssetCatalogItem item) {
        if (newMapNameBuffer == null || !isSelectableBackgroundImage(item)) return false;
        ResourceLocation texture = null;
        int imageWidth = 0;
        int imageHeight = 0;
        if (item instanceof AssetCatalogItem.RegisteredAsset registered
                && registered.assetRef() instanceof BuiltInTextureAssetRef builtIn) {
            texture = builtIn.texture();
            imageWidth = builtIn.textureWidth();
            imageHeight = builtIn.textureHeight();
        } else if (item instanceof AssetCatalogItem.LibraryFile libraryFile) {
            AssetThumbnail thumbnail = session.getAssetThumbnailRegistry()
                    .findById(libraryFile.entry().id()).orElse(null);
            if (thumbnail == null) {
                thumbnail = session.getAssetThumbnailRegistry().findById(item.id()).orElse(null);
            }
            if (thumbnail != null) {
                texture = thumbnail.texture();
                imageWidth = thumbnail.width();
                imageHeight = thumbnail.height();
            }
        }
        if (texture == null || imageWidth <= 0 || imageHeight <= 0) return false;
        newMapAssetId = item.id();
        newMapAssetDisplayName = item.displayName();
        newMapPreviewTexture = texture;
        newMapPreviewWidth = imageWidth;
        newMapPreviewHeight = imageHeight;
        return true;
    }

    private void beginNewSceneDialog() {
        newSceneNameBuffer = "";
        clearNewSceneBackground();
    }

    private void closeNewSceneDialog() {
        newSceneNameBuffer = null;
        clearNewSceneBackground();
        closeMapPicker();
        returnToAssetManagerIfRequested();
    }

    private void clearNewSceneBackground() {
        newSceneMapDefinition = null;
        newSceneBackgroundAssetId = null;
        newSceneBackgroundDisplayName = null;
        newSceneBackgroundPreviewTexture = null;
        newSceneBackgroundPreviewWidth = 0;
        newSceneBackgroundPreviewHeight = 0;
    }

    private void handleSceneContextMenuAction(SceneContextMenuOverlay.Action action) {
        String sceneId = sceneContextMenu.getSceneId();
        sceneContextMenu.close();
        if (sceneId == null) return;
        if (action == SceneContextMenuOverlay.Action.DUPLICATE) {
            requestSceneDuplicate(sceneId, false);
        } else if (action == SceneContextMenuOverlay.Action.RENAME) {
            renamingSceneId = sceneId;
            sceneRenameBuffer = session.getActiveTabletop().getSceneDisplayName(sceneId);
        } else if (action == SceneContextMenuOverlay.Action.DELETE
                && session.getActiveTabletop().getSceneIds().size() > 1) {
            pendingDeleteSceneId = sceneId;
        }
    }

    private void confirmSceneRename() {
        if (renamingSceneId == null || sceneRenameBuffer == null || sceneRenameBuffer.isBlank()) return;
        if (session.isNetworkAuthorityActive()) {
            String sceneId = renamingSceneId;
            boolean reopenManager =
                    returnToAssetManagerSection == AssetManagerOverlay.Section.SCENES;
            String requestId = beginPendingSceneRequest(
                    VttSceneCommandPayload.RENAME, "renaming scene", "Scene renamed",
                    sceneId, sceneId,
                    session.getActiveTabletop().getSceneFolder(sceneId), reopenManager);
            if (requestId == null) return;
            if (!session.renameScene(sceneId, sceneRenameBuffer, requestId)) {
                clearPendingSceneRequest();
                VttClientEditorNotice.show("Could not rename scene");
                return;
            }
            renamingSceneId = null;
            sceneRenameBuffer = null;
            VttClientEditorNotice.show("Scene rename sent to server");
            return;
        }
        if (session.renameScene(renamingSceneId, sceneRenameBuffer)) {
            renamingSceneId = null;
            sceneRenameBuffer = null;
            returnToAssetManagerIfRequested();
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

    private void renderAssetFolderDialog(VRenderContext context) {
        int dialogWidth = 340;
        int dialogHeight = 120;
        int x = (context.screenWidth() - dialogWidth) / 2;
        int y = (context.screenHeight() - dialogHeight) / 2;
        renderSceneDialogFrame(context, x, y, dialogWidth, dialogHeight);

        String title = renamingAssetFolder ? "Rename Folder" : "Create Folder";
        context.graphics().drawCenteredString(
                this.font, title, x + dialogWidth / 2, y + 12, 0xFFFFFFFF);
        context.graphics().drawString(
                this.font, "Name", x + 14, y + 36, 0xFFCCCCCC, false);
        context.graphics().fill(
                x + 54, y + 31, x + dialogWidth - 14, y + 52, 0xFF17171C);
        context.graphics().hLine(
                x + 54, x + dialogWidth - 14, y + 31,
                EditorHudTheme.opaqueSelection());
        context.graphics().hLine(
                x + 54, x + dialogWidth - 14, y + 52,
                EditorHudTheme.opaqueSelection());
        context.graphics().vLine(
                x + 54, y + 31, y + 52,
                EditorHudTheme.opaqueSelection());
        context.graphics().vLine(
                x + dialogWidth - 14, y + 31, y + 52,
                EditorHudTheme.opaqueSelection());
        context.graphics().drawString(
                this.font, assetFolderNameBuffer + "_",
                x + 61, y + 38, 0xFFFFFFFF, false);

        int buttonY = y + 73;
        int buttonWidth = 128;
        renderAssetFolderDialogButton(
                context, x + 14, buttonY, buttonWidth,
                renamingAssetFolder ? "Rename" : "Create", true);
        renderAssetFolderDialogButton(
                context, x + dialogWidth - buttonWidth - 14,
                buttonY, buttonWidth, "Cancel", false);
    }

    private void renderAssetFolderDialogButton(
            VRenderContext context,
            int x,
            int y,
            int width,
            String label,
            boolean primary
    ) {
        context.graphics().fill(
                x, y, x + width, y + 28,
                primary ? EditorHudTheme.selection() : 0xFF303036);
        context.graphics().hLine(
                x, x + width, y, EditorHudTheme.outline());
        context.graphics().hLine(
                x, x + width, y + 28, EditorHudTheme.outline());
        context.graphics().vLine(
                x, y, y + 28, EditorHudTheme.outline());
        context.graphics().vLine(
                x + width, y, y + 28, EditorHudTheme.outline());
        context.graphics().drawCenteredString(
                this.font, label, x + width / 2, y + 10, 0xFFFFFFFF);
    }

    private boolean handleAssetFolderDialogMouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        int dialogWidth = 340;
        int dialogHeight = 120;
        int x = (this.width - dialogWidth) / 2;
        int y = (this.height - dialogHeight) / 2;
        int buttonY = y + 73;
        int buttonWidth = 128;
        if (mouseX >= x + 14 && mouseX <= x + 14 + buttonWidth
                && mouseY >= buttonY && mouseY <= buttonY + 28) {
            confirmAssetFolderDialog();
        } else if (mouseX >= x + dialogWidth - buttonWidth - 14
                && mouseX <= x + dialogWidth - 14
                && mouseY >= buttonY && mouseY <= buttonY + 28) {
            closeAssetFolderDialog();
        }
        return true;
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
        context.graphics().hLine(x, x + width, y, EditorHudTheme.outline());
        context.graphics().hLine(
                x, x + width, y + height, EditorHudTheme.outline());
        context.graphics().vLine(x, y, y + height, EditorHudTheme.outline());
        context.graphics().vLine(
                x + width, y, y + height, EditorHudTheme.outline());
    }

    private void handleActiveSceneChange() {
        String activeSceneId = session.getActiveScene() == null
                ? null : session.getActiveScene().getId();
        if (java.util.Objects.equals(observedActiveSceneId, activeSceneId)) return;
        if (sceneBackgroundEditor.isActive()) cancelSceneBackgroundEdit();
        observedActiveSceneId = activeSceneId;
        selectionManager.clearSelection();
        inputController.clearEditorHistory();
        inputController.selectHandTool();
        tokenCatalogContextMenu.close();
        sceneContextMenu.close();
        renamingObjectId = null;
        renameBuffer = null;
        closeNewSceneDialog();
        closeNewMapDialog();
        tokenCreationDraft = null;
        renamingSceneId = null;
        sceneRenameBuffer = null;
        pendingDeleteSceneId = null;
        closeBackgroundImagePicker();
        tokenImagePickerActive = false;
        closeHudPopups();
        applyActiveSceneInitialCamera();
        initialCameraApplied = true;
        VTT.LOGGER.info("Editor changed to active scene: {}", activeSceneId);
    }

    private void renderNewSceneDialog(VRenderContext context) {
        int width = 380;
        int height = 170;
        int x = context.screenWidth() / 2 - width / 2;
        int y = context.screenHeight() / 2 - height / 2;
        renderSceneDialogFrame(context, x, y, width, height);
        context.graphics().drawString(this.font, "Create Scene", x + 10, y + 10, 0xFFFFFFFF, false);
        String sceneFolder =
                assetManagerTargetFolder(AssetManagerOverlay.Section.SCENES);
        if (!sceneFolder.isBlank()) {
            context.graphics().drawString(
                    this.font, "Create in: Assets/" + ellipsize(sceneFolder, 25),
                    x + 128, y + 10, 0xFFAAAAAA, false);
        }
        context.graphics().drawString(this.font, "Name:", x + 10, y + 30,
                0xFFAAAAAA, false);
        context.graphics().fill(x + 55, y + 24, x + width - 10, y + 43, 0xCC111116);
        sceneDialogBorder(context, x + 55, y + 24, width - 65, 19, 0xFFFFAA44);
        context.graphics().drawString(this.font, newSceneNameBuffer + "_",
                x + 61, y + 30, 0xFFFFFFFF, false);

        int previewX = x + 10;
        int previewY = y + 52;
        int previewSize = 80;
        context.graphics().fill(previewX, previewY,
                previewX + previewSize, previewY + previewSize, 0xCC111116);
        sceneDialogBorder(context, previewX, previewY, previewSize, previewSize, 0xFF77777D);
        if (newSceneBackgroundPreviewTexture != null) {
            int[] fitted = fitPreview(
                    newSceneBackgroundPreviewWidth, newSceneBackgroundPreviewHeight,
                    previewSize - 4);
            int imageX = previewX + (previewSize - fitted[0]) / 2;
            int imageY = previewY + (previewSize - fitted[1]) / 2;
            context.graphics().blit(
                    newSceneBackgroundPreviewTexture,
                    imageX, imageY, fitted[0], fitted[1],
                    0.0F, 0.0F,
                    newSceneBackgroundPreviewWidth, newSceneBackgroundPreviewHeight,
                    newSceneBackgroundPreviewWidth, newSceneBackgroundPreviewHeight);
        } else {
            context.graphics().drawCenteredString(
                    this.font, "No map", previewX + previewSize / 2,
                    previewY + previewSize / 2 - 4, 0xFF88888E);
        }

        renderNewSceneButton(context, x + 100, y + 55, 260, 22,
                "Choose Initial Map", true);
        renderNewSceneButton(context, x + 100, y + 82, 260, 22,
                "Use No Initial Map", newSceneMapDefinition != null);
        String selectedBackground = newSceneBackgroundDisplayName == null
                ? "Initial map: none"
                : "Initial map: " + ellipsize(newSceneBackgroundDisplayName, 31);
        context.graphics().drawString(this.font, selectedBackground,
                x + 100, y + 113,
                newSceneBackgroundAssetId == null ? 0xFFAAAAAA : 0xFFFFFFFF, false);

        renderNewSceneButton(context, x + 100, y + 140, 110, 20,
                "Create", !newSceneNameBuffer.isBlank());
        renderNewSceneButton(context, x + 250, y + 140, 110, 20,
                "Cancel", true);
    }

    private void renderNewMapDialog(VRenderContext context) {
        int width = 380;
        int height = 202;
        int x = context.screenWidth() / 2 - width / 2;
        int y = context.screenHeight() / 2 - height / 2;
        renderSceneDialogFrame(context, x, y, width, height);
        context.graphics().drawString(this.font,
                editingMapDefinitionId == null ? "Create Map" : "Edit Map",
                x + 10, y + 10,
                0xFFFFFFFF, false);
        String mapFolder =
                assetManagerTargetFolder(AssetManagerOverlay.Section.MAPS);
        if (editingMapDefinitionId == null && !mapFolder.isBlank()) {
            context.graphics().drawString(
                    this.font, "Create in: Assets/" + ellipsize(mapFolder, 25),
                    x + 128, y + 10, 0xFFAAAAAA, false);
        }
        context.graphics().drawString(this.font, "Name:", x + 10, y + 30,
                0xFFAAAAAA, false);
        context.graphics().fill(x + 55, y + 24, x + width - 10, y + 43, 0xCC111116);
        sceneDialogBorder(context, x + 55, y + 24, width - 65, 19, 0xFF66CCFF);
        context.graphics().drawString(this.font, newMapNameBuffer + "_",
                x + 61, y + 30, 0xFFFFFFFF, false);

        int previewX = x + 10;
        int previewY = y + 52;
        int previewSize = 80;
        context.graphics().fill(previewX, previewY,
                previewX + previewSize, previewY + previewSize, 0xCC111116);
        sceneDialogBorder(context, previewX, previewY, previewSize, previewSize, 0xFF77777D);
        if (newMapPreviewTexture != null) {
            int[] fitted = fitPreview(newMapPreviewWidth, newMapPreviewHeight, previewSize - 4);
            int imageX = previewX + (previewSize - fitted[0]) / 2;
            int imageY = previewY + (previewSize - fitted[1]) / 2;
            context.graphics().blit(
                    newMapPreviewTexture, imageX, imageY, fitted[0], fitted[1],
                    0.0F, 0.0F, newMapPreviewWidth, newMapPreviewHeight,
                    newMapPreviewWidth, newMapPreviewHeight);
        } else {
            context.graphics().drawCenteredString(this.font, "No image",
                    previewX + previewSize / 2, previewY + previewSize / 2 - 4, 0xFF88888E);
        }

        renderNewSceneButton(context, x + 100, y + 55, 260, 22,
                "Choose Image", true);
        String selectedImage = newMapAssetDisplayName == null
                ? "Image: none" : "Image: " + ellipsize(newMapAssetDisplayName, 34);
        context.graphics().drawString(this.font, selectedImage, x + 100, y + 87,
                newMapAssetId == null ? 0xFFAAAAAA : 0xFFFFFFFF, false);
        if (newMapAssetId != null) {
            context.graphics().drawString(this.font,
                    "Original size: " + newMapPreviewWidth + " x " + newMapPreviewHeight,
                    x + 100, y + 103, 0xFFAAAAAA, false);
        }

        context.graphics().drawString(this.font, "Texture:", x + 100, y + 119,
                0xFFAAAAAA, false);
        renderNewSceneButton(context, x + 170, y + 113, 190, 20,
                formatMapTextureMode(newMapTextureMode) + "  v", true);
        if (newMapTextureModeListOpen) {
            renderNewSceneButton(context, x + 170, y + 134, 190, 18,
                    "Stretch", true);
            renderNewSceneButton(context, x + 170, y + 153, 190, 18,
                    "Repeat", true);
        }

        renderNewSceneButton(context, x + 100, y + 174, 110, 20,
                editingMapDefinitionId == null ? "Create" : "Save",
                newMapAssetId != null && !newMapNameBuffer.isBlank());
        renderNewSceneButton(context, x + 250, y + 174, 110, 20, "Cancel", true);
    }

    private boolean handleNewMapDialogMouseClicked(
            double mouseX, double mouseY, int button
    ) {
        if (newMapNameBuffer == null) return false;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        int dialogX = this.width / 2 - 190;
        int dialogY = this.height / 2 - 101;
        if (inside(mouseX, mouseY, dialogX + 100, dialogY + 55, 260, 22)) {
            openBackgroundImagePicker(BackgroundPickerTarget.NEW_MAP);
            return true;
        }
        if (inside(mouseX, mouseY, dialogX + 170, dialogY + 113, 190, 20)) {
            newMapTextureModeListOpen = !newMapTextureModeListOpen;
            return true;
        }
        if (newMapTextureModeListOpen
                && inside(mouseX, mouseY, dialogX + 170, dialogY + 134, 190, 18)) {
            newMapTextureMode = MapTextureMode.STRETCH;
            newMapTextureModeListOpen = false;
            return true;
        }
        if (newMapTextureModeListOpen
                && inside(mouseX, mouseY, dialogX + 170, dialogY + 153, 190, 18)) {
            newMapTextureMode = MapTextureMode.REPEAT;
            newMapTextureModeListOpen = false;
            return true;
        }
        if (inside(mouseX, mouseY, dialogX + 100, dialogY + 174, 110, 20)) {
            confirmNewMap();
            return true;
        }
        if (inside(mouseX, mouseY, dialogX + 250, dialogY + 174, 110, 20)) {
            closeNewMapDialog();
            return true;
        }
        return true;
    }

    private String formatMapTextureMode(MapTextureMode mode) {
        return mode == MapTextureMode.REPEAT ? "Repeat" : "Stretch";
    }

    private boolean handleNewSceneDialogMouseClicked(
            double mouseX, double mouseY, int button
    ) {
        if (newSceneNameBuffer == null) return false;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        int dialogX = this.width / 2 - 190;
        int dialogY = this.height / 2 - 85;
        if (inside(mouseX, mouseY, dialogX + 100, dialogY + 55, 260, 22)) {
            openMapPicker(MapPickerTarget.NEW_SCENE);
            return true;
        }
        if (inside(mouseX, mouseY, dialogX + 100, dialogY + 82, 260, 22)) {
            clearNewSceneBackground();
            return true;
        }
        if (inside(mouseX, mouseY, dialogX + 100, dialogY + 140, 110, 20)) {
            confirmNewScene();
            return true;
        }
        if (inside(mouseX, mouseY, dialogX + 250, dialogY + 140, 110, 20)) {
            closeNewSceneDialog();
            return true;
        }
        return true;
    }

    private void renderNewSceneButton(
            VRenderContext context, int x, int y, int width, int height,
            String label, boolean enabled
    ) {
        boolean hovered = enabled && inside(
                context.mouseX(), context.mouseY(), x, y, width, height);
        context.graphics().fill(x, y, x + width, y + height,
                enabled ? hovered ? 0xEE34343D : 0xDD18181E : 0xCC111114);
        sceneDialogBorder(context, x, y, width, height,
                enabled ? 0xFFFFAA44 : 0xFF55555A);
        context.graphics().drawCenteredString(this.font, label,
                x + width / 2, y + (height - 8) / 2,
                enabled ? 0xFFFFFFFF : 0xFF77777D);
    }

    private void sceneDialogBorder(
            VRenderContext context, int x, int y, int width, int height, int color
    ) {
        context.graphics().hLine(x, x + width, y, color);
        context.graphics().hLine(x, x + width, y + height, color);
        context.graphics().vLine(x, y, y + height, color);
        context.graphics().vLine(x + width, y, y + height, color);
    }

    private int[] fitPreview(int sourceWidth, int sourceHeight, int maximumSize) {
        if (sourceWidth <= 0 || sourceHeight <= 0) return new int[]{maximumSize, maximumSize};
        double scale = Math.min(
                maximumSize / (double) sourceWidth,
                maximumSize / (double) sourceHeight);
        return new int[]{
                Math.max(1, (int) Math.round(sourceWidth * scale)),
                Math.max(1, (int) Math.round(sourceHeight * scale))
        };
    }

    private boolean inside(
            double mouseX, double mouseY, int x, int y, int width, int height
    ) {
        return mouseX >= x && mouseX <= x + width
                && mouseY >= y && mouseY <= y + height;
    }

    private String ellipsize(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) return value;
        return value.substring(0, Math.max(0, maximumLength - 3)) + "...";
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

    private enum BackgroundPickerTarget {
        NONE,
        NEW_SCENE,
        NEW_MAP,
        ACTIVE_SCENE
    }

    private enum MapPickerTarget {
        NONE,
        NEW_SCENE,
        ACTIVE_SCENE
    }

    private record MapPreview(ResourceLocation texture, int width, int height) {
    }

    private record PendingAssetDeletion(
            AssetManagerOverlay.Section section,
            String id,
            AssetDeleteConfirmationOverlay.Request request
    ) {
    }

    private record PendingAssetBatchDeletion(
            AssetManagerOverlay.Section section,
            List<AssetManagerOverlay.MoveEntry> entries,
            AssetBatchDeleteConfirmationOverlay.Request request
    ) {
        private PendingAssetBatchDeletion {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }

    private record PendingAssetFolderDeletion(
            AssetManagerOverlay.Section section,
            String folder,
            AssetFolderDeleteConfirmationOverlay.Request request
    ) {
    }

    @Override
    public void removed() {
        if (sceneBackgroundEditor.isActive()) cancelSceneBackgroundEdit();
        if (pendingHistoryRequestId != null) {
            inputController.clearEditorHistory();
            VttClientSceneHistorySync.releaseAfterNextSnapshot();
            session.requestSceneHistoryResync();
            pendingHistoryRequestId = null;
        }
        if (session.isLocalMaster()
                && VttClientPresentationState.isFollowingMasterCamera()) {
            sendPresentationCommand(VttPresentationCommandPayload.TOGGLE_CAMERA_FOLLOW);
        }
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
