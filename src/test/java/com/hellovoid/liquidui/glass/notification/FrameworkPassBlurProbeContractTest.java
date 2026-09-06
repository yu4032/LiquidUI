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

    @Test
    public void transactionProbeObservesExactShadePassBlurOwnershipWithoutMutation() throws Exception {
        Path probePath = Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/FrameworkPassBlurTransactionProbe.java");
        assertTrue(Files.exists(probePath));
        String probe = Files.readString(probePath);
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/FrameworkPassBlurProbeHook.java"));
        String graph = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/FrameworkPassBlurProbe.java"));

        assertTrue(hook.contains("SetPassBlurSurface"));
        assertTrue(hook.contains("setUpdateTextureFlag"));
        assertTrue(hook.contains("FrameworkPassBlurTransactionProbe::observeSetPassBlurSurface"));
        assertTrue(hook.contains("FrameworkPassBlurTransactionProbe::observeSetUpdateTextureFlag"));
        assertTrue(graph.contains("FrameworkPassBlurTransactionProbe.registerShadeRoot"));
        assertTrue(probe.contains("[NotifGlass][FrameworkPB][TX]"));
        assertTrue(probe.contains("sequence"));
        assertTrue(probe.contains("Thread.currentThread"));
        assertTrue(probe.contains("getStackTrace"));

        assertFalse(hook.contains("ArgumentRewriteHookBackend"));
        assertFalse(probe.contains("SetPassBlurSurface.invoke"));
        assertFalse(probe.contains("setUpdateTextureFlag.invoke"));
        assertFalse(probe.contains("new SurfaceControl.Transaction"));
        assertFalse(probe.contains(".apply()"));
        assertFalse(probe.contains(".release()"));
        assertFalse(probe.contains("addTextureView.invoke"));
    }

    @Test
    public void frameworkProbeReinspectsAfterShadeAttachAndMatchingTransactions() throws Exception {
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/FrameworkPassBlurProbeHook.java"));
        String graph = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/FrameworkPassBlurProbe.java"));
        String transaction = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/FrameworkPassBlurTransactionProbe.java"));

        assertTrue(hook.contains("onAttachedToWindow"));
        assertTrue(hook.contains("FrameworkPassBlurProbe.inspectIfGenerationChanged"));
        assertTrue(graph.contains("inspectIfGenerationChanged"));
        assertTrue(graph.contains("probeGeneration"));
        assertTrue(graph.contains("inspectionFingerprint"));
        assertTrue(graph.contains("removeTextureView"));
        assertTrue(transaction.contains("FrameworkPassBlurProbe.onMatchingShadeTransaction"));
        assertFalse(graph.contains("addTextureView.invoke"));
        assertFalse(graph.contains("removeTextureView.invoke"));
    }
}
