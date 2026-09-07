package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class NotificationRedDebugArchitectureTest {
    @Test
    public void redDebugUsesDeviceProvenFinalRenderAuthorityWithoutGlassPipeline() throws Exception {
        Path hook = Path.of(
                "src/main/java/com/hellovoid/liquidui/hook/systemui/notification/NotificationRedBackgroundHook.java");
        Path policy = Path.of(
                "src/main/java/com/hellovoid/liquidui/hook/systemui/notification/NotificationRedBackgroundPolicy.java");
        assertTrue(Files.exists(hook));
        assertTrue(Files.exists(policy));
        String source = Files.readString(hook);
        String policySource = Files.readString(policy);

        assertTrue(source.contains("NotificationBackgroundView"));
        assertTrue(source.contains("onDraw"));
        assertTrue(source.contains("mBackground"));
        assertTrue(source.contains("setMiViewBlurModeCompat"));
        assertTrue(source.contains("clearMiBackgroundBlendColorCompat"));
        assertTrue(source.contains("NotificationRedBackgroundPolicy.OPAQUE_RED"));
        assertTrue(policySource.contains("0xFFFF0000"));

        assertFalse(source.contains("TextureView"));
        assertFalse(source.contains("EGL"));
        assertFalse(source.contains("SetPassBlurSurface"));
        assertFalse(source.contains("NotificationGlassSession"));
    }
}
