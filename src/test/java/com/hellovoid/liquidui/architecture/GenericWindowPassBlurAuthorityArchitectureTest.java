package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GenericWindowPassBlurAuthorityArchitectureTest {
    @Test
    public void genericWindowsMustNotOpenVendorPassBlurWithoutVerifiedAuthority() throws Exception {
        String genericHost = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/systemui/SystemUiGlassWindowHost.java"));
        String notification = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassAdapter.java"));
        String authority = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/ShadeWindowGlassAuthority.java"));
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationSharedGlassHook.java"));

        // The exact Shade Window authority owns producer enablement. Page/component adapters do not.
        assertTrue(authority.contains("session.attachRenderer("));
        assertTrue(authority.contains("authorityState.isEnabled()"));
        assertTrue(authority.contains("session.setVendorPassBlurEnabled("));
        assertFalse(notification.contains("session.setVendorPassBlurEnabled("));
        assertFalse(notification.contains("attachRenderer("));

        // Both independent HyperOS authorities are observed before aggregation.
        assertTrue(hook.contains("observeNotification("));
        assertTrue(hook.contains("observeControlCenter("));

        // Generic plugin/control-center/volume adapters have no producer authority of their own.
        assertTrue(genericHost.contains("session.sceneHost()"));
        assertFalse(genericHost.contains("attachRenderer("));
        assertFalse(genericHost.contains("SetPassBlurSurface"));
    }
}
