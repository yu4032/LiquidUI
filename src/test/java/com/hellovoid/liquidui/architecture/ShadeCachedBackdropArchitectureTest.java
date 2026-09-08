package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/** Regression contract: Shade background blur is cached in LiquidUI, not animated by vendor blur. */
public final class ShadeCachedBackdropArchitectureTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    public void shadeAuthorityEnablesCachedBackdropFillOnSharedRenderer() throws Exception {
        String authority = read(
                "src/main/java/com/hellovoid/liquidui/glass/notification/ShadeWindowGlassAuthority.java");
        String session = read(
                "src/main/java/com/hellovoid/liquidui/glass/core/WindowGlassSession.java");
        String renderer = read(
                "src/main/java/com/hellovoid/liquidui/glass/core/WindowGlassRenderer.java");

        assertTrue(authority.contains("session.setBackdropFillEnabled(true"));
        assertTrue(session.contains("setBackdropFillEnabled(boolean enabled"));
        assertTrue(renderer.contains("renderBackdropFillPass("));
        assertTrue(renderer.contains("prismalRenderer.blurTextureV"));
    }

    @Test
    public void shadeHookForcesRootNativeBlurModesOff() throws Exception {
        String hook = read(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationSharedGlassHook.java");

        assertTrue(hook.contains("shadeWindowClass.isInstance(thisObject)"));
        assertTrue(hook.contains("args[0] = Boolean.FALSE"));
        assertTrue(hook.contains("args[0] = Integer.valueOf(0)"));
    }
}
