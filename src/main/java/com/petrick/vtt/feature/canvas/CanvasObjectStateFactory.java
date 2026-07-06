package com.petrick.vtt.feature.canvas;

import com.petrick.vtt.feature.asset.AssetRef;
import com.petrick.vtt.feature.canvas.visual.ColorVisual;
import com.petrick.vtt.feature.canvas.visual.TextureVisual;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fábrica temporária de estados visuais para objetos do canvas.
 *
 * Por enquanto cria estados de teste:
 * - 1 Normal
 * - 2 Injured
 * - 3 Dead
 *
 * Futuramente isso será substituído por estados reais vindos de tokens salvos/importados.
 */
public final class CanvasObjectStateFactory {

    private CanvasObjectStateFactory() {
    }

    public static Map<String, CanvasObjectState> createTestTokenStates(AssetRef normalAsset) {
        Map<String, CanvasObjectState> states = new LinkedHashMap<>();

        states.put(
                "1",
                new CanvasObjectState(
                        "1",
                        "Normal",
                        new TextureVisual(normalAsset)
                )
        );

        states.put(
                "2",
                new CanvasObjectState(
                        "2",
                        "Injured",
                        new ColorVisual(0xFFFF5555)
                )
        );

        states.put(
                "3",
                new CanvasObjectState(
                        "3",
                        "Dead",
                        new ColorVisual(0xFF555555)
                )
        );

        return states;
    }
}