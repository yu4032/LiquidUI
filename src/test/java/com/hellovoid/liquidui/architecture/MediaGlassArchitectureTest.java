package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MediaGlassArchitectureTest {
    @Test
    public void miuiNotificationMediaUsesExactPlayerAndBackgroundAuthorities() throws Exception {
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/media/MediaGlassHook.java"));
        String adapter = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/media/MediaGlassAdapter.java"));
        String material = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/media/MediaNativeMaterialController.java"));
        String module = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/ModuleMain.java"));

        assertTrue(hook.contains("com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl"));
        assertTrue(hook.contains("com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewHolder"));
        assertTrue(hook.contains("\"attach\""));
        assertTrue(hook.contains("\"detach\""));
        assertTrue(hook.contains("\"updateMediaBackground\""));
        assertTrue(hook.contains("\"player\""));
        assertTrue(hook.contains("\"mediaBg\""));

        assertTrue(adapter.contains("SystemUiGlassDomain.MEDIA"));
        assertTrue(adapter.contains("SystemUiMaterialHostKind.MEDIA_CARD"));
        assertTrue(adapter.contains("notification_item_bg_radius"));

        assertTrue(material.contains("setMiViewBlurMode"));
        assertTrue(material.contains("clearMiBackgroundBlendColor"));
        assertTrue(material.contains("getMiBackgroundBlendColor"));
        assertTrue(material.contains("setMiBackgroundBlendColors"));
        assertTrue(material.contains("setAlpha(0)"));
        assertTrue(material.contains("originalAlpha"));
        assertTrue(module.contains("new MediaGlassHook("));

        String combined = hook + adapter + material;
        assertFalse(combined.contains("new WindowGlassRenderer"));
        assertFalse(combined.contains("new SystemUiGlassCore"));
        assertFalse(combined.contains("HandlerThread"));
        assertFalse(combined.contains("SurfaceTexture"));
        assertFalse(combined.contains("SetPassBlurSurface"));
        assertFalse(combined.contains("PixelCopy"));
        assertFalse(combined.contains("ScreenCapture"));
    }
}
