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

    /** AttachmentDefinition origin. Mutually exclusive with sourceTokenDefinitionId. */
    private String sourceAttachmentDefinitionId;

    /** Present only on a placed attachment that follows another scene object. */
    private VttAttachmentBinding attachmentBinding;

    private VttSceneTransform transform = new VttSceneTransform();

    private VttSceneSize size = new VttSceneSize();

    private VttSceneState state = new VttSceneState();

    /** Null in legacy scenes means that the whole visual token is collidable. */
    private VttSceneCollisionBox collisionBox;

    /**
     * Índice explícito da camada.
     * Mesmo que a ordem da lista mude, isso ajuda no save futuro.
     */
    private int layerIndex;

    /** Legacy outer vision radius in world units. Zero uses the default radius. */
    private double visionRange;

    /** Fully illuminated radius. The area up to the outer radius uses uniform dimming. */
    private Double visionInnerRadius;

    /** Null keeps old scene JSON compatible and means enabled. */
    private Boolean visionEnabled;

    /** Whether this vision source illuminates its own inner/outer radius. */
    private Boolean visionOwnLightEnabled;

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

    public String getSourceAttachmentDefinitionId() {
        return sourceAttachmentDefinitionId;
    }

    public void setSourceAttachmentDefinitionId(String sourceAttachmentDefinitionId) {
        this.sourceAttachmentDefinitionId = sourceAttachmentDefinitionId == null
                || sourceAttachmentDefinitionId.isBlank()
                ? null : sourceAttachmentDefinitionId.trim();
    }

    public boolean isAttachment() {
        return sourceAttachmentDefinitionId != null;
    }

    public VttAttachmentBinding getAttachmentBinding() {
        return attachmentBinding;
    }

    public void setAttachmentBinding(VttAttachmentBinding attachmentBinding) {
        this.attachmentBinding = attachmentBinding != null && attachmentBinding.isBound()
                ? attachmentBinding : null;
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

    public VttSceneCollisionBox getCollisionBox() {
        return collisionBox;
    }

    public void setCollisionBox(VttSceneCollisionBox collisionBox) {
        this.collisionBox = collisionBox;
    }

    public double getVisionRange() {
        return visionRange;
    }

    public void setVisionRange(double visionRange) {
        this.visionRange = Math.max(0.0, visionRange);
    }

    public double getVisionOuterRadius() {
        return visionRange;
    }

    public void setVisionOuterRadius(double visionOuterRadius) {
        setVisionRange(visionOuterRadius);
    }

    public double getVisionInnerRadius() {
        return visionInnerRadius == null ? 256.0 : visionInnerRadius;
    }

    public void setVisionInnerRadius(double visionInnerRadius) {
        this.visionInnerRadius = Math.max(0.0, visionInnerRadius);
    }

    public boolean isVisionEnabled() {
        return visionEnabled == null || visionEnabled;
    }

    public void setVisionEnabled(boolean visionEnabled) {
        this.visionEnabled = visionEnabled;
    }

    public boolean isVisionOwnLightEnabled() {
        return visionOwnLightEnabled == null || visionOwnLightEnabled;
    }

    public void setVisionOwnLightEnabled(boolean visionOwnLightEnabled) {
        this.visionOwnLightEnabled = visionOwnLightEnabled;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId == null || ownerId.isBlank() ? null : ownerId.trim();
    }
}
