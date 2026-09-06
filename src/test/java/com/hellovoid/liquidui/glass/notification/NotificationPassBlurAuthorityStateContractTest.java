package com.hellovoid.liquidui.glass.notification;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class NotificationPassBlurAuthorityStateContractTest {
    @Test
    public void authorityIsFailClosedUntilHyperOsPublishesNotifPassBlur() {
        NotificationPassBlurAuthorityState state = new NotificationPassBlurAuthorityState();
        assertFalse(state.isKnown());
        assertFalse(state.isEnabled());
    }

    @Test
    public void authorityTracksVendorValueAndNotifiesOnlyOnRealTransitions() {
        NotificationPassBlurAuthorityState state = new NotificationPassBlurAuthorityState();
        List<Boolean> changes = new ArrayList<>();
        NotificationPassBlurAuthorityState.Listener listener = changes::add;
        state.addListener(listener);

        state.observe(true);
        state.observe(true);
        state.observe(false);

        assertTrue(state.isKnown());
        assertFalse(state.isEnabled());
        assertEquals(List.of(true, false), changes);
    }

    @Test
    public void lateListenerReceivesCurrentVendorAuthorityImmediately() {
        NotificationPassBlurAuthorityState state = new NotificationPassBlurAuthorityState();
        state.observe(true);
        List<Boolean> changes = new ArrayList<>();
        NotificationPassBlurAuthorityState.Listener listener = changes::add;
        state.addListener(listener);
        assertEquals(List.of(true), changes);
        state.removeListener(listener);
        state.observe(false);
        assertEquals(List.of(true), changes);
    }
}
