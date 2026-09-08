package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression contract: Shade drag must not continuously reconfigure the native root blur radius. */
public final class ShadeCachedBackdropArchitectureTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    public void rootBlurPolicyLatchesAnyVisibleShadeToOneRadius() throws Exception {
        String policy = read(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationShadeBlurPolicy.java");

        assertTrue(policy.contains("requested > 0f ? 1f : 0f"));
    }

    @Test
    public void noFullScreenLiquidUiBackdropIsIntroducedForThisOptimization() throws Exception {
        String authority = read(
                "src/main/java/com/hellovoid/liquidui/glass/notification/ShadeWindowGlassAuthority.java");
        String session = read(
                "src/main/java/com/hellovoid/liquidui/glass/core/WindowGlassSession.java");

        assertFalse(authority.contains("setBackdropFillEnabled"));
        assertFalse(session.contains("setBackdropFillEnabled"));
    }
}
