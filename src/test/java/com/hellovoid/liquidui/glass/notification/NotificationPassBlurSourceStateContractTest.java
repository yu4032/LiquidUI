package com.hellovoid.liquidui.glass.notification;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class NotificationPassBlurSourceStateContractTest {
    @Test
    public void sourceIsFailClosedUntilDisplayAreaLeashAppears() {
        NotificationPassBlurSourceState state = new NotificationPassBlurSourceState();
        NotificationPassBlurSourceState.Snapshot snapshot = state.snapshot(0);
        assertFalse(snapshot.available());
        assertEquals(0L, snapshot.generation());
    }

    @Test
    public void sourceGenerationChangesOnlyWhenDisplayLeashIdentityChanges() {
        NotificationPassBlurSourceState state = new NotificationPassBlurSourceState();
        Object first = new Object();
        Object second = new Object();
        state.observe(0, first);
        long g1 = state.snapshot(0).generation();
        state.observe(0, first);
        assertEquals(g1, state.snapshot(0).generation());
        state.observe(0, second);
        assertTrue(state.snapshot(0).generation() > g1);
        assertTrue(state.snapshot(0).source() == second);
    }

    @Test
    public void sourceListenersAreDisplayScopedAndVanishClearsOnlyMatchingLeash() {
        NotificationPassBlurSourceState state = new NotificationPassBlurSourceState();
        List<Integer> changes = new ArrayList<>();
        state.addListener(0, snapshot -> changes.add(snapshot.available() ? 1 : 0));
        Object display0 = new Object();
        Object display1 = new Object();
        state.observe(1, display1);
        assertTrue(changes.isEmpty());
        state.observe(0, display0);
        assertEquals(List.of(1), changes);
        state.remove(0, new Object());
        assertEquals(List.of(1), changes);
        state.remove(0, display0);
        assertEquals(List.of(1, 0), changes);
    }
}
