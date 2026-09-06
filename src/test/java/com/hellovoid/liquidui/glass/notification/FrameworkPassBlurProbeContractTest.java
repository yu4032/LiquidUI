package com.hellovoid.liquidui.glass.notification;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class FrameworkPassBlurProbeContractTest {
    @Test
    public void probeIsReadOnlyAndSearchesFrameworkConsumerObjects() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/FrameworkPassBlurProbe.java"));
        assertTrue(source.contains("inspectOnce"));
        assertTrue(source.contains("getViewRootImpl"));
        assertTrue(source.contains("mThreadedRenderer"));
        assertTrue(source.contains("addTextureView"));
        assertTrue(source.contains("SurfaceTexture"));
        assertTrue(source.contains("TextureView"));
        assertTrue(source.contains("[NotifGlass][FrameworkPB]"));
        assertFalse(source.contains("SetPassBlurSurface"));
        assertFalse(source.contains("setUpdateTextureFlag"));
        assertFalse(source.contains("SurfaceControl.Transaction"));
        assertFalse(source.contains("addTextureView.invoke"));
    }

    @Test
    public void probeHookOnlyObservesVendorNotificationPanelPassBlurTrue() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/FrameworkPassBlurProbeHook.java"));
        assertTrue(source.contains("NotificationPanelView"));
        assertTrue(source.contains("setPassWindowBlurEnabled"));
        assertTrue(source.contains("!requested"));
        assertTrue(source.contains("FrameworkPassBlurProbe.inspectOnce"));
        assertTrue(source.contains("BeforeMethodHookBackend"));
        assertFalse(source.contains("ArgumentRewriteHookBackend"));
    }

    @Test
    public void diagnosticProbeDoesNotReplaceWorkingRtdaGlassSource() throws Exception {
        String glassHook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java"));
        String moduleMain = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/ModuleMain.java"));
        assertTrue(glassHook.contains("RootTaskDisplayAreaOrganizer"));
        assertTrue(glassHook.contains("sourceState.observe"));
        assertFalse(glassHook.contains("FrameworkPassBlurProbe"));
        assertTrue(moduleMain.contains("FrameworkPassBlurProbeHook"));
    }
}
