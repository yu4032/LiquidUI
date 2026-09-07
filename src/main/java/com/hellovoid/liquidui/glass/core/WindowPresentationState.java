package com.hellovoid.liquidui.glass.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Pure per-Window and per-node presentation authority. */
public final class WindowPresentationState {
    public enum Phase {
        NATIVE_FALLBACK,
        SOURCE_BOUND,
        SOURCE_FRESH,
        GLASS_ACTIVE,
        SOURCE_LOST,
        RECOVERING,
        TERMINAL_FAILURE,
        DETACHED
    }

    private Phase phase = Phase.NATIVE_FALLBACK;
    private long rootGeneration = -1L;
    private long producerGeneration = -1L;
    private long sceneGeneration = -1L;
    private long profileVersion = -1L;
    private long lastSwapSequence;
    private boolean fresh;
    private Map<String, Long> currentNodes = Map.of();
    private final Map<String, Long> presentedNodes = new HashMap<>();

    public synchronized Set<String> sourceBound(
            long root, long producer, long profile) {
        if (phase == Phase.TERMINAL_FAILURE || phase == Phase.DETACHED) return Set.of();
        Set<String> revoked = revokePresented();
        rootGeneration = root;
        producerGeneration = producer;
        profileVersion = profile;
        lastSwapSequence = 0L;
        fresh = false;
        phase = Phase.SOURCE_BOUND;
        return revoked;
    }

    public synchronized void freshFrame(long root, long producer) {
        if (phase != Phase.SOURCE_BOUND && phase != Phase.RECOVERING) return;
        if (root != rootGeneration || producer != producerGeneration) return;
        fresh = true;
        phase = Phase.SOURCE_FRESH;
    }

    public synchronized Set<String> scene(long generation, Map<String, Long> nodes) {
        if (generation < sceneGeneration) return Set.of();
        sceneGeneration = generation;
        currentNodes = Map.copyOf(nodes);
        Set<String> revoked = new HashSet<>();
        presentedNodes.entrySet().removeIf(entry -> {
            Long current = currentNodes.get(entry.getKey());
            boolean remove = current == null || current.longValue() != entry.getValue();
            if (remove) revoked.add(entry.getKey());
            return remove;
        });
        if (currentNodes.isEmpty() && phase == Phase.GLASS_ACTIVE) {
            phase = fresh ? Phase.SOURCE_FRESH : Phase.NATIVE_FALLBACK;
        }
        return Set.copyOf(revoked);
    }

    public synchronized Set<String> accept(GlassActivationToken token) {
        if (token == null || !fresh) return Set.of();
        if (phase != Phase.SOURCE_FRESH && phase != Phase.GLASS_ACTIVE) return Set.of();
        if (token.rootGeneration() != rootGeneration
                || token.producerGeneration() != producerGeneration
                || token.profileVersion() != profileVersion
                || token.sceneGeneration() < 0L
                || token.sceneGeneration() > sceneGeneration
                || token.swapSequence() <= lastSwapSequence) {
            return Set.of();
        }

        Set<String> newlyPresented = new HashSet<>();
        for (Map.Entry<String, Long> rendered : token.renderedNodes().entrySet()) {
            Long current = currentNodes.get(rendered.getKey());
            if (current == null || current.longValue() != rendered.getValue()) continue;
            Long previous = presentedNodes.put(rendered.getKey(), rendered.getValue());
            if (previous == null || previous.longValue() != rendered.getValue()) {
                newlyPresented.add(rendered.getKey());
            }
        }
        lastSwapSequence = token.swapSequence();
        if (!presentedNodes.isEmpty()) phase = Phase.GLASS_ACTIVE;
        return Set.copyOf(newlyPresented);
    }

    public synchronized Set<String> sourceLost(long producer) {
        if (producer != producerGeneration
                || phase == Phase.TERMINAL_FAILURE
                || phase == Phase.DETACHED) {
            return Set.of();
        }
        fresh = false;
        phase = Phase.SOURCE_LOST;
        return revokePresented();
    }

    public synchronized Set<String> terminalFailure() {
        fresh = false;
        phase = Phase.TERMINAL_FAILURE;
        return revokePresented();
    }

    public synchronized Set<String> detach() {
        fresh = false;
        phase = Phase.DETACHED;
        currentNodes = Map.of();
        return revokePresented();
    }

    public synchronized Phase phase() {
        return phase;
    }

    public synchronized long producerGeneration() {
        return producerGeneration;
    }

    private Set<String> revokePresented() {
        if (presentedNodes.isEmpty()) return Set.of();
        Set<String> revoked = Set.copyOf(presentedNodes.keySet());
        presentedNodes.clear();
        return revoked;
    }
}
