package com.hellovoid.liquidui.glass.notification;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class NotificationPassBlurContentAuthorityStateContractTest {
    @Test
    public void lockWallpaperExclusionIsFailClosedUntilExactShadeStateArrives() {
        NotificationPassBlurContentAuthorityState state =
                new NotificationPassBlurContentAuthorityState();
        NotificationPassBlurContentAuthorityState.Snapshot snapshot = state.snapshot();
        assertFalse(snapshot.known());
        assertFalse(snapshot.excludeLockWallpaper());
        assertEquals(0L, snapshot.generation());
    }

    @Test
    public void unlockedShadeExcludesLockWallpaperWhileKeyguardAllowsIt() {
        NotificationPassBlurContentAuthorityState state =
                new NotificationPassBlurContentAuthorityState();
        state.observe(false);
        NotificationPassBlurContentAuthorityState.Snapshot unlocked = state.snapshot();
        assertTrue(unlocked.known());
        assertFalse(unlocked.keyguardShowing());
        assertTrue(unlocked.excludeLockWallpaper());

        state.observe(true);
        NotificationPassBlurContentAuthorityState.Snapshot locked = state.snapshot();
        assertTrue(locked.keyguardShowing());
        assertFalse(locked.excludeLockWallpaper());
        assertTrue(locked.generation() > unlocked.generation());
    }

    @Test
    public void contentGenerationChangesOnlyOnRealKeyguardTransitions() {
        NotificationPassBlurContentAuthorityState state =
                new NotificationPassBlurContentAuthorityState();
        List<Long> generations = new ArrayList<>();
        NotificationPassBlurContentAuthorityState.Listener listener =
                snapshot -> generations.add(snapshot.generation());
        state.addListener(listener);

        state.observe(false);
        long unlockedGeneration = state.snapshot().generation();
        state.observe(false);
        assertEquals(unlockedGeneration, state.snapshot().generation());
        state.observe(true);

        assertEquals(2, generations.size());
        assertTrue(generations.get(1) > generations.get(0));
        state.removeListener(listener);
    }
}
