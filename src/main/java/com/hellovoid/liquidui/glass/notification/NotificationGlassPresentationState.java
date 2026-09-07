package com.hellovoid.liquidui.glass.notification;

/**
 * Pure presentation-authority state shared between the UI/session and EGL renderer.
 *
 * A framework PassBlur bind is not presentation success. Glass becomes authoritative only after
 * the current source generation produced a fresh frame, the current scene has drawable nodes, and
 * an optical frame for both generations completed eglSwapBuffers().
 */
final class NotificationGlassPresentationState {
    enum Phase {
        FALLBACK_NATIVE,
        SOURCE_BOUND,
        SOURCE_FRESH,
        GLASS_ACTIVE,
        SOURCE_LOST,
        TERMINAL_FAILURE
    }

    record ActivationToken(long sourceGeneration, long sceneGeneration, long swapSequence) {}

    private Phase phase = Phase.FALLBACK_NATIVE;
    private long sourceGeneration = -1L;
    private long sceneGeneration = -1L;
    private int drawableNodes;
    private long swapSequence;
    private boolean fresh;

    synchronized void sourceBound(long generation) {
        if (phase == Phase.TERMINAL_FAILURE) return;
        sourceGeneration = generation;
        fresh = false;
        phase = Phase.SOURCE_BOUND;
    }

    synchronized void freshFrame(long generation) {
        if (generation != sourceGeneration
                || phase == Phase.SOURCE_LOST
                || phase == Phase.FALLBACK_NATIVE
                || phase == Phase.TERMINAL_FAILURE) {
            return;
        }
        fresh = true;
        phase = Phase.SOURCE_FRESH;
    }

    synchronized void scene(long generation, int nodes) {
        sceneGeneration = generation;
        drawableNodes = Math.max(0, nodes);
        if (drawableNodes == 0 && phase == Phase.GLASS_ACTIVE) {
            phase = fresh ? Phase.SOURCE_FRESH : Phase.FALLBACK_NATIVE;
        }
    }

    synchronized ActivationToken swapSucceeded(long source, long scene, long swap) {
        if ((phase != Phase.SOURCE_FRESH && phase != Phase.GLASS_ACTIVE)
                || source != sourceGeneration
                || scene != sceneGeneration
                || !fresh
                || drawableNodes <= 0
                || swap <= 0L) {
            return null;
        }
        swapSequence = swap;
        phase = Phase.GLASS_ACTIVE;
        return new ActivationToken(source, scene, swap);
    }

    synchronized boolean accept(ActivationToken token) {
        return token != null
                && phase == Phase.GLASS_ACTIVE
                && token.sourceGeneration() == sourceGeneration
                && token.sceneGeneration() == sceneGeneration
                && token.swapSequence() == swapSequence;
    }

    synchronized void sourceLost(long generation) {
        if (generation != sourceGeneration || phase == Phase.TERMINAL_FAILURE) return;
        fresh = false;
        phase = Phase.SOURCE_LOST;
    }

    synchronized void fallbackNative() {
        if (phase == Phase.TERMINAL_FAILURE) return;
        fresh = false;
        phase = Phase.FALLBACK_NATIVE;
    }

    synchronized void terminalFailure() {
        fresh = false;
        phase = Phase.TERMINAL_FAILURE;
    }

    synchronized boolean isGlassActive() {
        return phase == Phase.GLASS_ACTIVE;
    }

    synchronized Phase phase() {
        return phase;
    }

    synchronized long sourceGeneration() {
        return sourceGeneration;
    }

    synchronized long sceneGeneration() {
        return sceneGeneration;
    }
}
