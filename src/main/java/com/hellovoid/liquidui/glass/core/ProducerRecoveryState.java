package com.hellovoid.liquidui.glass.core;

/**
 * Android-free producer rollover/fresh-frame state shared by every Window glass renderer.
 * A producer generation becomes presentation-ready only after both a successful framework bind
 * and a fresh frame from that exact generation. The higher-level rebind methods preserve the
 * validated renderer's single-flight recovery semantics while generation tracking prevents stale
 * endpoints from regaining authority.
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

        static Decision bind() {
            return new Decision(true, false, false, false, true);
        }

        static Decision invalidateFrame() {
            return new Decision(true, false, true, false, false);
        }
    }

    private long nextGeneration;
    private long currentGeneration;
    private long boundGeneration;
    private long freshGeneration;
    private long lastSuccessfulGeneration;
    private boolean recreateRequired = true;
    private boolean rebindPending;
    private boolean activationExhausted;

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
        rebindPending = true;
        activationExhausted = false;
        return Decision.rebind();
    }

    public synchronized void onBindSucceeded(long generation) {
        if (generation == 0L || generation != currentGeneration || recreateRequired) return;
        boundGeneration = generation;
        lastSuccessfulGeneration = generation;
        rebindPending = false;
        activationExhausted = false;
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
                && !recreateRequired
                && !activationExhausted;
    }

    /** Validated renderer entry point: accept only one producer rollover at a time. */
    synchronized Decision onRebindRequested() {
        if (rebindPending) return Decision.none();
        rebindPending = true;
        activationExhausted = false;
        boundGeneration = 0L;
        freshGeneration = 0L;
        recreateRequired = true;
        return Decision.rebind();
    }

    /** Called after a new SurfaceTexture/Surface producer endpoint has been created. */
    synchronized Decision onProducerRecreated() {
        if (!rebindPending) return Decision.none();
        return Decision.bind();
    }

    /** Renderer convenience overload for the current generation. */
    synchronized void onBindSucceeded() {
        onBindSucceeded(currentGeneration);
    }

    synchronized void onBindExhausted() {
        rebindPending = false;
        boundGeneration = 0L;
        freshGeneration = 0L;
        activationExhausted = true;
    }

    synchronized void onRecreateFailed() {
        onBindExhausted();
    }

    synchronized Decision onGeometryInvalidated() {
        freshGeneration = 0L;
        return Decision.invalidateFrame();
    }

    synchronized void onFreshFrameConsumed() {
        onFreshFrame(currentGeneration);
    }

    synchronized void onTerminalFailure() {
        onBindExhausted();
    }

    synchronized void onShutdown() {
        shutdown();
    }

    synchronized boolean isRebindPending() {
        return rebindPending;
    }

    synchronized boolean hasFreshFrame() {
        return currentGeneration != 0L
                && currentGeneration == freshGeneration
                && !recreateRequired
                && !activationExhausted;
    }

    synchronized boolean isActivationExhausted() {
        return activationExhausted;
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
        rebindPending = false;
        activationExhausted = false;
    }
}
