package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/** Guards the exact systemui-001 source of notifPassBlur / ctrlPassBlur. */
public final class ShadePassBlurFlowAuthorityArchitectureTest {
    @Test
    public void aggregateAuthorityBootstrapsFromCurrentVendorViews() throws Exception {
        String authority = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/ShadeWindowGlassAuthority.java"));
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationSharedGlassHook.java"));

        // setPassWindowBlurEnabled is edge-triggered by HyperOS. A Window authority created later
        // must first read the already-applied values from both exact page roots.
        assertTrue(authority.contains("NotificationPanelView"));
        assertTrue(authority.contains("ControlCenterContainer"));
        assertTrue(authority.contains("getPassWindowBlurEnabled"));
        assertTrue(authority.contains("observeCurrentPassBlurAuthority("));
        assertTrue(authority.contains("authorityState.observeNotification("));
        assertTrue(authority.contains("authorityState.observeControlCenter("));

        // Future vendor changes continue to be mirrored by the exact View setter hook.
        assertTrue(hook.contains("setPassWindowBlurEnabled"));
        assertTrue(hook.contains("authorityState.observeNotification("));
        assertTrue(hook.contains("authorityState.observeControlCenter("));
    }
}
