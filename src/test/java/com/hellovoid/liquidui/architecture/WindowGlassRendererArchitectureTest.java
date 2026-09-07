package com.hellovoid.liquidui.architecture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public final class WindowGlassRendererArchitectureTest {
    private static final Path RENDERER = Path.of(
            "src/main/java/com/hellovoid/liquidui/glass/core/WindowGlassRenderer.java");
    private static final Path HOST = Path.of(
            "src/main/java/com/hellovoid/liquidui/glass/core/GlassHostView.java");

    @Test
    public void windowRendererOwnsGpuPipelineButNoPrivateThread() throws Exception {
        String renderer = Files.readString(RENDERER);
        String host = Files.readString(HOST);

        assertTrue(renderer.contains("EGL14."));
        assertTrue(renderer.contains("GL_TEXTURE_EXTERNAL_OES"));
        assertTrue(renderer.contains("new SurfaceTexture("));
        assertTrue(renderer.contains("new PrismalRenderer("));
        assertTrue(renderer.contains("SystemUiPassBlurBridge"));
        assertTrue(renderer.contains("PassBlurShaders"));
        assertTrue(renderer.contains("DisplayTransformEngine.compose"));
        assertFalse(renderer.contains("new HandlerThread("));
        assertFalse(host.contains("EGL14."));
        assertFalse(host.contains("new SurfaceTexture("));
        assertFalse(renderer.contains("glReadPixels"));
        assertFalse(renderer.contains("PixelCopy"));
    }

    @Test
    public void rendererConsumesSharedFrameCoordinatorAndEmitsNodeToken() throws Exception {
        String renderer = Files.readString(RENDERER);

        assertTrue(renderer.contains("frameCoordinator.requestSourceFrame()"));
        assertTrue(renderer.contains("frameCoordinator.requestScene()"));
        assertTrue(renderer.contains("new GlassActivationToken("));
        assertTrue(renderer.contains("node.lifecycleGeneration()"));
        assertTrue(renderer.contains("presentationState.accept("));
        assertFalse(renderer.contains("AtomicBoolean drawRequested"));
    }
}
