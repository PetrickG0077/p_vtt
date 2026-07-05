package com.petrick.vtt.platform.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.petrick.vtt.VTT;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Registra teclas usadas pelo cliente do VTT.
 */
@EventBusSubscriber(
        modid = VTT.MOD_ID,
        value = Dist.CLIENT
)
public final class ClientKeyMappings {

    public static final String CATEGORY = "key.categories.vtt";

    public static final KeyMapping OPEN_VTT_SCREEN = new KeyMapping(
            "key.vtt.open_screen",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            CATEGORY
    );

    private ClientKeyMappings() {
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_VTT_SCREEN);
    }
}