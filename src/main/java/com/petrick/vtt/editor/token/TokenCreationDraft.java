package com.petrick.vtt.editor.token;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dados temporários enquanto o usuário está criando ou editando um token.
 */
public final class TokenCreationDraft {

    public enum Mode {
        CREATE,
        EDIT
    }

    private static final int MAX_STATES = 9;

    private Mode mode = Mode.CREATE;

    private String editingTokenDefinitionId;

    private String originalDisplayName;

    private String name = "";

    private String player = "";

    private String notes = "";

    private String errorMessage = "";

    private String selectedImageId;

    private String defaultStateId;

    private String selectedImageDisplayName;

    private ResourceLocation selectedImageTexture;

    private int selectedImageWidth;

    private int selectedImageHeight;

    private final List<TokenStateDraft> states = new ArrayList<>();

    private String selectedStateId;

    public TokenCreationDraft() {
        ensureDefaultState();
    }

    public Mode getMode() {
        return mode;
    }

    public boolean isEditing() {
        return mode == Mode.EDIT;
    }

    public boolean deleteSelectedState() {
        return deleteState(selectedStateId);
    }

    public boolean duplicateState(String stateIdToDuplicate) {
        ensureDefaultState();

        if (!canAddState()) {
            setErrorMessage("State limit reached");
            return false;
        }

        if (stateIdToDuplicate == null || stateIdToDuplicate.isBlank()) {
            return false;
        }

        TokenStateDraft sourceState = null;

        for (TokenStateDraft state : states) {
            if (state.getId().equals(stateIdToDuplicate)) {
                sourceState = state;
                break;
            }
        }

        if (sourceState == null) {
            return false;
        }

        String newId = createNextStateId();

        TokenStateDraft duplicatedState = new TokenStateDraft(
                newId,
                sourceState.getDisplayName() + " Copy"
        );

        if (sourceState.hasImage()) {
            duplicatedState.selectImage(
                    sourceState.getImageId(),
                    sourceState.getImageDisplayName(),
                    sourceState.getImageTexture(),
                    sourceState.getImageWidth(),
                    sourceState.getImageHeight()
            );
        }

        states.add(duplicatedState);
        selectedStateId = duplicatedState.getId();
        syncMainImageFromState(duplicatedState);

        clearError();

        return true;
    }

    public boolean deleteState(String stateIdToDelete) {
        ensureDefaultState();

        if (states.size() <= 1) {
            setErrorMessage("Token must have at least one state");
            return false;
        }

        if (stateIdToDelete == null || stateIdToDelete.isBlank()) {
            return false;
        }

        int removedIndex = -1;

        for (int i = 0; i < states.size(); i++) {
            TokenStateDraft state = states.get(i);

            if (state.getId().equals(stateIdToDelete)) {
                removedIndex = i;
                states.remove(i);
                break;
            }
        }

        if (removedIndex < 0) {
            return false;
        }

        if (states.isEmpty()) {
            ensureDefaultState();
            return true;
        }

        if (stateIdToDelete.equals(selectedStateId)) {
            int newSelectedIndex = Math.min(removedIndex, states.size() - 1);
            selectedStateId = states.get(newSelectedIndex).getId();
            syncMainImageFromState(states.get(newSelectedIndex));
        } else {
            syncMainImageFromState(getSelectedState());
        }

        if (stateIdToDelete.equals(defaultStateId)) {
            defaultStateId = states.get(0).getId();
        }

        clearError();

        return true;
    }

    public void beginEdit(
            String tokenDefinitionId,
            String originalDisplayName
    ) {
        if (tokenDefinitionId == null || tokenDefinitionId.isBlank()) {
            throw new IllegalArgumentException("Token definition id cannot be null or blank");
        }

        this.mode = Mode.EDIT;
        this.editingTokenDefinitionId = tokenDefinitionId;
        this.originalDisplayName = originalDisplayName == null
                ? ""
                : originalDisplayName;
    }

    public String getEditingTokenDefinitionId() {
        return editingTokenDefinitionId;
    }

    public String getOriginalDisplayName() {
        return originalDisplayName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = sanitize(name);
        clearError();
    }

    public String getPlayer() {
        return player;
    }

    public void setPlayer(String player) {
        this.player = sanitize(player);
        clearError();
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = sanitize(notes);
        clearError();
    }

    public boolean hasName() {
        return name != null && !name.isBlank();
    }

    public String getResolvedDisplayName() {
        if (hasName()) {
            return name.trim();
        }

        if (selectedImageDisplayName != null && !selectedImageDisplayName.isBlank()) {
            return selectedImageDisplayName;
        }

        for (TokenStateDraft state : states) {
            if (state.hasImage()) {
                return state.getImageDisplayName();
            }
        }

        return "New Token";
    }

    public boolean hasSelectedImage() {
        return selectedImageTexture != null;
    }

    public String getSelectedImageId() {
        return selectedImageId;
    }

    public String getSelectedImageDisplayName() {
        return selectedImageDisplayName;
    }

    public ResourceLocation getSelectedImageTexture() {
        return selectedImageTexture;
    }

    public int getSelectedImageWidth() {
        return selectedImageWidth;
    }

    public int getSelectedImageHeight() {
        return selectedImageHeight;
    }

    public void selectImage(
            String imageId,
            String displayName,
            ResourceLocation texture,
            int width,
            int height
    ) {
        if (imageId == null || imageId.isBlank()) {
            clearSelectedImage();
            return;
        }

        if (texture == null || width <= 0 || height <= 0) {
            clearSelectedImage();
            return;
        }

        this.selectedImageId = imageId;
        this.selectedImageDisplayName = displayName == null || displayName.isBlank()
                ? imageId
                : displayName;
        this.selectedImageTexture = texture;
        this.selectedImageWidth = width;
        this.selectedImageHeight = height;

        TokenStateDraft selectedState = getSelectedState();

        if (selectedState != null) {
            selectedState.selectImage(
                    imageId,
                    displayName,
                    texture,
                    width,
                    height
            );
        }

        clearError();
    }

