package com.petrick.vtt.feature.tabletop;

/**
 * Representa um objeto salvo dentro de uma cena.
 *
 * Por enquanto, o principal uso é salvar tokens colocados na mesa.
 *
 * Estrutura propositalmente plana/composta para preparar:
 * - ECS futuro;
 * - Delta Updates;
 * - multiplayer/server authority.
 */
public final class VttSceneObject {

    private String id;

    private String displayName;

    /**
     * TokenDefinition de origem.
     *
     * Exemplo:
     * user/tokens/goblin
     */
    private String sourceTokenDefinitionId;

    private VttSceneTransform transform = new VttSceneTransform();

    private VttSceneSize size = new VttSceneSize();

    private VttSceneState state = new VttSceneState();

    /**
     * Índice explícito da camada.
     * Mesmo que a ordem da lista mude, isso ajuda no save futuro.
     */
    private int layerIndex;

    /** Vision radius in world units. Zero means unlimited. */
    private double visionRange;

    /** Stable player identifier that owns this placed token. Null means unowned. */
    private String ownerId;

    public VttSceneObject() {}

    public VttSceneObject(
            String id,
            String displayName,
            String sourceTokenDefinitionId
    ) {
        this.id = id;
        this.displayName = displayName;
        this.sourceTokenDefinitionId = sourceTokenDefinitionId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getSourceTokenDefinitionId() {
        return sourceTokenDefinitionId;
    }

    public void setSourceTokenDefinitionId(String sourceTokenDefinitionId) {
        this.sourceTokenDefinitionId = sourceTokenDefinitionId;
    }

    public VttSceneTransform getTransform() {
        return transform;
    }

    public void setTransform(VttSceneTransform transform) {
        this.transform = transform == null
                ? new VttSceneTransform()
                : transform;
    }

    public VttSceneSize getSize() {
        return size;
    }

    public void setSize(VttSceneSize size) {
        this.size = size == null
                ? new VttSceneSize()
                : size;
    }

    public VttSceneState getState() {
        return state;
    }

    public void setState(VttSceneState state) {
        this.state = state == null
                ? new VttSceneState()
                : state;
    }

    public int getLayerIndex() {
        return layerIndex;
    }

    public void setLayerIndex(int layerIndex) {
        this.layerIndex = Math.max(0, layerIndex);
    }

    public double getVisionRange() {
        return visionRange;
    }

    public void setVisionRange(double visionRange) {
        this.visionRange = Math.max(0.0, visionRange);
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId == null || ownerId.isBlank() ? null : ownerId.trim();
    }
}
