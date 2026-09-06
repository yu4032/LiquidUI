package com.hellovoid.liquidui.glass.notification;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Regression contract for the HyperOS-native-background capture architecture. */
public class NotificationCaptureDrawableArchitectureTest {
    @Test
    public void notificationGlassInstallsOnNativeBackgroundViewInsteadOfOverlayTextureView() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        String runtime = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationCaptureGlassRuntime.java");
        String drawable = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationCaptureGlassDrawable.java");

        assertTrue(hook.contains("com.android.systemui.statusbar.notification.utils.NotificationUtil"));
        assertTrue(hook.contains("applyElementViewBlend"));
        assertTrue(hook.contains("setMiBackgroundBlendColors"));
        assertTrue(hook.contains("setMiViewBlurModeCompat"));
        assertTrue(runtime.contains("NotificationBackgroundView"));
        assertTrue(runtime.contains("view.setBackground(drawable)"));
        assertTrue(runtime.contains("originalBackground"));
        assertTrue(runtime.contains("restore"));
        assertTrue(drawable.contains("extends Drawable"));

        assertFalse(hook.contains("new NotificationGlassRuntime("));
        assertFalse(hook.contains("RootTaskDisplayAreaOrganizer"));
        assertFalse(runtime.contains("NotificationPassBlurTextureView"));
        assertFalse(runtime.contains("SystemUiPassBlurBridge"));
    }

    @Test
    public void installDisablesNativeBlurOnTheSameNotificationBackgroundView() throws Exception {
        String runtime = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationCaptureGlassRuntime.java");
        assertTrue(runtime.contains("clearMiBackgroundBlendColorCompat"));
        assertTrue(runtime.contains("setMiBackgroundBlurMode"));
        assertTrue(runtime.contains("setMiViewBlurModeCompat"));
        assertTrue(runtime.contains("setMiBackgroundBlurRadius"));
        assertTrue(runtime.contains("setPassWindowBlurEnabled"));
        assertTrue(runtime.contains("0"));
        assertTrue(runtime.contains("false"));
    }

    @Test
    public void captureBackendExcludesNotificationShadeAndUsesAdaptiveTwoTierRefresh() throws Exception {
        String capture = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationDisplayCaptureBackend.java");

        assertTrue(capture.contains("android.window.ScreenCapture$CaptureArgs$Builder"));
        assertTrue(capture.contains("captureDisplay"));
        assertTrue(capture.contains("setFrameScale"));
        assertTrue(capture.contains("setExcludeLayers"));
        assertTrue(capture.contains("setExcludeOrIncludeLayerNames"));
        assertTrue(capture.contains("setCaptureMode"));
        assertTrue(capture.contains("NotificationShade#"));
        assertTrue(capture.contains("StatusBar#"));
        assertTrue(capture.contains("NavigationBar0#"));
        assertTrue(capture.contains("DynamicIslandWindow#"));
        assertTrue(capture.contains("FULL_CAPTURE_SCALE"));
        assertTrue(capture.contains("CHANGE_PROBE_SCALE"));
        assertTrue(capture.contains("0.20f"));
        assertTrue(capture.contains("0.04f"));
        assertTrue(capture.contains("OnPreDrawListener"));
        assertTrue(capture.contains("HandlerThread"));
        assertTrue(capture.contains("CHANGE_PIXEL_THRESHOLD"));
        assertTrue(capture.contains("48"));
    }

    @Test
    public void notificationShadeRootSurfaceIsOnlyExcludedFromCaptureNeverUsedAsProducer() throws Exception {
        String capture = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationDisplayCaptureBackend.java");
        assertTrue(capture.contains("getViewRootImpl"));
        assertTrue(capture.contains("getSurfaceControl"));
        assertTrue(capture.contains("SurfaceControl[]"));
        assertFalse(capture.contains("SetPassBlurSurface"));
        assertFalse(capture.contains("setUpdateTextureFlag"));
    }

    @Test
    public void capturedBitmapIsMappedIntoNativeViewCanvasAndClippedOnce() throws Exception {
        String drawable = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationCaptureGlassDrawable.java");
        assertTrue(drawable.contains("getLocationOnScreen"));
        assertTrue(drawable.contains("drawBitmap"));
        assertTrue(drawable.contains("RenderEffect.createBlurEffect"));
        assertTrue(drawable.contains("addRoundRect"));
        assertTrue(drawable.contains("clipPath"));
        assertFalse(drawable.contains("TextureView"));
        assertFalse(drawable.contains("SurfaceTexture"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
