package com.hellovoid.liquidui.glass.core;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Lifecycle shell for one Window; Task 6 attaches its session-local GPU renderer. */
public class WindowGlassSession implements AutoCloseable {
    private final WindowKey key;
    private final GlassSceneState sceneState = new GlassSceneState();
    private final WindowPresentationState presentationState = new WindowPresentationState();
    private final AtomicBoolean closed = new AtomicBoolean();

    public WindowGlassSession(WindowKey key) {
        this.key = Objects.requireNonNull(key, "key");
    }

    public WindowKey key() {
        return key;
    }

    public GlassSceneState sceneState() {
        return sceneState;
    }

    public WindowPresentationState presentationState() {
        return presentationState;
    }

    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        sceneState.clear();
        presentationState.detach();
    }
}
