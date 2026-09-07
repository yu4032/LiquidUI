package com.hellovoid.liquidui.glass.core;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** One immutable, deterministically ordered Window scene generation. */
public record GlassSceneSnapshot(long generation, List<GlassNode> nodes) {
    public static final GlassSceneSnapshot EMPTY = new GlassSceneSnapshot(0L, List.of());

    public GlassSceneSnapshot {
        nodes = Objects.requireNonNull(nodes, "nodes").stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(GlassNode::zOrder).thenComparing(GlassNode::id))
                .toList();
    }
}
