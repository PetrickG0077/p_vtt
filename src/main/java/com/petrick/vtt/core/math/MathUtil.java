package com.petrick.vtt.core.math;

/**
 * Funções matemáticas utilizadas por todo o VTT.
 */
public final class MathUtil {

    public static final double EPSILON = 1e-9;

    public static final double HALF_PI = Math.PI / 2.0;

    private MathUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Limita um valor entre um mínimo e um máximo.
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Interpolação linear.
     */
    public static double lerp(double start, double end, double alpha) {
        return start + (end - start) * alpha;
    }

    /**
     * Alinha um valor ao múltiplo mais próximo do tamanho da grade.
     */
    public static double snapToGrid(double value, double gridSize) {

        if (gridSize <= 0.0) {
            return value;
        }

        return Math.round(value / gridSize) * gridSize;
    }

    /**
     * Compara dois doubles considerando uma margem de erro.
     */
    public static boolean nearlyEquals(double a, double b, double epsilon) {
        return Math.abs(a - b) <= epsilon;
    }

}