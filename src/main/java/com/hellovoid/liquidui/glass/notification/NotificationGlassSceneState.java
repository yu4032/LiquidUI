package com.hellovoid.liquidui.glass.notification;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Thread-safe publication point between SystemUI UI-thread geometry and the EGL thread. */
final class NotificationGlassSceneState {
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<NotificationGlassSceneSnapshot> latest =
            new AtomicReference<>(NotificationGlassSceneSnapshot.EMPTY);

    NotificationGlassSceneSnapshot latest() { return latest.get(); }

    NotificationGlassSceneSnapshot publish(List<NotificationGlassNode> nodes) {
        NotificationGlassSceneSnapshot next = new NotificationGlassSceneSnapshot(
                generation.incrementAndGet(), nodes);
        latest.set(next);
        return next;
    }

    void clear() { publish(List.of()); }
}
