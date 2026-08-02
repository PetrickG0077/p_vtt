package com.petrick.vtt.editor.hud;

import java.util.Locale;
import java.util.OptionalInt;

/** Shared formatting and input rules for editor RGB hexadecimal fields. */
public final class HexColorFormat {
    public static final int DIGIT_COUNT = 6;

    private HexColorFormat() {}

    public static String format(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0x00FFFFFF);
    }

    public static boolean accepts(char character) {
        return character == '#' || Character.digit(character, 16) >= 0;
    }

    public static String append(String current, char character) {
        String value = current == null ? "" : current;
        if (!accepts(character)) return value;
        if (character == '#') return value.isEmpty() ? "#" : value;
        int digits = value.startsWith("#") ? value.length() - 1 : value.length();
        if (digits >= DIGIT_COUNT) return value;
        return value + Character.toUpperCase(character);
    }

    public static OptionalInt parse(String text) {
        String value = text == null ? "" : text.trim();
        if (value.startsWith("#")) value = value.substring(1);
        if (value.length() != DIGIT_COUNT) return OptionalInt.empty();
        try {
            return OptionalInt.of(Integer.parseUnsignedInt(value, 16) & 0x00FFFFFF);
        } catch (NumberFormatException ignored) {
            return OptionalInt.empty();
        }
    }
}
