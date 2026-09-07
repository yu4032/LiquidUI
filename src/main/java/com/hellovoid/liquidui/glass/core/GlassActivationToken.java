package com.hellovoid.liquidui.glass.core;

import java.util.Map;
import java.util.Objects;

/** Proof that exact node generations reached a successful Window output swap. */
public record GlassActivationToken(
        long rootGeneration,
        long producerGeneration,
        long sceneGeneration,
        long profileVersion,
        long swapSequence,
        Map<String, Long> renderedNodes) {
    public GlassActivationToken {
        renderedNodes = Map.copyOf(Objects.requireNonNull(renderedNodes, "renderedNodes"));
    }
}
