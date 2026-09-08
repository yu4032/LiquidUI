package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GenericWindowPassBlurAuthorityArchitectureTest {
    @Test
    public void genericWindowMustNotOpenVendorPassBlurWithoutVerifiedAuthority() throws Exception {
        String genericHost = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/systemui/SystemUiGlassWindowHost.java"));
        String notification = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassAdapter.java"));

        // Notification has an explicit, reverse-engineered authority state for its known Shade root.
        assertTrue(notification.contains("session.attachRenderer("));
        assertTrue(notification.contains("authorityState.isEnabled()"));

        // Generic plugin/control-center/volume adapters have no producer authority of their own.
        // They may only reuse a renderer that an exact authority owner already established.
        assertTrue(genericHost.contains("session.sceneHost()"));
        assertFalse(genericHost.contains("attachRenderer("));
        assertFalse(genericHost.contains("SetPassBlurSurface"));
    }
}
