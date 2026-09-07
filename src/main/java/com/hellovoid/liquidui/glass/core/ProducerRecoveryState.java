package com.hellovoid.liquidui.glass.core;

/**
 * Android-free producer rollover/fresh-frame state shared by every Window glass renderer.
 * A producer generation becomes presentation-ready only after both a successful framework bind
 * and a fresh frame from that exact generation.
 */
public final class ProducerRecoveryState {
    public record Decision(
            boolean accepted,
            boolean clearFrameworkBinding,
            boolean clearFrameAvailable,
            boolean recreateProducer,
            boolean requestBind) {
        static Decision none() {
            return new Decision(false, false, false, false, false);
        }

        static Decision rebind() {
            return new Decision(true, true, true, true, false);
        }
    }

    private long nextGeneration;
    private long currentGeneration;
    private long boundGeneration;
    private long freshGeneration;
    private long lastSuccessfulGeneration;
    private boolean recreateRequired = true;

    /** Returns the active producer generation, creating a new logical generation when required. */
    public synchronized long observeProducer() {
        if (recreateRequired || currentGeneration == 0L) {
            currentGeneration = ++nextGeneration;
            boundGeneration = 0L;
            freshGeneration = 0L;
            recreateRequired = false;
        }
        return currentGeneration;
    }

    /** Root/endpoint loss invalidates the old generation and forces a new producer observation. */
    public synchronized Decision onRootLost(long generation) {
        if (generation == 0L || generation != currentGeneration) return Decision.none();
        boundGeneration = 0L;
        freshGeneration = 0L;
        recreateRequired = true;
        return Decision.rebind();
    }

    public synchronized void onBindSucceeded(long generation) {
        if (generation == 0L || generation != currentGeneration || recreateRequired) return;
        boundGeneration = generation;
        lastSuccessfulGeneration = generation;
    }

    public synchronized void onBindFailed(long generation) {
        if (generation == 0L || generation != currentGeneration) return;
        boundGeneration = 0L;
        freshGeneration = 0L;
    }

    public synchronized void onFreshFrame(long generation) {
        if (generation == 0L || generation != currentGeneration || recreateRequired) return;
        freshGeneration = generation;
    }

    public synchronized boolean isReady(long generation) {
        return generation != 0L
                && generation == currentGeneration
                && generation == boundGeneration
                && generation == freshGeneration
                && !recreateRequired;
    }

    public synchronized long currentGeneration() {
        return currentGeneration;
    }

    public synchronized long lastSuccessfulGeneration() {
        return lastSuccessfulGeneration;
    }

    public synchronized void shutdown() {
        currentGeneration = 0L;
        boundGeneration = 0L;
        freshGeneration = 0L;
        recreateRequired = true;
    }
}
