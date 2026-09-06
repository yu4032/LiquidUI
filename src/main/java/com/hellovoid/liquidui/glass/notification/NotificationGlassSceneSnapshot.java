package com.hellovoid.liquidui.glass.notification;

import java.util.List;

/** Immutable scene generation consumed by the EGL thread. */
final class NotificationGlassSceneSnapshot {
    static final NotificationGlassSceneSnapshot EMPTY =
            new NotificationGlassSceneSnapshot(0L, List.of());

    final long generation;
    final List<NotificationGlassNode> nodes;

    NotificationGlassSceneSnapshot(long generation, List<NotificationGlassNode> nodes) {
        this.generation = generation;
        this.nodes = List.copyOf(nodes);
    }

    int size() { return nodes.size(); }
}
