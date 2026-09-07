package com.hellovoid.liquidui.glass.notification;

import org.junit.Test;

import static org.junit.Assert.*;

public class NotificationGlassPresentationStateContractTest {
    @Test
    public void requiresFreshFrameSceneAndSwapForActivation() {
        NotificationGlassPresentationState state = new NotificationGlassPresentationState();
        state.sourceBound(7L);
        state.scene(11L, 2);
        assertFalse(state.isGlassActive());

        state.freshFrame(7L);
        assertFalse(state.isGlassActive());

        NotificationGlassPresentationState.ActivationToken token =
                state.swapSucceeded(7L, 11L, 1L);
        assertNotNull(token);
        assertTrue(state.accept(token));
        assertTrue(state.isGlassActive());
    }

    @Test
    public void staleTokenRejectedAfterSourceLoss() {
        NotificationGlassPresentationState state = new NotificationGlassPresentationState();
        state.sourceBound(3L);
        state.scene(4L, 1);
        state.freshFrame(3L);
        NotificationGlassPresentationState.ActivationToken token =
                state.swapSucceeded(3L, 4L, 1L);
        assertNotNull(token);

        state.sourceLost(3L);

        assertFalse(state.accept(token));
        assertFalse(state.isGlassActive());
        assertEquals(NotificationGlassPresentationState.Phase.SOURCE_LOST, state.phase());
    }

    @Test
    public void lateFreshFrameCannotResurrectLostGeneration() {
        NotificationGlassPresentationState state = new NotificationGlassPresentationState();
        state.sourceBound(5L);
        state.scene(9L, 2);
        state.sourceLost(5L);

        state.freshFrame(5L);
        NotificationGlassPresentationState.ActivationToken late =
                state.swapSucceeded(5L, 9L, 1L);

        assertNull(late);
        assertFalse(state.isGlassActive());
        assertEquals(NotificationGlassPresentationState.Phase.SOURCE_LOST, state.phase());
    }

    @Test
    public void staleSourceOrSceneCannotActivate() {
        NotificationGlassPresentationState state = new NotificationGlassPresentationState();
        state.sourceBound(9L);
        state.scene(20L, 3);
        state.freshFrame(9L);

        assertNull(state.swapSucceeded(8L, 20L, 1L));
        assertNull(state.swapSucceeded(9L, 19L, 2L));
        assertFalse(state.isGlassActive());
    }

    @Test
    public void noDrawableRowsCannotActivate() {
        NotificationGlassPresentationState state = new NotificationGlassPresentationState();
        state.sourceBound(2L);
        state.scene(6L, 0);
        state.freshFrame(2L);

        assertNull(state.swapSucceeded(2L, 6L, 1L));
        assertFalse(state.isGlassActive());
    }

    @Test
    public void newerSourceRevokesOldFreshnessAndToken() {
        NotificationGlassPresentationState state = new NotificationGlassPresentationState();
        state.sourceBound(1L);
        state.scene(1L, 1);
        state.freshFrame(1L);
        NotificationGlassPresentationState.ActivationToken old =
                state.swapSucceeded(1L, 1L, 1L);
        assertTrue(state.accept(old));

        state.sourceBound(2L);
        state.scene(2L, 1);

        assertFalse(state.accept(old));
        assertFalse(state.isGlassActive());
        assertEquals(NotificationGlassPresentationState.Phase.SOURCE_BOUND, state.phase());
    }

    @Test
    public void terminalFailureIsFailClosed() {
        NotificationGlassPresentationState state = new NotificationGlassPresentationState();
        state.sourceBound(1L);
        state.scene(1L, 1);
        state.freshFrame(1L);
        NotificationGlassPresentationState.ActivationToken token =
                state.swapSucceeded(1L, 1L, 1L);
        assertTrue(state.accept(token));

        state.terminalFailure();

        assertFalse(state.accept(token));
        assertFalse(state.isGlassActive());
        assertEquals(NotificationGlassPresentationState.Phase.TERMINAL_FAILURE, state.phase());
    }
}
