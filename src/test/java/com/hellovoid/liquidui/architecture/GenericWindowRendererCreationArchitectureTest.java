package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GenericWindowRendererCreationArchitectureTest {
    @Test
    public void onlyVerifiedShadeWindowAuthorityCreatesTheShadeRenderer() throws Exception {
        String genericHost = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/systemui/SystemUiGlassWindowHost.java"));
        String notification = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassAdapter.java"));
        Path authorityPath = Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/ShadeWindowGlassAuthority.java");

        assertTrue(genericHost.contains("session.sceneHost()"));
        assertFalse(genericHost.contains("attachRenderer("));

        // Component adapters publish nodes only. Their page-local hierarchy must never own the
        // shared output TextureView.
        assertTrue(notification.contains("glassCore.sessionFor(stack)"));
        assertTrue(notification.contains("session.sceneHost()"));
        assertFalse(notification.contains("attachRenderer("));

        assertTrue(Files.exists(authorityPath));
        String authority = Files.readString(authorityPath);
        assertTrue(authority.contains("session.attachRenderer("));
        assertTrue(authority.contains("NotificationShadeWindowView"));
        assertTrue(authority.contains("SharedNotificationContainer"));
        assertTrue(authority.contains("ControlCenterContainer"));
        assertTrue(authority.contains("root, root, insertionIndex, true"));
    }
}
