package com.hellovoid.liquidui.architecture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public final class SystemUiGlassHostControllerArchitectureTest {
    @Test
    public void controllerRoutesHostsThroughSharedWindowSession() throws Exception {
        Path controllerPath = Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/systemui/SystemUiGlassHostController.java");
        Path hostPath = Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/systemui/SystemUiGlassWindowHost.java");
        assertTrue(Files.exists(controllerPath));
        assertTrue(Files.exists(hostPath));

        String controller = Files.readString(controllerPath);
        String host = Files.readString(hostPath);
        String joined = controller + host;

        assertTrue(controller.contains("SystemUiGlassHostState"));
        assertTrue(controller.contains("bindAdapter("));
        assertTrue(controller.contains("new GlassNode("));
        assertTrue(host.contains("sessionFor("));
        assertTrue(host.contains("sceneHost()"));
        assertFalse(host.contains("attachRenderer("));
        assertTrue(controller.contains("getLocationInWindow"));

        assertFalse(joined.contains("new WindowGlassRenderer"));
        assertFalse(joined.contains("new HandlerThread"));
        assertFalse(joined.contains("SetPassBlurSurface"));
        assertFalse(joined.contains("SurfaceTexture"));
        assertFalse(joined.contains("EGL14"));
        assertFalse(joined.contains("glReadPixels"));
        assertFalse(joined.contains("PixelCopy"));
        assertFalse(joined.contains("ScreenCapture"));
    }

    @Test
    public void controllerUsesExactAuthorizationBeforeNativeSuppression() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/systemui/SystemUiGlassHostController.java"));
        assertTrue(controller.contains("state.authorize("));
        assertTrue(controller.contains("material.suppress("));
        assertTrue(controller.contains("state.revoke("));
        assertTrue(controller.contains("material.restore("));
        assertTrue(controller.contains("onTerminalFailure"));
        assertTrue(controller.contains("failTerminal()"));
    }
}
