package com.petrick.vtt.editor.tool;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.tabletop.VttFogArea;
import com.petrick.vtt.feature.tabletop.VttFogOfWar;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttSceneSize;
import com.petrick.vtt.feature.tabletop.VttSceneTransform;
import com.petrick.vtt.platform.render.VRenderContext;

import java.util.function.Supplier;

/** Creates, selects and reveals localized rectangular fog areas. */
public final class FogTool implements Tool {
    public static final String ID = "fog";
    private static final int LEFT_MOUSE_BUTTON = 0;
    private static final double MIN_DRAG_PIXELS = 4.0;

    private final Supplier<VttScene> sceneSupplier;
    private final Runnable saveAction;
    private Vec2d start;
    private Vec2d end;
    private String selectedAreaId;
    private boolean moving;
    private Vec2d dragStartWorld;
    private VttSceneTransform originalTransform;

    public FogTool(Supplier<VttScene> sceneSupplier, Runnable saveAction) {
        this.sceneSupplier = sceneSupplier;
        this.saveAction = saveAction;
    }

    @Override public String getId() { return ID; }

    @Override
    public boolean mouseClicked(ToolContext context, double mouseX, double mouseY, int button, int modifiers) {
        if (button != LEFT_MOUSE_BUTTON) return false;
        Vec2d world = context.renderState().screenToWorld(new Vec2d(mouseX, mouseY));
        VttFogArea clicked = findTopmostAreaAt(world);
        if (clicked != null) {
            selectedAreaId = clicked.getId();
            originalTransform = copyTransform(clicked.getTransform());
            dragStartWorld = world;
            moving = true;
            return true;
        }
        selectedAreaId = null;
        start = world;
        end = start;
        return true;
    }

    @Override
    public boolean mouseDragged(ToolContext context, double mouseX, double mouseY, int button,
                                double dragX, double dragY, int modifiers) {
        if (button != LEFT_MOUSE_BUTTON || (!moving && start == null)) return false;
        Vec2d world = context.renderState().screenToWorld(new Vec2d(mouseX, mouseY));
        if (moving) moveSelectedArea(world);
        else end = world;
        return true;
    }

    @Override
    public boolean mouseReleased(ToolContext context, double mouseX, double mouseY, int button, int modifiers) {
        if (button != LEFT_MOUSE_BUTTON || (!moving && start == null)) return false;
        Vec2d world = context.renderState().screenToWorld(new Vec2d(mouseX, mouseY));
        if (moving) {
            moveSelectedArea(world);
            saveAction.run();
        } else {
            end = world;
            Vec2d startScreen = context.renderState().worldToScreen(start);
            boolean largeEnough = Math.abs(mouseX - startScreen.x()) >= MIN_DRAG_PIXELS
                    && Math.abs(mouseY - startScreen.y()) >= MIN_DRAG_PIXELS;
            if (largeEnough) createArea();
        }
        finishOperation();
        return true;
    }

    @Override
    public void render(VRenderContext context, ToolContext toolContext) {
        if (start != null && end != null) renderCreationPreview(context);
        VttFogArea selected = getSelectedArea();
        if (selected != null) renderSelection(context, selected);
    }

    public boolean isDrawing() { return start != null || moving; }

    public void cancel() {
        VttFogArea selected = getSelectedArea();
        if (selected != null && originalTransform != null) selected.setTransform(originalTransform);
        finishOperation();
    }

    public void deactivate() {
        cancel();
        selectedAreaId = null;
    }

    public void activateLocalizedMode() {
        VttScene scene = sceneSupplier.get();
        if (scene == null) return;
        VttFogOfWar fog = scene.getFogOfWar();
        if (fog.isDefaultHidden()) {
            fog.setDefaultHidden(false);
            saveAction.run();
        }
    }

    public boolean toggleSelectedVisibility() {
        VttFogArea area = getSelectedArea();
        if (area == null) return false;
        area.setVisible(!area.isVisible());
        saveAction.run();
        return true;
    }

    public boolean toggleEnabled() {
        VttScene scene = sceneSupplier.get();
        if (scene == null) return false;
        VttFogOfWar fog = scene.getFogOfWar();
        fog.setEnabled(!fog.isEnabled());
        saveAction.run();
        return true;
    }

    public boolean deleteSelectedArea() {
        VttScene scene = sceneSupplier.get();
        if (scene == null || selectedAreaId == null) return false;
        boolean removed = scene.getFogOfWar().removeArea(selectedAreaId);
        if (removed) {
            selectedAreaId = null;
            finishOperation();
            saveAction.run();
        }
        return removed;
    }

    public boolean scaleSelectedArea(double factor) {
        VttFogArea area = getSelectedArea();
        if (area == null) return false;
        area.getTransform().setScaleX(Math.max(0.05, area.getTransform().getScaleX() * factor));
        area.getTransform().setScaleY(Math.max(0.05, area.getTransform().getScaleY() * factor));
        saveAction.run();
        return true;
    }

    public boolean rotateSelectedArea(double degrees) {
        VttFogArea area = getSelectedArea();
        if (area == null) return false;
        area.getTransform().setRotationDegrees(area.getTransform().getRotationDegrees() + degrees);
        saveAction.run();
        return true;
    }

    public boolean resetSelectedAreaTransform() {
        VttFogArea area = getSelectedArea();
        if (area == null) return false;
        area.getTransform().setScaleX(1.0);
        area.getTransform().setScaleY(1.0);
        area.getTransform().setRotationDegrees(0.0);
        saveAction.run();
        return true;
    }

