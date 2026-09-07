package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SystemUiGlassCoreArchitectureTest {
    @Test
    public void processCoreOwnsTheOnlyWindowRegistryAndRenderThread() throws Exception {
        String module = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/ModuleMain.java"));
        String core = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/core/SystemUiGlassCore.java"));
        String bridge = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/core/SystemUiPassBlurBridge.java"));
        String coreTree = readTree(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/core"));
        String notification = readTree(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification"));
        String production = readTree(Path.of(
                "src/main/java/com/hellovoid/liquidui"));

        assertEquals(1L, count(module, "new SystemUiGlassCore("));
        assertTrue(core.contains("new WindowGlassRegistry"));
        assertTrue(core.contains("sessionFor(Object root, int displayId)"));
        assertTrue(core.contains("public WindowGlassSession sessionFor(View "));
        assertTrue(core.contains("new HandlerThread("));
        assertTrue(core.contains("new Handler(renderThread.getLooper())"));
        assertEquals(1L, count(coreTree, "new HandlerThread("));

        assertFalse(notification.contains("new SystemUiGlassCore"));
        assertFalse(notification.contains("new WindowGlassRegistry"));
        assertFalse(notification.contains("postOnAnimation"));
        assertFalse(notification.contains("EGL14."));
        assertFalse(notification.contains("SetPassBlurSurface"));
        assertFalse(notification.contains("GL_TEXTURE_EXTERNAL_OES"));

        assertFalse(production.contains("glReadPixels"));
        assertFalse(production.contains("PixelCopy"));
        assertTrue(bridge.contains("SCALE = 1.0f"));
    }

    private static long count(String source, String needle) {
        long count = 0L;
        int from = 0;
        while ((from = source.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }

    private static String readTree(Path root) throws Exception {
        StringBuilder joined = new StringBuilder();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                joined.append(Files.readString(file));
            }
        }
        return joined.toString();
    }
}
