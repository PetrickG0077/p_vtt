package com.petrick.vtt.feature.tabletop;

import java.util.ArrayList;
import java.util.List;

/** Persistent fog configuration and rectangular editing operations for one scene. */
public final class VttFogOfWar {
    private boolean enabled;
    private boolean defaultHidden = true;
    private List<VttFogArea> revealedAreas = new ArrayList<>();
    private List<VttFogArea> hiddenAreas = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isDefaultHidden() { return defaultHidden; }
    public void setDefaultHidden(boolean defaultHidden) { this.defaultHidden = defaultHidden; }

    public List<VttFogArea> getRevealedAreas() {
        if (revealedAreas == null) revealedAreas = new ArrayList<>();
        return revealedAreas;
    }

    public List<VttFogArea> getHiddenAreas() {
        if (hiddenAreas == null) hiddenAreas = new ArrayList<>();
        return hiddenAreas;
    }

    public void addRevealedArea(VttFogArea area) {
        if (area != null) getRevealedAreas().add(area);
    }

    public void addHiddenArea(VttFogArea area) {
        if (area != null) getHiddenAreas().add(area);
    }

    public boolean removeArea(String areaId) {
        if (areaId == null || areaId.isBlank()) return false;
        boolean removedReveal = getRevealedAreas().removeIf(
                area -> area != null && areaId.equals(area.getId()));
        boolean removedHide = getHiddenAreas().removeIf(
                area -> area != null && areaId.equals(area.getId()));
        return removedReveal || removedHide;
    }

    public void clearAreas() {
        getRevealedAreas().clear();
        getHiddenAreas().clear();
    }
}
