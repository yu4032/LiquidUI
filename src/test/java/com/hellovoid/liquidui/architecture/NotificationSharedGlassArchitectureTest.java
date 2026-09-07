package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class NotificationSharedGlassArchitectureTest {
    private static String read(String path) throws Exception { return Files.readString(Path.of(path)); }

    @Test
    public void activeHookOwnsOneSharedNsslRuntimeAndNoStandaloneProbe() throws Exception {
        String module = read("src/main/java/com/hellovoid/liquidui/ModuleMain.java");
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationSharedGlassHook.java");
        String runtime = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassRuntime.java");
        String session = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassSession.java");

        assertTrue(module.contains("new NotificationSharedGlassHook"));
        assertFalse(module.contains("new NotificationLiquidGlassHook"));
        assertTrue(hook.contains("NotificationGlassRuntime"));
        assertFalse(hook.contains("NotificationGpuPassBlurStreamProbe"));
        assertFalse(hook.contains("streamProbe.observe"));
        assertTrue(runtime.contains("WeakHashMap<View, NotificationGlassSession> sessions"));
        assertTrue(session.contains("new NotificationPassBlurTextureView"));
        assertTrue(session.contains("NotificationGlassSceneState"));
    }

    @Test
    public void exactMaterialAuthorityKeepsTwoDpFallbackBelowSharedGlass() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationSharedGlassHook.java");
        String fallback = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationVendorMaterialController.java");
        String handoff = read("src/main/java/com/hellovoid/liquidui/glass/notification/LegacyNotificationVendorMaterialController.java");

        assertTrue(hook.contains("ExpandableNotificationRowInjector"));
        assertTrue(hook.contains("getDeclaredMethod(\"updateBackground$1\")"));
        assertTrue(hook.contains("injectorClass.getField(\"view\")"));
        assertTrue(hook.contains("rowClass.getField(\"mBackgroundNormal\")"));
        assertTrue(hook.contains("fallbackController.applySharedGlassFallbackMaterial(target)"));
        assertTrue(fallback.contains("CARD_PASS_BLUR_RADIUS_DP = 2.0f"));
        assertTrue(fallback.contains("applySharedGlassFallbackMaterial"));
        assertFalse(fallback.substring(
                fallback.indexOf("void applySharedGlassFallbackMaterial"),
                fallback.indexOf("void applyHyperLightElementMaterial"))
                .contains("scheduleAgslRefractionProbe"));
        assertFalse(fallback.substring(
                fallback.indexOf("void applySharedGlassFallbackMaterial"),
                fallback.indexOf("void applyHyperLightElementMaterial"))
                .contains("enableGpuBackdropContainer"));
        assertTrue(handoff.contains("background.setAlpha(0f)"));
        assertFalse(handoff.substring(
                handoff.indexOf("void suppressRow"), handoff.indexOf("void restoreRow"))
                .contains("disableBlur.invoke"));
        assertFalse(handoff.substring(
                handoff.indexOf("void suppressRow"), handoff.indexOf("void restoreRow"))
                .contains("clearBlend.invoke"));
    }

    @Test
    public void sharedSessionRevokesBeforeRootRolloverRebind() throws Exception {
        String observer = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationViewRootSurfaceObserver.java");
        String session = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassSession.java");
        String bridge = read("src/main/java/com/hellovoid/liquidui/glass/notification/SystemUiPassBlurBridge.java");

        assertTrue(observer.contains("SurfaceChangedCallback"));
        assertTrue(observer.contains("addSurfaceChangedCallback"));
        assertTrue(observer.contains("surfaceDestroyed"));
        assertTrue(observer.contains("surfaceCreated"));
        assertTrue(observer.contains("surfaceReplaced"));
        assertTrue(observer.contains("host.post(() -> listener.onSurfaceReady(name))"));
        assertFalse(observer.contains("SetPassBlurSurface"));
        assertFalse(observer.contains("SurfaceControl.Transaction transaction"));
        assertTrue(session.contains("revokeSharedPresentation(\"root-surface-destroyed\")"));
        assertTrue(session.contains("renderer.rebindProducer(\"root-\" + event)"));
        assertTrue(session.contains("renderer.setAlpha(0f)"));
        assertTrue(session.contains("materialController.restoreAll()"));
        assertFalse(bridge.contains("bindInTransaction"));
    }

    @Test
    public void sharedRendererUsesQuarterScaleZeroCopyPassBlurAndPrismal() throws Exception {
        String bridge = read("src/main/java/com/hellovoid/liquidui/glass/notification/SystemUiPassBlurBridge.java");
        String renderer = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationPassBlurTextureView.java");
        String compositor = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassCompositor.java");

        assertTrue(bridge.contains("SCALE = 0.25f"));
        assertTrue(bridge.contains("setUpdateTextureFlag"));
        assertTrue(bridge.contains("SetPassBlurSurface"));
        assertTrue(renderer.contains("GL_TEXTURE_EXTERNAL_OES"));
        assertTrue(renderer.contains("updateTexImage"));
        assertTrue(renderer.contains("prismalRenderer.prepareBackdrop"));
        assertTrue(renderer.contains("eglSwapBuffers"));
        assertTrue(compositor.contains("renderer.drawGlass"));

        assertFalse(renderer.contains("PixelCopy"));
        assertFalse(renderer.contains("ImageReader"));
        assertFalse(renderer.contains("MediaProjection"));
        assertFalse(renderer.contains("ScreenCapture"));
        assertFalse(renderer.contains("glReadPixels"));
    }

    @Test
    public void validatedProbeDocumentsFreshProducerAfterPassBlurRootDestruct() throws Exception {
        String probe = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGpuPassBlurStreamProbe.java");
        assertTrue(probe.contains("retireGpuProducerForRootRollover"));
        assertTrue(probe.contains("recreateGpuProducerForRootRollover"));
        assertTrue(probe.contains("producerPreserved=false"));
        assertTrue(probe.contains("producerSurface = null"));
        assertTrue(probe.contains("surfaceTexture = null"));
        assertFalse(probe.contains("producerPreserved=true"));
        assertFalse(probe.contains("postDelayed"));
        assertFalse(probe.contains("Thread.sleep"));
    }

    @Test
    public void failedBindDoesNotAdvanceEndpointGeneration() throws Exception {
        String probe = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGpuPassBlurStreamProbe.java");
        assertTrue(probe.contains("long nextGeneration = endpointGeneration + 1"));
        assertTrue(probe.contains("endpointGeneration = next.endpointGeneration"));
        assertFalse(probe.contains("++endpointGeneration"));
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
