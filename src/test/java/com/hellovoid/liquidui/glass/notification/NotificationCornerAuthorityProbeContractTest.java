package com.hellovoid.liquidui.glass.notification;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class NotificationCornerAuthorityProbeContractTest {
    @Test
    public void cornerProbeCapturesNativeAndPrismalAuthoritiesWithoutMutation() throws Exception {
        Path probePath = Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationCornerAuthorityProbe.java");
        assertTrue(Files.exists(probePath));
        String probe = Files.readString(probePath);
        String collector = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassNodeCollector.java"));
        String session = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassSession.java"));
        String renderer = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationPassBlurTextureView.java"));

        assertTrue(probe.contains("[NotifGlass][CornerProbe]"));
        assertTrue(probe.contains("observeNode"));
        assertTrue(probe.contains("observeMapping"));
        assertTrue(probe.contains("MAX_PARENT_DEPTH"));

        assertTrue(probe.contains("actualWidth"));
        assertTrue(probe.contains("actualHeight"));
        assertTrue(probe.contains("clipTop"));
        assertTrue(probe.contains("clipBottom"));
        assertTrue(probe.contains("expand="));
        assertTrue(probe.contains("topRadius"));
        assertTrue(probe.contains("bottomRadius"));
        assertTrue(probe.contains("clipChildren"));
        assertTrue(probe.contains("clipToPadding"));
        assertTrue(probe.contains("clipToOutline"));
        assertTrue(probe.contains("outlineProvider"));
        assertTrue(probe.contains("node=["));
        assertTrue(probe.contains("radii=["));

        assertTrue(probe.contains("backdropRect=["));
        assertTrue(probe.contains("validDockRect=["));
        assertTrue(probe.contains("overscanInsets=["));
        assertTrue(probe.contains("coverage="));

        assertTrue(collector.contains("NotificationCornerAuthorityProbe.observeNode"));
        assertTrue(session.contains("NotificationCornerAuthorityProbe.observeMapping(renderer)"));
        assertFalse(renderer.contains("NotificationCornerAuthorityProbe.observeMapping"));

        assertFalse(probe.contains("setClipChildren"));
        assertFalse(probe.contains("setClipToPadding"));
        assertFalse(probe.contains("setClipToOutline"));
        assertFalse(probe.contains("setOutlineProvider"));
        assertFalse(probe.contains("setCornerRadius"));
        assertFalse(probe.contains("setLayoutParams"));
        assertFalse(probe.contains("requestLayout"));
        assertFalse(probe.contains("invalidate("));
    }

    @Test
    public void cornerProbeIsBoundedAndDoesNotChangeRendererMappingMath() throws Exception {
        String probe = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationCornerAuthorityProbe.java"));
        String session = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassSession.java"));
        String renderer = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationPassBlurTextureView.java"));

        assertTrue(probe.contains("WeakHashMap"));
        assertTrue(probe.contains("MAX_PARENT_DEPTH = 8"));
        assertTrue(renderer.contains("Miuix307BackdropMapping.compute"));
        assertTrue(session.contains("NotificationCornerAuthorityProbe.observeMapping(renderer)"));
        assertFalse(renderer.contains("CORNER_PROBE_RADIUS_OVERRIDE"));
        assertFalse(renderer.contains("CORNER_PROBE_OVERSCAN_OVERRIDE"));
    }
}
