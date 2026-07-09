package com.petrick.vtt.feature.asset.animation;

import java.util.List;

/**
 * Textura animada carregada em memória.
 */
public final class AnimatedTexture {

    private final String id;

    private final List<AnimatedTextureFrame> frames;

    private final int totalDurationMs;

    public AnimatedTexture(
            String id,
            List<AnimatedTextureFrame> frames
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Animated texture id cannot be null or blank");
        }

        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("Animated texture must have at least one frame");
        }

        this.id = id;
        this.frames = List.copyOf(frames);

        int duration = 0;

        for (AnimatedTextureFrame frame : frames) {
            duration += Math.max(1, frame.durationMs());
        }

        this.totalDurationMs = Math.max(1, duration);
    }

    public String id() {
        return id;
    }

    public List<AnimatedTextureFrame> frames() {
        return frames;
    }

    public int frameCount() {
        return frames.size();
    }

    public AnimatedTextureFrame frameAtTime(long timeMs) {
        if (frames.size() == 1) {
            return frames.get(0);
        }

        int localTime = (int) Math.floorMod(timeMs, totalDurationMs);

        int accumulated = 0;

        for (AnimatedTextureFrame frame : frames) {
            accumulated += Math.max(1, frame.durationMs());

            if (localTime < accumulated) {
                return frame;
            }
        }

        return frames.get(frames.size() - 1);
    }
}