package com.petrick.vtt;

import com.mojang.logging.LogUtils;
import com.petrick.vtt.core.application.VTTApplication;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(VTT.MOD_ID)
public final class VTT {

    public static final String MOD_ID = "vtt";

    public static final Logger LOGGER = LogUtils.getLogger();

    private static VTTApplication application;

    public VTT(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("========================================");
        LOGGER.info("Initializing Virtual Tabletop...");
        LOGGER.info("========================================");

        application = new VTTApplication();
        application.initialize();
    }

    public static VTTApplication getApplication() {
        if (application == null) {
            throw new IllegalStateException("VTTApplication has not been initialized yet.");
        }

        return application;
    }
}