package com.petrick.vtt.feature.canvas;

import com.petrick.vtt.core.math.Rectd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cena temporária do canvas.
 *
 * Por enquanto ela guarda objetos simples de teste.
 * Futuramente será substituída por uma cena baseada no ECS.
 */
public final class CanvasScene {

    private final List<CanvasObject> objects = new ArrayList<>();

    public static CanvasScene createDebugScene() {
        CanvasScene scene = new CanvasScene();

        scene.addObject(new CanvasObject(
                "object_1",
                new Rectd(-50, -50, 100, 100),
                0xFFFFFFFF
        ));

        scene.addObject(new CanvasObject(
                "object_2",
                new Rectd(150, -40, 80, 80),
                0xFFFFAA55
        ));

        scene.addObject(new CanvasObject(
                "object_3",
                new Rectd(-220, 100, 120, 70),
                0xFF55AAFF
        ));

        return scene;
    }

    public void addObject(CanvasObject object) {
        objects.add(object);
    }

    public List<CanvasObject> getObjects() {
        return Collections.unmodifiableList(objects);
    }
}