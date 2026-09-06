package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class NotificationSharedGlassArchitectureTest {
    private static String read(String path) throws Exception { return Files.readString(Path.of(path)); }

    @Test
    public void previousPrismalProducerRemainsAvailableButDormant() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        String renderer = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationPassBlurTextureView.java");
        String bridge = read("src/main/java/com/hellovoid/liquidui/glass/notification/SystemUiPassBlurBridge.java");

        assertTrue(renderer.contains("prismalRenderer.prepareBackdrop"));
        assertTrue(bridge.contains("setPassBlurSurface"));
        assertFalse(hook.contains("NotificationPassBlurTextureView"));
        assertFalse(hook.contains("SystemUiPassBlurBridge"));
        assertFalse(hook.contains("NotificationGlassSession"));
    }

    @Test
    public void activeMaterialAuthorityDoesNotBranchOnNotificationStyle() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        assertTrue(hook.contains("ExpandableNotificationRow"));
        assertTrue(hook.contains("NotificationUtil"));
        assertTrue(hook.contains("MiBlurCompat"));
        assertFalse(hook.contains("showMiuiStyle"));
        assertFalse(hook.contains("notifStyle"));
        assertFalse(hook.contains("Google"));
    }

    @Test
    public void activePathIsCpuCaptureFreeAndShadeEndpointFree() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        assertFalse(hook.contains("PixelCopy"));
        assertFalse(hook.contains("ImageReader"));
        assertFalse(hook.contains("MediaProjection"));
        assertFalse(hook.contains("ScreenCapture"));
        assertFalse(hook.contains("NotificationShade"));
        assertFalse(hook.contains("SurfaceControl"));
    }

    @Test
    public void topBottomRadiusAuthorityRemainsOnRowRoundness() throws Exception {
        String collector = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassNodeCollector.java");
        String registry = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationMaterialTargetRegistry.java");

        // Target SystemUI's setRoundRect(View, boolean, boolean) flags control outline geometry
        // and flip-radius selection; they are not top/bottom rounded flags. Per-edge magnitudes
        // remain getTopCornerRadius/getBottomCornerRadius -> NotificationBackgroundView#setRadius.
        assertTrue(collector.contains("topCornerRadius.invoke(rowObject)"));
        assertTrue(collector.contains("bottomCornerRadius.invoke(rowObject)"));
        assertFalse(collector.contains("roundState.topRounded()"));
        assertFalse(collector.contains("roundState.bottomRounded()"));
        assertFalse(registry.contains("topRounded"));
        assertFalse(registry.contains("bottomRounded"));
        assertTrue(registry.contains("OutlineState"));
        assertTrue(registry.contains("useActualHeightGeometry"));
        assertTrue(registry.contains("useFlipRadius"));
        assertTrue(registry.contains("observeRoundRect"));
    }
}
