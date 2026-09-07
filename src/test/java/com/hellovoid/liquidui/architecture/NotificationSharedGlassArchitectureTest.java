package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class NotificationSharedGlassArchitectureTest {
    private static String read(String path) throws Exception { return Files.readString(Path.of(path)); }

    @Test
    public void activeHookDelegatesToInjectedWindowCoreAndNoStandaloneProbe() throws Exception {
        String module = read("src/main/java/com/hellovoid/liquidui/ModuleMain.java");
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationSharedGlassHook.java");
        String runtime = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassRuntime.java");
        String adapter = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassAdapter.java");

        assertTrue(module.contains("new SystemUiGlassCore("));
        assertTrue(module.contains("new NotificationSharedGlassHook("));
        assertTrue(hook.contains("SystemUiGlassCore glassCore"));
        assertTrue(hook.contains("NotificationGlassRuntime"));
        assertFalse(hook.contains("NotificationGpuPassBlurStreamProbe"));
        assertTrue(runtime.contains("SystemUiGlassCore glassCore"));
        assertTrue(runtime.contains("new NotificationGlassAdapter("));
        assertFalse(runtime.contains("NotificationGlassSession"));
        assertTrue(adapter.contains("core.sessionFor(stack)"));
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
        assertTrue(handoff.contains("background.setAlpha(0f)"));
        assertFalse(handoff.substring(
                handoff.indexOf("void suppressRow"), handoff.indexOf("void restoreRow"))
                .contains("disableBlur.invoke"));
        assertFalse(handoff.substring(
                handoff.indexOf("void suppressRow"), handoff.indexOf("void restoreRow"))
                .contains("clearBlend.invoke"));
    }

    @Test
    public void windowCoreRevokesBeforeRootRolloverRecovery() throws Exception {
        String observer = read("src/main/java/com/hellovoid/liquidui/glass/core/ViewRootSurfaceObserver.java");
        String renderer = read("src/main/java/com/hellovoid/liquidui/glass/core/WindowGlassRenderer.java");
        String bridge = read("src/main/java/com/hellovoid/liquidui/glass/core/SystemUiPassBlurBridge.java");

        assertTrue(observer.contains("addSurfaceChangedCallback"));
        assertTrue(observer.contains("surfaceDestroyed"));
        assertTrue(observer.contains("surfaceCreated"));
        assertTrue(observer.contains("surfaceReplaced"));
        assertTrue(observer.contains("host.post(() -> listener.onSurfaceReady(name))"));
        assertFalse(observer.contains("SystemUiPassBlurBridge.bind("));
        assertTrue(renderer.contains("presentationState.sourceLost(producerGeneration)"));
        assertTrue(renderer.contains("producerRecovery.onRootLost(producerGeneration)"));
        assertTrue(renderer.indexOf("presentationState.sourceLost(producerGeneration)")
                < renderer.indexOf("recreateInputProducer(\"root-destroyed\")"));
        assertFalse(bridge.contains("bindInTransaction"));
    }

    @Test
    public void activationCarriesExactRendererOwnedNodeLifecycle() throws Exception {
        String renderer = read("src/main/java/com/hellovoid/liquidui/glass/core/WindowGlassRenderer.java");
        String presentation = read("src/main/java/com/hellovoid/liquidui/glass/core/WindowPresentationState.java");

        assertTrue(renderer.contains("new GlassActivationToken("));
        assertTrue(renderer.contains("currentNodes"));
        assertTrue(renderer.contains("node.lifecycleGeneration()"));
        assertTrue(renderer.contains("presentationState.accept(token)"));
        assertTrue(presentation.contains("current.longValue() != rendered.getValue()"));
        assertTrue(presentation.contains("token.profileVersion() != profileVersion"));
        assertTrue(presentation.contains("token.producerGeneration() != producerGeneration"));
    }

    @Test
    public void producerPauseResumeRequiresANewFreshFrameBeforeReactivation() throws Exception {
        String renderer = read("src/main/java/com/hellovoid/liquidui/glass/core/WindowGlassRenderer.java");
        String recovery = read("src/main/java/com/hellovoid/liquidui/glass/core/ProducerRecoveryState.java");

        int start = renderer.indexOf("void setProducerUpdatesEnabled");
        int end = renderer.indexOf("void setVendorPassBlurEnabled");
        assertTrue(start >= 0 && end > start);
        String gate = renderer.substring(start, end);
        assertTrue(gate.contains("producerRecovery.onGeometryInvalidated()"));
        assertTrue(gate.contains("frameAvailable.set(false)"));
        assertTrue(gate.contains("presentationState.sourceBound("));
        assertTrue(recovery.contains("freshGeneration = 0L"));
        assertTrue(recovery.contains("isReady"));
    }

    @Test
    public void sceneAndSourceRequestsCoalesceOnOneSharedFrameCoordinator() throws Exception {
        String renderer = read("src/main/java/com/hellovoid/liquidui/glass/core/WindowGlassRenderer.java");
        String coordinator = read("src/main/java/com/hellovoid/liquidui/glass/core/FrameCoordinator.java");
        String notification = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassAdapter.java");

        assertTrue(renderer.contains("frameCoordinator.requestScene()"));
        assertTrue(renderer.contains("frameCoordinator.requestSourceFrame()"));
        assertFalse(renderer.contains("AtomicBoolean drawScheduled"));
        assertTrue(coordinator.contains("sourcePending"));
        assertTrue(coordinator.contains("scenePending"));
        assertFalse(notification.contains("postOnAnimation"));
    }

    @Test
    public void windowRendererUsesFullScaleZeroCopyPassBlurAndPrismal() throws Exception {
        String bridge = read("src/main/java/com/hellovoid/liquidui/glass/core/SystemUiPassBlurBridge.java");
        String renderer = read("src/main/java/com/hellovoid/liquidui/glass/core/WindowGlassRenderer.java");
        String compositor = read("src/main/java/com/hellovoid/liquidui/glass/core/GlassCompositor.java");

        assertTrue(bridge.contains("SCALE = 1.0f"));
        assertFalse(bridge.contains("SCALE = 0.25f"));
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
        assertFalse(renderer.contains("glReadPixels"));
    }

    @Test
    public void sharedGlassRadiusUsesNativeCardResourceNotTransientRowRoundness() throws Exception {
        String collector = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassNodeCollector.java");
        String registry = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationMaterialTargetRegistry.java");

        assertTrue(collector.contains("notification_item_bg_radius"));
        assertTrue(collector.contains("nativeCardRadiusPx(background)"));
        assertFalse(collector.contains("topCornerRadius.invoke(rowObject)"));
        assertFalse(collector.contains("bottomCornerRadius.invoke(rowObject)"));
        assertTrue(collector.contains("GlassMaterialProfile.CARD"));
        assertTrue(registry.contains("OutlineState"));
        assertTrue(registry.contains("useActualHeightGeometry"));
        assertTrue(registry.contains("useFlipRadius"));
        assertTrue(registry.contains("observeRoundRect"));
    }
}
