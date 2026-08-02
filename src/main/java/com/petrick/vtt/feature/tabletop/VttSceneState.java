package com.petrick.vtt.feature.tabletop;

/**
 * Bloco de estado salvo no JSON da cena.
 */
public final class VttSceneState {

    private String activeStateId = "1";

    private boolean visible = true;

    private boolean flippedHorizontally;

    /** RGB multiplier applied while rendering this placed token. White is neutral. */
    private int tintColorRgb = 0xFFFFFF;

    public VttSceneState() {}

    public VttSceneState(
            String activeStateId,
            boolean visible,
            boolean flippedHorizontally
    ) {
        this.activeStateId = activeStateId == null || activeStateId.isBlank()
                ? "1"
                : activeStateId;
        this.visible = visible;
        this.flippedHorizontally = flippedHorizontally;
    }

    public String getActiveStateId() {
        return activeStateId;
    }

    public void setActiveStateId(String activeStateId) {
        this.activeStateId = activeStateId == null || activeStateId.isBlank()
                ? "1"
                : activeStateId;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isFlippedHorizontally() {
        return flippedHorizontally;
    }

    public void setFlippedHorizontally(boolean flippedHorizontally) {
        this.flippedHorizontally = flippedHorizontally;
    }

    public int getTintColorRgb() {
        return tintColorRgb & 0x00FFFFFF;
    }

    public void setTintColorRgb(int tintColorRgb) {
        this.tintColorRgb = tintColorRgb & 0x00FFFFFF;
    }
}
