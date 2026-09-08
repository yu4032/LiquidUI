package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class OngoingActivityChipGlassArchitectureTest {
    @Test
    public void ongoingActivityChipUsesExactBinderAndBackgroundViewAuthority() throws Exception {
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/statusbar/OngoingActivityChipGlassHook.java"));
        String adapter = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/statusbar/OngoingActivityChipGlassAdapter.java"));
        String material = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/statusbar/OngoingActivityChipNativeMaterialController.java"));
        String module = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/ModuleMain.java"));

        assertTrue(hook.contains("com.android.systemui.statusbar.chips.ui.binder.OngoingActivityChipBinder"));
        assertTrue(hook.contains("com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel$Active"));
        assertTrue(hook.contains("NotificationIconContainerViewBinder$IconViewStore"));
        assertTrue(hook.contains("\"bind\""));
        assertTrue(hook.contains("\"backgroundView\""));

        assertTrue(adapter.contains("SystemUiGlassDomain.STATUS_BAR"));
        assertTrue(adapter.contains("SystemUiMaterialHostKind.STATUS_CAPSULE"));
        assertTrue(adapter.contains("ongoing_activity_chip_corner_radius"));
        assertTrue(material.contains("GradientDrawable"));
        assertTrue(material.contains("originalAlpha"));
        assertTrue(material.contains("setAlpha(0)"));
        assertTrue(material.contains("refresh"));
        assertTrue(module.contains("new OngoingActivityChipGlassHook("));

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
