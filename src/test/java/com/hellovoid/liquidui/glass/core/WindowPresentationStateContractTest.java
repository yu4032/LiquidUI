package com.hellovoid.liquidui.glass.core;

import org.junit.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WindowPresentationStateContractTest {
    @Test
    public void newlyAttachedNodeWaitsForItsOwnSuccessfulSwap() {
        WindowPresentationState state = freshState(3L, 1L, Map.of("old", 1L));
        assertEquals(Set.of("old"), state.accept(token(
                3L, 1L, 1L, 1L, Map.of("old", 1L))));

        state.scene(2L, Map.of("old", 1L, "new", 7L));

        assertTrue(state.accept(token(
                3L, 1L, 2L, 2L, Map.of("old", 1L))).isEmpty());
        assertEquals(Set.of("new"), state.accept(token(
                3L, 1L, 2L, 3L, Map.of("old", 1L, "new", 7L))));
    }

    @Test
    public void staleProducerLifecycleAndProfileTokensAreRejected() {
        WindowPresentationState state = freshState(9L, 12L, Map.of("tile", 5L));

        assertTrue(state.accept(token(
                8L, 12L, 1L, 1L, Map.of("tile", 5L))).isEmpty());
        assertTrue(state.accept(token(
                9L, 11L, 1L, 2L, Map.of("tile", 5L))).isEmpty());
        assertTrue(state.accept(new GlassActivationToken(
                9L, 12L, 1L, 2L, 3L, Map.of("tile", 5L))).isEmpty());
        assertTrue(state.accept(token(
                9L, 12L, 1L, 4L, Map.of("tile", 4L))).isEmpty());
    }

    @Test
    public void sourceLossRevokesEveryPresentedNode() {
        WindowPresentationState state = freshState(
                4L, 6L, Map.of("a", 1L, "b", 2L));
        assertEquals(Set.of("a", "b"), state.accept(token(
                4L, 6L, 1L, 1L, Map.of("a", 1L, "b", 2L))));

        assertEquals(Set.of("a", "b"), state.sourceLost(6L));
        assertEquals(WindowPresentationState.Phase.SOURCE_LOST, state.phase());
        assertTrue(state.accept(token(
                4L, 6L, 1L, 2L, Map.of("a", 1L, "b", 2L))).isEmpty());
    }

    private static WindowPresentationState freshState(
            long rootGeneration,
            long producerGeneration,
            Map<String, Long> nodes) {
        WindowPresentationState state = new WindowPresentationState();
        state.sourceBound(rootGeneration, producerGeneration, 1L);
        state.freshFrame(rootGeneration, producerGeneration);
        state.scene(1L, nodes);
        return state;
    }

    private static GlassActivationToken token(
            long rootGeneration,
            long producerGeneration,
            long sceneGeneration,
            long swapSequence,
            Map<String, Long> nodes) {
        return new GlassActivationToken(
                rootGeneration,
                producerGeneration,
                sceneGeneration,
                1L,
                swapSequence,
                nodes);
    }
}
