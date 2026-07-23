package com.petrick.vtt.editor.overlay;

import com.petrick.vtt.feature.tabletop.VttScene;
import com.petrick.vtt.feature.tabletop.VttTabletop;
import com.petrick.vtt.platform.render.VRenderContext;
import net.minecraft.client.gui.Font;
import java.util.List;
import java.util.Optional;

/** Simple scene selector for the active tabletop. */
public final class SceneListOverlay {
    private static final int WIDTH = 220;
    private static final int PADDING = 8;
    private static final int LINE_HEIGHT = 12;
    private static final int OFFSET_FROM_BOTTOM = 40;

    public void render(VRenderContext context, Font font, VttTabletop tabletop,
                       VttScene activeScene) {
        if (tabletop == null) return;
        List<String> ids = tabletop.getSceneIds();
        int height = PADDING * 2 + 26 + Math.max(1, ids.size()) * LINE_HEIGHT;
        int x = panelX(context.screenWidth());
        int y = panelY(context.screenHeight(), height);
        context.graphics().fill(x, y, x + WIDTH, y + height, 0xDD000000);
        context.graphics().hLine(x, x + WIDTH, y, 0xFFFFAA44);
        context.graphics().hLine(x, x + WIDTH, y + height, 0xFFFFAA44);
        context.graphics().vLine(x, y, y + height, 0xFFFFAA44);
        context.graphics().vLine(x + WIDTH, y, y + height, 0xFFFFAA44);
        context.graphics().drawString(
                font, "Scenes", x + PADDING, y + PADDING, 0xFFFFFFFF, false);
        int rowY = y + PADDING + 18;
        if (ids.isEmpty()) {
            context.graphics().drawString(
                    font, "No scenes", x + PADDING, rowY, 0xFF888888, false);
            rowY += LINE_HEIGHT;
        } else {
            for (String id : ids) {
                boolean active = activeScene != null && id.equals(activeScene.getId());
                String name = active ? activeScene.getDisplayName() : tabletop.getSceneDisplayName(id);
                context.graphics().drawString(font, (active ? "> " : "  ") + name,
                        x + PADDING, rowY, active ? 0xFFFFCC66 : 0xFFDDDDDD, false);
                rowY += LINE_HEIGHT;
            }
        }
    }

    public Optional<String> findSceneIdAt(
            VttTabletop tabletop,
            int screenWidth,
            int screenHeight,
            double mouseX,
            double mouseY
    ) {
        if (tabletop == null) return Optional.empty();
        int x = panelX(screenWidth);
        List<String> ids = tabletop.getSceneIds();
        int height = PADDING * 2 + 26 + Math.max(1, ids.size()) * LINE_HEIGHT;
        int firstY = panelY(screenHeight, height) + PADDING + 18;
        if (mouseX < x || mouseX > x + WIDTH) return Optional.empty();
        for (int i = 0; i < ids.size(); i++) {
            int top = firstY + i * LINE_HEIGHT;
            if (mouseY >= top && mouseY <= top + LINE_HEIGHT) return Optional.of(ids.get(i));
        }
        return Optional.empty();
    }

    private int panelX(int screenWidth) {
        return (screenWidth - WIDTH) / 2;
    }

    private int panelY(int screenHeight, int height) {
        return screenHeight - height - OFFSET_FROM_BOTTOM;
    }

}