    public void clearSelectedImage() {
        clearMainImage();

        TokenStateDraft selectedState = getSelectedState();

        if (selectedState != null) {
            selectedState.clearImage();
        }
    }

    public List<TokenStateDraft> getStates() {
        ensureDefaultState();
        return Collections.unmodifiableList(states);
    }

    public String getSelectedStateId() {
        ensureDefaultState();
        return selectedStateId;
    }

    public TokenStateDraft getSelectedState() {
        ensureDefaultState();

        for (TokenStateDraft state : states) {
            if (state.getId().equals(selectedStateId)) {
                return state;
            }
        }

        return states.get(0);
    }

    public boolean isStateSelected(String stateId) {
        if (stateId == null || stateId.isBlank()) {
            return false;
        }

        return stateId.equals(getSelectedStateId());
    }

    public void selectState(String stateId) {
        if (stateId == null || stateId.isBlank()) {
            return;
        }

        for (TokenStateDraft state : states) {
            if (state.getId().equals(stateId)) {
                this.selectedStateId = stateId;
                syncMainImageFromState(state);
                clearError();
                return;
            }
        }
    }

    public boolean canAddState() {
        return states.size() < MAX_STATES;
    }

    public TokenStateDraft addState() {
        if (!canAddState()) {
            setErrorMessage("State limit reached");
            return null;
        }

        String id = createNextStateId();

        TokenStateDraft state = new TokenStateDraft(
                id,
                "State " + id
        );

        states.add(state);
        selectedStateId = id;
        syncMainImageFromState(state);
        clearError();

        return state;
    }

    public void replaceStates(
            List<TokenStateDraft> newStates,
            String stateIdToSelect
    ) {
        states.clear();

        if (newStates != null) {
            for (TokenStateDraft state : newStates) {
                if (state != null) {
                    states.add(state);
                }
            }
        }

        ensureDefaultState();

        if (stateIdToSelect != null && hasStateId(stateIdToSelect)) {
            selectedStateId = stateIdToSelect;
            defaultStateId = stateIdToSelect;
        } else {
            selectedStateId = states.get(0).getId();
            defaultStateId = states.get(0).getId();
        }

        syncMainImageFromState(getSelectedState());
    }

    public boolean hasAnyStateImage() {
        for (TokenStateDraft state : states) {
            if (state.hasImage()) {
                return true;
            }
        }

        return false;
    }

    public boolean allStatesHaveImages() {
        ensureDefaultState();

        for (TokenStateDraft state : states) {
            if (!state.hasImage()) {
                return false;
            }
        }

        return true;
    }

    public String getDefaultStateIdForSave() {
        ensureDefaultState();

        if (hasStateId(defaultStateId)) {
            return defaultStateId;
        }

        return states.get(0).getId();
    }

    public TokenStateDraft getDefaultStateForSave() {
        String defaultStateIdForSave = getDefaultStateIdForSave();

        for (TokenStateDraft state : states) {
            if (state.getId().equals(defaultStateIdForSave)) {
                return state;
            }
        }

        return states.get(0);
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean hasError() {
        return errorMessage != null && !errorMessage.isBlank();
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = sanitize(errorMessage);
    }

    public void clearError() {
        this.errorMessage = "";
    }

    public String getDefaultStateId() {
        ensureDefaultState();
        return defaultStateId;
    }

    public boolean isDefaultState(String stateId) {
        if (stateId == null || stateId.isBlank()) {
            return false;
        }

        return stateId.equals(getDefaultStateId());
    }

    public boolean setDefaultState(String stateId) {
        ensureDefaultState();

        if (stateId == null || stateId.isBlank()) {
            return false;
        }

        if (!hasStateId(stateId)) {
            return false;
        }

        defaultStateId = stateId;
        clearError();

        return true;
    }

    private void ensureDefaultState() {
        if (states.isEmpty()) {
            TokenStateDraft defaultState = new TokenStateDraft("1", "Normal");
            states.add(defaultState);
            selectedStateId = defaultState.getId();
            defaultStateId = defaultState.getId();
        }

        if (selectedStateId == null || selectedStateId.isBlank()) {
            selectedStateId = states.get(0).getId();
        }

        if (defaultStateId == null || defaultStateId.isBlank() || !hasStateId(defaultStateId)) {
            defaultStateId = states.get(0).getId();
        }
    }



    private String createNextStateId() {
        int id = 1;

        while (hasStateId(Integer.toString(id))) {
            id++;
        }

        return Integer.toString(id);
    }

    private boolean hasStateId(String stateId) {
        for (TokenStateDraft state : states) {
            if (state.getId().equals(stateId)) {
                return true;
            }
        }

        return false;
    }

    private void syncMainImageFromState(TokenStateDraft state) {
        if (state == null || !state.hasImage()) {
            clearMainImage();
            return;
        }

        this.selectedImageId = state.getImageId();
        this.selectedImageDisplayName = state.getImageDisplayName();
        this.selectedImageTexture = state.getImageTexture();
        this.selectedImageWidth = state.getImageWidth();
        this.selectedImageHeight = state.getImageHeight();
    }

    private void clearMainImage() {
        this.selectedImageId = null;
        this.selectedImageDisplayName = null;
        this.selectedImageTexture = null;
        this.selectedImageWidth = 0;
        this.selectedImageHeight = 0;
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }

        return value;
    }
}