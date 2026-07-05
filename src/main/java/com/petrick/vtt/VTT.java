package com.petrick.vtt;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(VTT.MOD_ID)
public final class VTT {

    public static final String MOD_ID = "vtt";

    public static final Logger LOGGER = LogUtils.getLogger();

    public VTT(IEventBus modEventBus, ModContainer modContainer) {

        LOGGER.info("");
        LOGGER.info("========================================");
        LOGGER.info("Initializing Virtual Tabletop...");
        LOGGER.info("Mod ID: {}", MOD_ID);
        LOGGER.info("========================================");
        LOGGER.info("");

    }

}