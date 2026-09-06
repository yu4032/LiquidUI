package com.hellovoid.liquidui.glass.notification;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProducerRecreateReadinessStateContractTest {
    @Test
    public void startupRecreateDefersUntilOutputEglBecomesReady() {
        ProducerRecreateReadinessState state = new ProducerRecreateReadinessState();
        assertEquals(ProducerRecreateReadinessState.Action.DEFER, state.requestRecreate());
        assertTrue(state.hasPendingRecreate());
        assertFalse(state.isOutputReady());

        assertEquals(ProducerRecreateReadinessState.Action.RUN_NOW, state.onOutputReady());
        assertFalse(state.hasPendingRecreate());
        assertTrue(state.isOutputReady());
    }

    @Test
    public void liveRecreateRunsImmediatelyButOutputLossDefersAgain() {
        ProducerRecreateReadinessState state = new ProducerRecreateReadinessState();
        assertEquals(ProducerRecreateReadinessState.Action.NONE, state.onOutputReady());
        assertEquals(ProducerRecreateReadinessState.Action.RUN_NOW, state.requestRecreate());

        state.onOutputUnavailable();
        assertEquals(ProducerRecreateReadinessState.Action.DEFER, state.requestRecreate());
        assertEquals(ProducerRecreateReadinessState.Action.RUN_NOW, state.onOutputReady());
    }

    @Test
    public void repeatedEarlyRequestsCoalesceIntoOneDeferredRecreate() {
        ProducerRecreateReadinessState state = new ProducerRecreateReadinessState();
        assertEquals(ProducerRecreateReadinessState.Action.DEFER, state.requestRecreate());
        assertEquals(ProducerRecreateReadinessState.Action.DEFER, state.requestRecreate());
        assertEquals(ProducerRecreateReadinessState.Action.RUN_NOW, state.onOutputReady());
        assertEquals(ProducerRecreateReadinessState.Action.NONE, state.onOutputReady());
    }
}
