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
        assertTrue(hook.contains("ExpandableNotificationRowInjector"));
        assertTrue(hook.contains("ExpandableNotificationRow"));
        assertTrue(hook.contains("NotificationUtil"));
        assertFalse(hook.contains("MI_BLUR_COMPAT"));
        assertFalse(hook.contains("showMiuiStyle"));
        assertFalse(hook.contains("notifStyle"));
        assertFalse(hook.contains("Google"));
    }

    @Test
    public void activeHookUsesGpuStreamProbeWithoutCpuCaptureApis() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        assertTrue(hook.contains("NotificationGpuPassBlurStreamProbe"));
        assertTrue(hook.contains("streamProbe.observe(target)"));
        assertFalse(hook.contains("PixelCopy"));
        assertFalse(hook.contains("ImageReader"));
        assertFalse(hook.contains("MediaProjection"));
        assertFalse(hook.contains("ScreenCapture"));
        assertFalse(hook.contains("SurfaceControl.capture"));
        assertFalse(hook.contains("SetPassBlurSurface"));
        assertTrue(hook.contains("NotificationShadeWindowView"));
        assertTrue(hook.contains("NotificationShadeBlurPolicy"));
    }

    @Test
    public void gpuPassBlurProbeUsesOfficialLongLivedQuarterScaleSurfaceTexture() throws Exception {
        String bridge = read("src/main/java/com/hellovoid/liquidui/glass/notification/SystemUiPassBlurBridge.java");
        String probe = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGpuPassBlurStreamProbe.java");

        assertTrue(bridge.contains("SCALE = 0.25f"));
        assertFalse(bridge.contains("SCALE = 1.0f"));
        assertTrue(bridge.contains("setUpdateTextureFlag"));
        assertTrue(bridge.contains("SetPassBlurSurface"));

        assertTrue(probe.contains("new SurfaceTexture"));
        assertTrue(probe.contains("setDefaultBufferSize"));
        assertTrue(probe.contains("setOnFrameAvailableListener"));
        assertTrue(probe.contains("updateTexImage"));
        assertTrue(probe.contains("SystemUiPassBlurBridge.bind"));
        assertTrue(probe.contains("SystemUiPassBlurBridge.resumeUpdates"));
        assertTrue(probe.contains("gpu stream frame"));
        assertTrue(probe.contains("EGL14"));
        assertTrue(probe.contains("GLES11Ext.GL_TEXTURE_EXTERNAL_OES"));

        assertFalse(probe.contains("Bitmap"));
        assertFalse(probe.contains("PixelCopy"));
        assertFalse(probe.contains("ImageReader"));
        assertFalse(probe.contains("MediaProjection"));
        assertFalse(probe.contains("ScreenCapture"));
        assertFalse(probe.contains("SurfaceControl.capture"));
    }

    @Test
    public void gpuProbeDoesNotRecycleProducerOnOrdinaryObserve() throws Exception {
        String probe = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGpuPassBlurStreamProbe.java");
        assertTrue(probe.contains("if (binding != null && binding.bound && binding.hostRootSurface.isValid())"));
        assertTrue(probe.contains("SystemUiPassBlurBridge.resumeUpdates(binding)"));
        assertFalse(probe.contains("retireVendorPassBlurAuthority"));
        assertFalse(probe.contains("ZeroCopyProducerRecoveryState"));
    }

    @Test
    public void topBottomRadiusAuthorityRemainsOnRowRoundness() throws Exception {
        String collector = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassNodeCollector.java");
        String registry = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationMaterialTargetRegistry.java");

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
