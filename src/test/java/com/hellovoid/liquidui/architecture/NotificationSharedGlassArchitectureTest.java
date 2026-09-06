package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class NotificationSharedGlassArchitectureTest {
    private static String read(String path) throws Exception { return Files.readString(Path.of(path)); }

    @Test
    public void oneShadeSessionOwnsOneProducerAndOneTextureView() throws Exception {
        String session = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassSession.java");
        String runtime = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassRuntime.java");
        assertTrue(session.contains("new NotificationPassBlurTextureView"));
        assertTrue(session.contains("parent.addView(host, stackIndex"));
        assertTrue(session.contains("indexOfChild(stack)"));
        assertTrue(runtime.contains("WeakHashMap<View, NotificationGlassSession> sessions"));
        assertFalse(session.contains("new NotificationPassBlurTextureView" + "(" + "row"));
    }

    @Test
    public void rendererBatchesAllRowsOverOnePreparedBackdrop() throws Exception {
        String renderer = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationPassBlurTextureView.java");
        String compositor = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassCompositor.java");
        assertTrue(renderer.contains("prismalRenderer.prepareBackdrop"));
        assertTrue(renderer.contains("compositor.drawFrame"));
        assertTrue(compositor.contains("for (NotificationGlassNode node : scene.nodes)"));
        assertTrue(compositor.contains("renderer.beginGlassFrame()"));
        assertTrue(compositor.contains("renderer.drawGlass"));
        assertEquals(1L, occurrences(renderer, "EGL14.eglSwapBuffers"));
    }

    @Test
    public void hookDoesNotBranchOnNotificationStyle() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        assertTrue(hook.contains("ExpandableNotificationRow"));
        assertTrue(hook.contains("NotificationStackScrollLayout"));
        assertTrue(hook.contains("MiuiNotificationTemplateViewWrapper"));
        assertFalse(hook.contains("showMiuiStyle"));
        assertFalse(hook.contains("notifStyle"));
        assertFalse(hook.contains("Google"));
    }

    @Test
    public void vendorBackgroundIsSuppressedOnlyAfterFirstGpuFrame() throws Exception {
        String session = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassSession.java");
        assertTrue(session.contains("onFirstFrameActive"));
        assertTrue(session.contains("active = true"));
        assertTrue(session.contains("suppressVendorMaterial()"));
        assertTrue(session.contains("onTerminalFailure"));
        assertTrue(session.contains("materialController.restoreAll()"));
    }

    @Test
    public void producerBindingIsGenerationSafeAndCpuCaptureFree() throws Exception {
        String renderer = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationPassBlurTextureView.java");
        assertTrue(renderer.contains("inputProducerGeneration = ++nextProducerGeneration"));
        assertTrue(renderer.contains("endpointGeneration != inputProducerGeneration"));
        assertTrue(renderer.contains("SystemUiPassBlurBridge.bind"));
        assertFalse(renderer.contains("PixelCopy"));
        assertFalse(renderer.contains("ImageReader"));
        assertFalse(renderer.contains("glReadPixels"));
        assertFalse(renderer.contains("MediaProjection"));
        assertFalse(renderer.contains("Bitmap"));
    }

    @Test
    public void vendorSuppressionRequiresAtLeastOneRenderedNotificationNode() throws Exception {
        String renderer = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationPassBlurTextureView.java");
        assertTrue(renderer.contains("scene != null && scene.size() > 0"));
        assertTrue(renderer.contains("post(activationListener::onFirstFrameActive)"));
    }

    @Test
    public void materialSuppressionOwnershipIsSessionLocal() throws Exception {
        String runtime = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassRuntime.java");
        String controller = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationVendorMaterialController.java");
        assertTrue(controller.contains("NotificationVendorMaterialController fork()"));
        assertTrue(runtime.contains("materialControllerPrototype.fork()"));
        assertFalse(runtime.contains("new NotificationGlassSession(stack, parent, collector, materialController)"));
    }

    @Test
    public void hostDetachDoesNotReenterParentRemoval() throws Exception {
        String session = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassSession.java");
        assertTrue(session.contains("!\"host-detached\".equals(reason)"));
        assertTrue(session.contains("parent.removeView(host)"));
    }

    @Test
    public void activeGlassSuppressesExactHyperOsShadeBlurAuthoritiesOnly() throws Exception {
        Path hookPath = Path.of("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        Path backendPath = Path.of("src/main/java/com/hellovoid/liquidui/xposed/Api101ArgumentRewriteHookBackend.java");
        assertTrue(Files.exists(backendPath));
        String hook = read(hookPath.toString());
        String backend = read(backendPath.toString());
        assertTrue(hook.contains("ShadeBlendBlurController$BlurProvider"));
        assertTrue(hook.contains("ShadeBlendBlurController$BlendBackground"));
        assertTrue(hook.contains("setBlurRatio"));
        assertTrue(hook.contains("setEnabled"));
        assertTrue(hook.contains("setPassWindowBlurEnabled"));
        assertTrue(hook.contains("com.android.systemui.statusbar.BlurUtils"));
        assertTrue(hook.contains("applyBlur"));
        assertTrue(hook.contains("NotificationShadeWindowView"));
        assertTrue(hook.contains("NotificationPanelView"));
        assertFalse(hook.contains("ControlCenter"));
        assertTrue(backend.contains("chain.proceed(args)"));
        assertTrue(backend.contains("chain.getThisObject()"));
    }

    @Test
    public void shadeBlurSuppressionFollowsPresentedNotificationGlassLifetime() throws Exception {
        String session = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassSession.java");
        String runtime = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassRuntime.java");
        assertTrue(session.contains("NotificationGlassActivityState"));
        assertTrue(session.contains("activityState.activate()"));
        assertTrue(session.contains("activityState.deactivate()"));
        assertTrue(session.contains("rows.isEmpty()"));
        assertTrue(runtime.contains("NotificationGlassActivityState"));
    }


    @Test
    public void producerSamplingFollowsHyperOsNotifPassBlurAuthority() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        String session = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassSession.java");
        String renderer = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationPassBlurTextureView.java");
        String bridge = read("src/main/java/com/hellovoid/liquidui/glass/notification/SystemUiPassBlurBridge.java");
        assertTrue(hook.contains("NotificationPassBlurAuthorityState"));
        assertTrue(hook.contains("authorityState.observe(requested)"));
        assertTrue(session.contains("authorityState.addListener"));
        assertTrue(session.contains("setVendorPassBlurEnabled"));
        assertTrue(renderer.contains("vendorPassBlurEnabled"));
        assertTrue(renderer.contains("!vendorPassBlurEnabled"));
        assertTrue(renderer.contains("SystemUiPassBlurBridge.unbind"));
        assertFalse(bridge.contains("setMiBlurWinExc"));
        assertFalse(bridge.contains("exclusions"));
    }

    @Test
    public void vendorPassBlurAuthorityBootstrapsFromNotificationBlurProviderSnapshot() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        assertTrue(hook.contains("getDeclaredField(\"passBlur\")"));
        assertTrue(hook.contains("authorityState.observe(blurProviderPassBlur.getBoolean(thisObject))"));
        int blurHook = hook.indexOf("blurProviderSetRatio,");
        int activityGuard = hook.indexOf("!activityState.isActive()", blurHook);
        int bootstrap = hook.indexOf("authorityState.observe(blurProviderPassBlur.getBoolean(thisObject))", blurHook);
        assertTrue(blurHook >= 0 && bootstrap > blurHook && activityGuard > bootstrap);
        assertFalse(hook.contains("blurProviderPassBlur.setBoolean"));
    }

    @Test
    public void shadeBlurSuppressionDoesNotMutateVendorPassBlurAuthority() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        assertFalse(hook.contains("blurProviderPassBlur.setBoolean"));
        assertFalse(hook.contains("blurProviderEnabled.setBoolean"));
        assertFalse(hook.contains("setPassWindowBlurEnabled.invoke(target, false)"));
        int start = hook.indexOf("setPassWindowBlurEnabled,");
        int end = hook.indexOf("blurUtilsApplyBlur,", start);
        assertTrue(start >= 0 && end > start);
        String passBlurHook = hook.substring(start, end);
        assertTrue(passBlurHook.contains("authorityState.observe(requested)"));
        assertFalse(passBlurHook.contains("args[0] ="));
    }


    @Test
    public void passBlurSourceComesFromRootTaskDisplayAreaNotShadeWindowRoot() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        String bridge = read("src/main/java/com/hellovoid/liquidui/glass/notification/SystemUiPassBlurBridge.java");
        String renderer = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationPassBlurTextureView.java");
        assertTrue(hook.contains("com.android.wm.shell.RootTaskDisplayAreaOrganizer"));
        assertTrue(hook.contains("onDisplayAreaAppeared"));
        assertTrue(hook.contains("onDisplayAreaVanished"));
        assertTrue(hook.contains("sourceState.observe"));
        assertTrue(bridge.contains("SurfaceControl sourceSurface"));
        assertTrue(renderer.contains("sourceState.snapshot"));
        assertTrue(renderer.contains("sourceGeneration"));
        assertFalse(bridge.contains("setPassBlurSurface.invoke(transaction, rootSurface"));
    }

    @Test
    public void prismalRoundnessUsesLiveRowRoundableAuthority() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        String collector = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassNodeCollector.java");
        assertTrue(hook.contains("getTopCornerRadius"));
        assertTrue(hook.contains("getBottomCornerRadius"));
        assertFalse(hook.contains("getDeclaredField(\"mCornerRadii\")"));
        assertTrue(collector.contains("topCornerRadius.invoke(rowObject)"));
        assertTrue(collector.contains("bottomCornerRadius.invoke(rowObject)"));
        assertFalse(collector.contains("cornerRadiiField"));
    }
    @Test
    public void producerRecreateWaitsForOutputEglInsteadOfFailingStartup() throws Exception {
        String renderer = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationPassBlurTextureView.java");
        assertTrue(renderer.contains("ProducerRecreateReadinessState"));
        assertTrue(renderer.contains("requestProducerRecreate"));
        assertTrue(renderer.contains("producer recreate deferred until output EGL ready"));
        assertTrue(renderer.contains("drainDeferredProducerRecreate"));
        assertTrue(renderer.contains("producerRecreateReadiness.onOutputUnavailable()"));
        int finish = renderer.indexOf("private void finishOutputAttach");
        int ready = renderer.indexOf("producerRecreateReadiness.onOutputReady()", finish);
        int drain = renderer.indexOf("drainDeferredProducerRecreate", finish);
        int resources = renderer.indexOf("ensureGlResources()", finish);
        assertTrue(finish >= 0 && ready > finish && drain > ready && resources > drain);
        assertFalse(renderer.contains("recreateInputProducer(\"source-change:"));
    }

    @Test
    public void outputTextureViewStaysVisibleForSurfaceLifecycleButHiddenByAlphaUntilFirstFrame() throws Exception {
        String session = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassSession.java");
        assertFalse(session.contains("renderer.setVisibility(View.INVISIBLE)"));
        assertTrue(session.contains("renderer.setAlpha(0f)"));
        assertTrue(session.contains("renderer.setAlpha(1f)"));

        int create = session.indexOf("new NotificationPassBlurTextureView");
        int hidden = session.indexOf("renderer.setAlpha(0f)", create);
        int add = session.indexOf("host.addView(renderer", create);
        assertTrue(create >= 0 && hidden > create && add > hidden);

        int firstFrame = session.indexOf("onFirstFrameActive()");
        int visible = session.indexOf("renderer.setAlpha(1f)", firstFrame);
        assertTrue(firstFrame >= 0 && visible > firstFrame);
    }

    @Test
    public void glInitializationDoesNotOverwriteProducerCreatedByDeferredRecreate() throws Exception {
        String renderer = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationPassBlurTextureView.java");
        int ensure = renderer.indexOf("private void ensureGlResources()");
        int next = renderer.indexOf("private void createInputProducer()", ensure);
        String body = renderer.substring(ensure, next);
        assertTrue(body.contains("oesTexture == 0 || inputSurfaceTexture == null || inputProducerSurface == null"));
        assertTrue(body.contains("createInputProducer()"));
    }

    private static long occurrences(String value, String needle) {
        long count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
