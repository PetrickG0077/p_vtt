package com.petrick.vtt.core.application;

import com.petrick.vtt.VTT;
import com.petrick.vtt.core.session.VTTSession;

/**
 * Classe central da aplicação VTT.
 *
 * Ela representa o estado global do mod VTT enquanto o Minecraft está aberto.
 *
 * Por enquanto, ela guarda apenas a sessão ativa.
 * Futuramente pode guardar serviços globais, registries, configurações e sistemas.
 */
public final class VTTApplication {

    private VTTSession activeSession;

    public VTTApplication() {
    }

    public synchronized void initialize() {
        if (activeSession != null) {
            return;
        }

        VTT.LOGGER.info("Initializing VTT application...");

        this.activeSession = new VTTSession();

        VTT.LOGGER.info("VTT application initialized.");
    }

    public void shutdown() {
        VTT.LOGGER.info("Shutting down VTT application...");

        VTT.LOGGER.info("VTT application shutdown complete.");
    }

    public VTTSession getActiveSession() {
        if (activeSession == null) {
            initialize();
        }

        return activeSession;
    }
}
