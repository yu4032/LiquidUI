package com.hellovoid.liquidui.glass.core;

import java.util.Objects;

/** Coalesces source and scene changes into a bounded latest-state render queue. */
public final class FrameCoordinator {
    public interface Poster {
        void post(Runnable command);
    }

    public interface Drain {
        void render(boolean sourcePending, boolean scenePending);
    }

    private final Poster poster;
    private final Drain drain;
    private boolean sourcePending;
    private boolean scenePending;
    private boolean queued;
    private boolean cancelled;

    public FrameCoordinator(Poster poster, Drain drain) {
        this.poster = Objects.requireNonNull(poster, "poster");
        this.drain = Objects.requireNonNull(drain, "drain");
    }

    public synchronized void requestSourceFrame() {
        request(true, false);
    }

    public synchronized void requestScene() {
        request(false, true);
    }

    public synchronized void cancel() {
        cancelled = true;
        sourcePending = false;
        scenePending = false;
    }

    private void request(boolean source, boolean scene) {
        if (cancelled) return;
        sourcePending |= source;
        scenePending |= scene;
        if (!queued) {
            queued = true;
            poster.post(this::drainOnce);
        }
    }

    private void drainOnce() {
        final boolean source;
        final boolean scene;
        synchronized (this) {
            if (cancelled) {
                queued = false;
                return;
            }
            source = sourcePending;
            scene = scenePending;
            sourcePending = false;
            scenePending = false;
        }

        drain.render(source, scene);

        synchronized (this) {
            queued = false;
            // Scene state is latest-state data and renderPendingFrame reads it again during the
            // active draw. A scene request arriving while GL is busy therefore must not create an
            // immediate self-loop; the next real UI scene request will drain the latest state.
            // A fresh OES source frame is different: updateTexImage() must eventually consume it,
            // so preserve exactly one follow-up when source work arrived during the draw.
            if (!cancelled && sourcePending) {
                queued = true;
                poster.post(this::drainOnce);
            }
        }
    }
}
