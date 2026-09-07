package com.hellovoid.liquidui.glass.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ProducerRecoveryStateContractTest {
    @Test
    public void repeatedObservePreservesProducerGeneration() {
        ProducerRecoveryState state = new ProducerRecoveryState();
        long first = state.observeProducer();
        long second = state.observeProducer();

        assertEquals(first, second);
        assertEquals(1L, first);
    }

    @Test
    public void rootLossRetiresFreshnessWithoutReusingProducer() {
        ProducerRecoveryState state = new ProducerRecoveryState();
        long first = state.observeProducer();
        state.onBindSucceeded(first);
        state.onFreshFrame(first);
        assertTrue(state.isReady(first));

        ProducerRecoveryState.Decision loss = state.onRootLost(first);
        assertTrue(loss.accepted());
        assertTrue(loss.clearFrameworkBinding());
        assertTrue(loss.clearFrameAvailable());
        assertTrue(loss.recreateProducer());
        assertFalse(state.isReady(first));

        long second = state.observeProducer();
        assertTrue(second > first);
    }

    @Test
    public void failedBindDoesNotAdvanceSuccessfulGeneration() {
        ProducerRecoveryState state = new ProducerRecoveryState();
        long first = state.observeProducer();
        state.onBindSucceeded(first);
        assertEquals(first, state.lastSuccessfulGeneration());

        state.onRootLost(first);
        long second = state.observeProducer();
        state.onBindFailed(second);

        assertEquals(first, state.lastSuccessfulGeneration());
        assertFalse(state.isReady(second));
    }

    @Test
    public void reboundProducerRequiresFreshFrameBeforeReady() {
        ProducerRecoveryState state = new ProducerRecoveryState();
        long first = state.observeProducer();
        state.onBindSucceeded(first);
        state.onFreshFrame(first);
        assertTrue(state.isReady(first));

        state.onRootLost(first);
        long second = state.observeProducer();
        state.onBindSucceeded(second);
        assertFalse(state.isReady(second));

        state.onFreshFrame(second);
        assertTrue(state.isReady(second));
    }

    @Test
    public void rendererStyleRebindIsSingleFlightAndRequestsBindAfterRecreate() {
        ProducerRecoveryState state = new ProducerRecoveryState();
        long first = state.observeProducer();
        state.onBindSucceeded(first);
        state.onFreshFrame(first);

        ProducerRecoveryState.Decision firstRequest = state.onRebindRequested();
        ProducerRecoveryState.Decision duplicate = state.onRebindRequested();
        assertTrue(firstRequest.accepted());
        assertTrue(firstRequest.recreateProducer());
        assertFalse(duplicate.accepted());
        assertFalse(state.hasFreshFrame());

        long second = state.observeProducer();
        assertTrue(second > first);
        ProducerRecoveryState.Decision recreated = state.onProducerRecreated();
        assertTrue(recreated.requestBind());

        state.onBindSucceeded();
        state.onFreshFrameConsumed();
        assertTrue(state.hasFreshFrame());
        assertTrue(state.isReady(second));
    }

    @Test
    public void activationExhaustionFailsClosedUntilNextRebind() {
        ProducerRecoveryState state = new ProducerRecoveryState();
        long generation = state.observeProducer();
        state.onBindSucceeded(generation);
        state.onFreshFrame(generation);
        assertTrue(state.hasFreshFrame());

        state.onBindExhausted();
        assertTrue(state.isActivationExhausted());
        assertFalse(state.hasFreshFrame());
        assertFalse(state.isReady(generation));

        assertTrue(state.onRebindRequested().accepted());
        assertFalse(state.isActivationExhausted());
    }
}
