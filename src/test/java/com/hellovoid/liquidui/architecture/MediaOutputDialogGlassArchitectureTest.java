package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MediaOutputDialogGlassArchitectureTest {
    @Test
    public void mediaOutputDialogUsesExactDialogRootAndStopLifecycle() throws Exception {
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/media/MediaOutputDialogGlassHook.java"));
        String adapter = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/media/MediaOutputDialogGlassAdapter.java"));
        String material = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/media/MediaOutputDialogNativeMaterialController.java"));
        String module = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/ModuleMain.java"));

        assertTrue(hook.contains("com.android.systemui.media.dialog.MediaOutputBaseDialog"));
        assertTrue(hook.contains("\"onCreate\", Bundle.class"));
        assertTrue(hook.contains("\"stop\""));
        assertTrue(hook.contains("\"updateDialogBackgroundColor\""));
        assertTrue(hook.contains("\"mDialogView\""));
        assertTrue(hook.contains("\"mDeviceListLayout\""));

        assertTrue(adapter.contains("SystemUiGlassDomain.MEDIA"));
        assertTrue(adapter.contains("SystemUiMaterialHostKind.SYSTEM_DIALOG"));
        assertTrue(adapter.contains("MEDIA_OUTPUT_DIALOG_RADIUS_DP"));
        assertTrue(adapter.contains("28f"));

        assertTrue(material.contains("rootOriginalAlpha"));
        assertTrue(material.contains("listOriginalAlpha"));
        assertTrue(material.contains("setAlpha(0)"));
        assertTrue(material.contains("refresh"));
        assertFalse(material.contains("setBackground(null)"));
        assertTrue(module.contains("new MediaOutputDialogGlassHook("));

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
