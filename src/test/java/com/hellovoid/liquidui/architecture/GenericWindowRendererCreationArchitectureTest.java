package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GenericWindowRendererCreationArchitectureTest {
    @Test
    public void genericAdaptersOnlyReuseAnAlreadyAuthorizedWindowRenderer() throws Exception {
        String genericHost = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/systemui/SystemUiGlassWindowHost.java"));
        String notification = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassAdapter.java"));

        assertTrue(genericHost.contains("session.sceneHost()"));
        assertTrue(genericHost.contains("if (existing != null)"));
        assertFalse(genericHost.contains("attachRenderer("));

        // Renderer creation remains with an exact authority owner, not a generic component.
        assertTrue(notification.contains("session.attachRenderer("));
        assertTrue(notification.contains("authorityState.isEnabled()"));
    }
}
