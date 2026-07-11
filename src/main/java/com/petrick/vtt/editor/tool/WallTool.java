package com.petrick.vtt.editor.tool;

import com.petrick.vtt.core.math.Vec2d;
import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttSceneSize;
import com.petrick.vtt.feature.tabletop.VttSceneTransform;
import com.petrick.vtt.feature.tabletop.VttWall;
import com.petrick.vtt.platform.render.VRenderContext;
import java.util.function.Supplier;

/** Click-drag tool used to create persistent rectangular wall areas. */
public final class WallTool implements Tool {
    public static final String ID = "wall";
    private static final int LEFT_MOUSE_BUTTON = 0;
    private static final double MIN_DRAG_PIXELS = 4.0;
    private final Supplier<VttScene> sceneSupplier;
    private final Runnable saveAction;
    private Vec2d start;
    private Vec2d end;

    public WallTool(Supplier<VttScene> sceneSupplier, Runnable saveAction) {
        this.sceneSupplier = sceneSupplier;
        this.saveAction = saveAction;
    }

    @Override public String getId() { return ID; }

    @Override
    public boolean mouseClicked(ToolContext context, double mouseX, double mouseY, int button, int modifiers) {
        if (button != LEFT_MOUSE_BUTTON) return false;
        start = context.renderState().screenToWorld(new Vec2d(mouseX, mouseY));
        end = start;
        return true;
    }

    @Override
    public boolean mouseDragged(ToolContext context, double mouseX, double mouseY, int button,
                                double dragX, double dragY, int modifiers) {
        if (button != LEFT_MOUSE_BUTTON || start == null) return false;
        end = context.renderState().screenToWorld(new Vec2d(mouseX, mouseY));
        return true;
    }

    @Override
    public boolean mouseReleased(ToolContext context, double mouseX, double mouseY, int button, int modifiers) {
        if (button != LEFT_MOUSE_BUTTON || start == null) return false;
        end = context.renderState().screenToWorld(new Vec2d(mouseX, mouseY));
        double width = Math.abs(end.x() - start.x());
        double height = Math.abs(end.y() - start.y());
        Vec2d startScreen = context.renderState().worldToScreen(start);
        boolean largeEnough = Math.abs(mouseX - startScreen.x()) >= MIN_DRAG_PIXELS
                && Math.abs(mouseY - startScreen.y()) >= MIN_DRAG_PIXELS;
        if (largeEnough) {
            VttScene scene = sceneSupplier.get();
            if (scene != null) {
                double centerX = (start.x() + end.x()) / 2.0;
                double centerY = (start.y() + end.y()) / 2.0;
                scene.addWall(new VttWall(
                        nextWallId(scene),
                        new VttSceneTransform(centerX, centerY, 1.0, 1.0, 0.0),
                        new VttSceneSize(width, height)
                ));
                saveAction.run();
            }
        }
        cancel();
        return true;
    }

    @Override
    public void render(VRenderContext context, ToolContext toolContext) {
        if (start == null || end == null) return;
        Vec2d a = context.renderState().worldToScreen(start);
        Vec2d b = context.renderState().worldToScreen(end);
        int left = (int) Math.round(Math.min(a.x(), b.x()));
        int top = (int) Math.round(Math.min(a.y(), b.y()));
        int right = (int) Math.round(Math.max(a.x(), b.x()));
        int bottom = (int) Math.round(Math.max(a.y(), b.y()));
        if (right <= left || bottom <= top) return;
        context.graphics().fill(left, top, right, bottom, 0x66FF8844);
        context.graphics().hLine(left, right, top, 0xFFFFAA66);
        context.graphics().hLine(left, right, bottom, 0xFFFFAA66);
        context.graphics().vLine(left, top, bottom, 0xFFFFAA66);
        context.graphics().vLine(right, top, bottom, 0xFFFFAA66);
    }

    public void cancel() {
        start = null;
        end = null;
    }

    public boolean isDrawing() { return start != null; }

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
