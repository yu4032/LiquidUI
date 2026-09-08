package com.hellovoid.liquidui.glass.notification;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class NotificationPassBlurAuthorityStateContractTest {
    @Test
    public void authorityIsFailClosedUntilHyperOsPublishesEitherShadeAuthority() {
        NotificationPassBlurAuthorityState state = new NotificationPassBlurAuthorityState();
        assertFalse(state.isKnown());
        assertFalse(state.isEnabled());
        assertFalse(state.isNotificationEnabled());
        assertFalse(state.isControlCenterEnabled());
    }

    @Test
    public void eitherNotificationOrControlCenterKeepsSharedWindowAuthorityEnabled() {
        NotificationPassBlurAuthorityState state = new NotificationPassBlurAuthorityState();
        List<Boolean> changes = new ArrayList<>();
        NotificationPassBlurAuthorityState.Listener listener = changes::add;
        state.addListener(listener);

        state.observeNotification(true);
        assertTrue(state.isEnabled());
        assertTrue(state.isNotificationEnabled());
        assertFalse(state.isControlCenterEnabled());

        // This is the real notification -> Control Center handoff. The Window producer must not
        // retire between the two page-specific vendor authorities.
        state.observeControlCenter(true);
        state.observeNotification(false);
        assertTrue(state.isEnabled());
        assertFalse(state.isNotificationEnabled());
        assertTrue(state.isControlCenterEnabled());

        state.observeControlCenter(false);
        assertFalse(state.isEnabled());
        assertEquals(List.of(true, false), changes);
    }

    @Test
    public void aggregateListenerDoesNotToggleWhenAuthorityTransfersBetweenPages() {
        NotificationPassBlurAuthorityState state = new NotificationPassBlurAuthorityState();
        List<Boolean> changes = new ArrayList<>();
        state.addListener(changes::add);

        state.observeNotification(true);
        state.observeControlCenter(true);
        state.observeNotification(false);
        state.observeNotification(false);
        assertEquals(List.of(true), changes);

        state.observeControlCenter(false);
        assertEquals(List.of(true, false), changes);
    }

    @Test
    public void lateListenerReceivesCurrentAggregateAuthorityImmediately() {
        NotificationPassBlurAuthorityState state = new NotificationPassBlurAuthorityState();
        state.observeControlCenter(true);
        List<Boolean> changes = new ArrayList<>();
        NotificationPassBlurAuthorityState.Listener listener = changes::add;
        state.addListener(listener);
        assertTrue(state.isKnown());
        assertEquals(List.of(true), changes);
        state.removeListener(listener);
        state.observeControlCenter(false);
        assertEquals(List.of(true), changes);
    }
}
