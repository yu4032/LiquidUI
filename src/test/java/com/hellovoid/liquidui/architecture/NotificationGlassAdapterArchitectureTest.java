package com.hellovoid.liquidui.architecture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public final class NotificationGlassAdapterArchitectureTest {
    private static final Path NOTIFICATION = Path.of(
            "src/main/java/com/hellovoid/liquidui/glass/notification");

    @Test
    public void notificationPackageOwnsNoGpuPipeline() throws Exception {
        String sources = readTree(NOTIFICATION);
        assertFalse(sources.contains("EGL14."));
        assertFalse(sources.contains("GL_TEXTURE_EXTERNAL_OES"));
        assertFalse(sources.contains("new SurfaceTexture("));
        assertFalse(sources.contains("new PrismalRenderer("));
        assertFalse(sources.contains("SetPassBlurSurface"));
        assertFalse(sources.contains("glReadPixels"));
        assertFalse(sources.contains("PixelCopy"));
    }

    @Test
    public void moduleOwnsExactlyOneCoreAndInjectsItIntoNotificationHook() throws Exception {
        String module = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/ModuleMain.java"));
        String hook = Files.readString(NOTIFICATION.resolve("NotificationSharedGlassHook.java"));
        String runtime = Files.readString(NOTIFICATION.resolve("NotificationGlassRuntime.java"));

        assertEquals(1L, count(module, "new SystemUiGlassCore("));
        assertTrue(module.contains("new NotificationSharedGlassHook("));
        assertTrue(module.contains("glassCore"));
        assertTrue(hook.contains("SystemUiGlassCore glassCore"));
        assertFalse(hook.contains("new SystemUiGlassCore("));
        assertTrue(runtime.contains("SystemUiGlassCore glassCore"));
        assertTrue(runtime.contains("new NotificationGlassAdapter("));
        assertFalse(runtime.contains("NotificationGlassSession"));
    }

    @Test
    public void collectorPublishesGenericCardNodesWithExactLifecycleIdentity() throws Exception {
        String collector = Files.readString(NOTIFICATION.resolve(
                "NotificationGlassNodeCollector.java"));
        String adapter = Files.readString(NOTIFICATION.resolve(
                "NotificationGlassAdapter.java"));

        assertTrue(collector.contains("GlassNode collect("));
        assertTrue(collector.contains("GlassMaterialProfile.CARD"));
        assertTrue(collector.contains("lifecycleGeneration"));
        assertTrue(collector.contains("zOrder"));
        assertTrue(adapter.contains("core.sessionFor(stack)"));
        assertTrue(adapter.contains("lifecycleGeneration"));
        assertTrue(adapter.contains("authorizedNodeIds"));
        assertTrue(adapter.contains("revokedNodeIds"));
        assertFalse(adapter.contains("postOnAnimation"));
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

    private static long count(String source, String needle) {
        long count = 0L;
        int from = 0;
        while ((from = source.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }
}
