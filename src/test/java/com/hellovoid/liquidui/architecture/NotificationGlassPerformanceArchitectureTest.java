package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class NotificationGlassPerformanceArchitectureTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    public void notificationProfileSkipsRedundantPrismalGaussian() throws Exception {
        String material = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassMaterial.java");
        String prismal = read("prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java");

        assertTrue(material.contains("b.blurRadiusPx = 0f"));
        assertTrue(prismal.contains("useBlurredBackdrop"));
        assertTrue(prismal.contains("params.blurRadiusPx > 0.01f"));
        assertTrue(prismal.contains("if (this.useBlurredBackdrop)"));
        assertTrue(prismal.contains("uniform1i(\"u_useBlurredTexture\", this.useBlurredBackdrop ? 1 : 0)"));
    }

    @Test
    public void sceneAndProducerFramesCoalesceOntoOneRenderRequest() throws Exception {
        String renderer = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationPassBlurTextureView.java");

        assertTrue(renderer.contains("AtomicBoolean renderPending"));
        assertTrue(renderer.contains("void requestRender(boolean fromFrameCallback)"));
        assertTrue(renderer.contains("if (!renderPending.compareAndSet(false, true)) return"));
        assertTrue(renderer.contains("requestRender(true)"));
        assertTrue(renderer.contains("requestRender(false)"));
        assertFalse(renderer.contains("drawLatestFrame(true)"));
        assertFalse(renderer.contains("renderHandler.post(() -> drawLatestFrame(false))"));
    }

    @Test
    public void rendererPowerDiagnosticsReportCoalescing() throws Exception {
        String renderer = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationPassBlurTextureView.java");

        assertTrue(renderer.contains("coalescedRenderRequestCount"));
        assertTrue(renderer.contains("renderRequestCount"));
        assertTrue(renderer.contains("coalescedRequests="));
    }
}