    private void createArea() {
        VttScene scene = sceneSupplier.get();
        if (scene == null) return;
        VttFogOfWar fog = scene.getFogOfWar();
        fog.setEnabled(true);
        fog.setDefaultHidden(false);
        VttFogArea area = new VttFogArea(
                nextAreaId(fog, "fog_area_"),
                new VttSceneTransform((start.x() + end.x()) / 2.0,
                        (start.y() + end.y()) / 2.0, 1.0, 1.0, 0.0),
                new VttSceneSize(Math.abs(end.x() - start.x()), Math.abs(end.y() - start.y()))
        );
        fog.addHiddenArea(area);
        selectedAreaId = area.getId();
        saveAction.run();
    }

    private void moveSelectedArea(Vec2d world) {
        VttFogArea area = getSelectedArea();
        if (area == null || originalTransform == null || dragStartWorld == null) return;
        area.getTransform().setX(originalTransform.getX() + world.x() - dragStartWorld.x());
        area.getTransform().setY(originalTransform.getY() + world.y() - dragStartWorld.y());
    }

    private VttFogArea findTopmostAreaAt(Vec2d world) {
        VttScene scene = sceneSupplier.get();
        if (scene == null) return null;
        VttFogOfWar fog = scene.getFogOfWar();
        for (int index = fog.getHiddenAreas().size() - 1; index >= 0; index--) {
            VttFogArea area = fog.getHiddenAreas().get(index);
            if (area != null && containsPoint(area, world)) return area;
        }
        for (int index = fog.getRevealedAreas().size() - 1; index >= 0; index--) {
            VttFogArea area = fog.getRevealedAreas().get(index);
            if (area != null && containsPoint(area, world)) return area;
        }
        return null;
    }

    private boolean containsPoint(VttFogArea area, Vec2d world) {
        VttSceneTransform transform = area.getTransform();
        double radians = Math.toRadians(-transform.getRotationDegrees());
        double dx = world.x() - transform.getX();
        double dy = world.y() - transform.getY();
        double localX = dx * Math.cos(radians) - dy * Math.sin(radians);
        double localY = dx * Math.sin(radians) + dy * Math.cos(radians);
        double width = Math.abs(area.getSize().getWidth() * transform.getScaleX());
        double height = Math.abs(area.getSize().getHeight() * transform.getScaleY());
        return Math.abs(localX) <= width / 2.0 && Math.abs(localY) <= height / 2.0;
    }

    private VttFogArea getSelectedArea() {
        VttScene scene = sceneSupplier.get();
        if (scene == null || selectedAreaId == null) return null;
        VttFogOfWar fog = scene.getFogOfWar();
        for (VttFogArea area : fog.getHiddenAreas()) {
            if (area != null && selectedAreaId.equals(area.getId())) return area;
        }
        for (VttFogArea area : fog.getRevealedAreas()) {
            if (area != null && selectedAreaId.equals(area.getId())) return area;
        }
        return null;
    }

    private VttSceneTransform copyTransform(VttSceneTransform source) {
        return new VttSceneTransform(source.getX(), source.getY(), source.getScaleX(),
                source.getScaleY(), source.getRotationDegrees());
    }

    private void renderCreationPreview(VRenderContext context) {
        Vec2d a = context.renderState().worldToScreen(start);
        Vec2d b = context.renderState().worldToScreen(end);
        int left = (int) Math.round(Math.min(a.x(), b.x()));
        int top = (int) Math.round(Math.min(a.y(), b.y()));
        int right = (int) Math.round(Math.max(a.x(), b.x()));
        int bottom = (int) Math.round(Math.max(a.y(), b.y()));
        context.graphics().fill(left, top, right, bottom, 0x6608080C);
        drawBorder(context, left, top, right, bottom, 0xFFFF8844);
    }

    private void renderSelection(VRenderContext context, VttFogArea area) {
        VttSceneTransform transform = area.getTransform();
        Vec2d center = context.renderState().worldToScreen(new Vec2d(transform.getX(), transform.getY()));
        double zoom = context.renderState().getCamera().getZoom();
        int width = Math.max(1, (int) Math.round(
                Math.abs(area.getSize().getWidth() * transform.getScaleX()) * zoom));
        int height = Math.max(1, (int) Math.round(
                Math.abs(area.getSize().getHeight() * transform.getScaleY()) * zoom));
        int left = -width / 2;
        int top = -height / 2;
        int right = left + width;
        int bottom = top + height;
        int color = area.isVisible() ? 0xFFFFAA44 : 0xFF44FF88;
        PoseStack pose = context.graphics().pose();
        pose.pushPose();
        pose.translate(center.x(), center.y(), 0.0);
        pose.mulPose(Axis.ZP.rotationDegrees((float) transform.getRotationDegrees()));
        drawBorder(context, left, top, right, bottom, color);
        pose.popPose();
    }

    private void drawBorder(VRenderContext context, int left, int top, int right, int bottom, int color) {
        context.graphics().hLine(left, right, top, color);
        context.graphics().hLine(left, right, bottom, color);
        context.graphics().vLine(left, top, bottom, color);
        context.graphics().vLine(right, top, bottom, color);
    }

    private void finishOperation() {
        start = null;
        end = null;
        moving = false;
        dragStartWorld = null;
        originalTransform = null;
    }

    private String nextAreaId(VttFogOfWar fog, String prefix) {
        int number = fog.getRevealedAreas().size() + fog.getHiddenAreas().size() + 1;
        String id = prefix + number;
        while (containsAreaId(fog, id)) id = prefix + ++number;
        return id;
    }

    private boolean containsAreaId(VttFogOfWar fog, String id) {
        return fog.getRevealedAreas().stream().anyMatch(area -> area != null && id.equals(area.getId()))
                || fog.getHiddenAreas().stream().anyMatch(area -> area != null && id.equals(area.getId()));
    }

}
