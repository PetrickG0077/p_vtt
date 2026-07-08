package com.petrick.vtt.editor.token;

/**
 * Dados temporários enquanto o usuário está criando um token.
 *
 * Por enquanto ainda não cria TokenDefinition.
 * Futuramente vai guardar:
 * - imagem escolhida;
 * - estados;
 * - dono/player;
 * - permissões;
 * - descrição etc.
 */
public final class TokenCreationDraft {

    private String name = "";

    private String player = "";

    private String notes = "";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = sanitize(name);
    }

    public String getPlayer() {
        return player;
    }

    public void setPlayer(String player) {
        this.player = sanitize(player);
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = sanitize(notes);
    }

    public boolean hasName() {
        return name != null && !name.isBlank();
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }

        return value;
    }
}