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
        assertTrue(notification.contains("authorityState.isEnabled()"));

        // A generic plugin/control-center/volume Window has no such authority yet. It may create
        // a renderer for shared composition, but it must leave the vendor PassBlur endpoint closed.
        assertTrue(genericHost.contains("attachRenderer(anchor, rootGroup, 0, false)"));
        assertFalse(genericHost.contains("attachRenderer(anchor, rootGroup, 0, true)"));
    }
}
