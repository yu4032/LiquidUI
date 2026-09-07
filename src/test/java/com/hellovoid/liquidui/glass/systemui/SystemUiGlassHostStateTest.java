package com.hellovoid.liquidui.glass.systemui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SystemUiGlassHostStateTest {
    @Test
    public void recycleAdvancesGenerationAndRejectsStaleAuthorization() {
        SystemUiGlassHostState state = new SystemUiGlassHostState();

        long first = state.register("tile:A");
        assertEquals(1L, first);
        assertTrue(state.isCurrent("tile:A", first));
        assertTrue(state.authorize("tile:A", first));
        assertTrue(state.authorized());

        assertTrue(state.unregister("tile:A", first));
        assertFalse(state.authorized());

        long second = state.register("tile:B");
        assertEquals(2L, second);
        assertTrue(state.isCurrent("tile:B", second));
        assertFalse(state.isCurrent("tile:A", first));
        assertFalse(state.authorize("tile:A", first));
        assertFalse(state.revoke("tile:A", first));
        assertFalse(state.authorized());
        assertTrue(state.authorize("tile:B", second));
        assertTrue(state.authorized());
    }

    @Test
    public void authorizationAndRevocationAreIdempotentForCurrentLifecycle() {
        SystemUiGlassHostState state = new SystemUiGlassHostState();
        long generation = state.register("volume:panel");

        assertTrue(state.authorize("volume:panel", generation));
        assertFalse(state.authorize("volume:panel", generation));
        assertTrue(state.revoke("volume:panel", generation));
        assertFalse(state.revoke("volume:panel", generation));
        assertFalse(state.authorized());
    }

    @Test
    public void terminalFailurePreventsFutureAuthorization() {
        SystemUiGlassHostState state = new SystemUiGlassHostState();
        long generation = state.register("media:card");
        assertTrue(state.authorize("media:card", generation));

        assertTrue(state.failTerminal());
        assertTrue(state.failed());
        assertFalse(state.authorized());
        assertFalse(state.authorize("media:card", generation));
        assertFalse(state.register("media:new") > generation);
    }
}
