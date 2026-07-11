package com.petrick.vtt.editor.tool;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttWall;
import com.petrick.vtt.platform.render.VRenderContext;
import java.util.function.Supplier;

/** Two-click tool used to create persistent wall segments. */
public final class WallTool implements Tool {
    public static final String ID = "wall";
    private static final int LEFT_MOUSE_BUTTON = 0;
    private final Supplier<VttScene> sceneSupplier;
    private final Runnable saveAction;
    private Vec2d start;

    public WallTool(Supplier<VttScene> sceneSupplier, Runnable saveAction) {
        this.sceneSupplier = sceneSupplier;
        this.saveAction = saveAction;
    }

    @Override public String getId() { return ID; }

    @Override
    public boolean mouseClicked(ToolContext context, double mouseX, double mouseY, int button, int modifiers) {
        if (button != LEFT_MOUSE_BUTTON) return false;
        Vec2d point = context.renderState().screenToWorld(new Vec2d(mouseX, mouseY));
        if (start == null) { start = point; return true; }
        if (point.subtract(start).length() > 0.001) {
            VttScene scene = sceneSupplier.get();
            if (scene != null) {
                scene.addWall(new VttWall(nextWallId(scene), start.x(), start.y(), point.x(), point.y()));
                saveAction.run();
            }
        }
        start = null;
        return true;
    }

    @Override
    public void render(VRenderContext context, ToolContext toolContext) {
        if (start == null) return;
        Vec2d a = context.renderState().worldToScreen(start);
        Vec2d b = context.mouseScreenPosition();
        double dx = b.x() - a.x(), dy = b.y() - a.y();
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0) return;
        PoseStack pose = context.graphics().pose();
        pose.pushPose();
        pose.translate(a.x(), a.y(), 0.0);
        pose.mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        context.graphics().fill(0, -2, (int) Math.round(length), 2, 0xFFFF8844);
        pose.popPose();
    }

    public void cancel() { start = null; }

    private String nextWallId(VttScene scene) {
        int number = scene.getWalls().size() + 1;
        String id = "wall_" + number;
        while (containsWall(scene, id)) id = "wall_" + ++number;
        return id;
    }

    private boolean containsWall(VttScene scene, String id) {
        return scene.getWalls().stream().anyMatch(wall -> id.equals(wall.getId()));
    }
}
