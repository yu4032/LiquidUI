package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Guards the separation between native page PassBlur state and LiquidUI's Window producer. */
public final class ShadeWindowProducerGateArchitectureTest {
    @Test
    public void pagePassBlurStateMustNotGateLiquidUiWindowProducer() throws Exception {
        String authority = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/ShadeWindowGlassAuthority.java"));
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationSharedGlassHook.java"));

        // HyperOS notifPassBlur/ctrlPassBlur describe native page material state. They are not
        // permission for LiquidUI's own SetPassBlurSurface consumer on the stable Shade ViewRoot.
        assertTrue(authority.contains("session.attachRenderer(\n                root, root, insertionIndex, true)"));
        assertFalse(authority.contains("session.setVendorPassBlurEnabled("));
        assertFalse(authority.contains("authorityState.addListener("));

        // Keep observing both native states for diagnostics/material policy only.
        assertTrue(hook.contains("authorityState.observeNotification("));
        assertTrue(hook.contains("authorityState.observeControlCenter("));
    }
}
