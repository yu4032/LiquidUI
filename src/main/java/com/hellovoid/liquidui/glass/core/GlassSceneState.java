package com.hellovoid.liquidui.glass.core;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Thread-safe publication point between component adapters and the shared render thread. */
public final class GlassSceneState {
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<GlassSceneSnapshot> latest =
            new AtomicReference<>(GlassSceneSnapshot.EMPTY);

    public GlassSceneSnapshot latest() {
        return latest.get();
    }

    public GlassSceneSnapshot publish(List<GlassNode> nodes) {
        GlassSceneSnapshot next = new GlassSceneSnapshot(
                generation.incrementAndGet(),
                Objects.requireNonNull(nodes, "nodes"));
        latest.set(next);
        return next;
    }

    public void clear() {
        publish(List.of());
    }
}
