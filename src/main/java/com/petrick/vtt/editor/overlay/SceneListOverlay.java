package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttTabletop;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;
import java.util.List;
import java.util.Optional;

/** Simple scene selector for the active tabletop. */
public final class SceneListOverlay {
    private static final int X = 240, Y = 10, WIDTH = 220, PADDING = 8, LINE_HEIGHT = 12;

    public void render(VRenderContext context, Font font, VttTabletop tabletop,
                       VttScene activeScene) {
        if (tabletop == null) return;
        List<String> ids = tabletop.getSceneIds();
        int height = PADDING * 2 + 30 + Math.max(1, ids.size()) * LINE_HEIGHT + LINE_HEIGHT;
        context.graphics().fill(X, Y, X + WIDTH, Y + height, 0xDD000000);
        context.graphics().hLine(X, X + WIDTH, Y, 0xFFFFAA44);
        context.graphics().hLine(X, X + WIDTH, Y + height, 0xFFFFAA44);
        context.graphics().vLine(X, Y, Y + height, 0xFFFFAA44);
        context.graphics().vLine(X + WIDTH, Y, Y + height, 0xFFFFAA44);
        context.graphics().drawString(font, "Scenes", X + PADDING, Y + PADDING, 0xFFFFFFFF, false);
        int rowY = Y + PADDING + 18;
        if (ids.isEmpty()) {
            context.graphics().drawString(font, "No scenes", X + PADDING, rowY, 0xFF888888, false);
            rowY += LINE_HEIGHT;
        } else {
            for (String id : ids) {
                boolean active = activeScene != null && id.equals(activeScene.getId());
                String name = active ? activeScene.getDisplayName() : id;
                context.graphics().drawString(font, (active ? "> " : "  ") + name,
                        X + PADDING, rowY, active ? 0xFFFFCC66 : 0xFFDDDDDD, false);
                rowY += LINE_HEIGHT;
            }
        }
        context.graphics().drawString(font, "+ New Scene", X + PADDING, rowY + 4, 0xFF66DD88, false);
    }

    public Optional<String> findSceneIdAt(VttTabletop tabletop, double mouseX, double mouseY) {
        if (tabletop == null || mouseX < X || mouseX > X + WIDTH) return Optional.empty();
        int firstY = Y + PADDING + 18;
        List<String> ids = tabletop.getSceneIds();
        for (int i = 0; i < ids.size(); i++) {
            int top = firstY + i * LINE_HEIGHT;
            if (mouseY >= top && mouseY <= top + LINE_HEIGHT) return Optional.of(ids.get(i));
        }
        return Optional.empty();
    }

    public boolean isCreateSceneButtonAt(VttTabletop tabletop, double mouseX, double mouseY) {
        if (tabletop == null || mouseX < X || mouseX > X + WIDTH) return false;
        int rowCount = Math.max(1, tabletop.getSceneIds().size());
        int top = Y + PADDING + 18 + rowCount * LINE_HEIGHT + 4;
        return mouseY >= top && mouseY <= top + LINE_HEIGHT;
    }
}
