package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ControlCenterBrightnessGlassArchitectureTest {
    @Test
    public void classicBrightnessUsesExactSeekbarTrackAuthority() throws Exception {
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/controlcenter/ControlCenterGlassHook.java"));
        String adapter = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/controlcenter/ControlCenterGlassAdapter.java"));
        String material = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/controlcenter/BrightnessSliderNativeMaterialController.java"));

        assertTrue(hook.contains("com.android.systemui.settings.brightness.BrightnessSliderView"));
        assertTrue(hook.contains("com.android.systemui.qs.MiuiQSContainer"));
        assertTrue(hook.contains("\"onFinishInflate\""));
        assertTrue(hook.contains("\"onConfigurationChanged\", Configuration.class"));
        assertTrue(hook.contains("mSlider"));
        assertTrue(hook.contains("brightnessView"));

        assertTrue(adapter.contains("SystemUiMaterialHostKind.BRIGHTNESS_SLIDER"));
        assertTrue(adapter.contains("panel_content_corner_radius"));
        assertTrue(material.contains("android.R.id.background"));
        assertTrue(material.contains("findDrawableByLayerId"));
        assertTrue(material.contains("setAlpha(0)"));
        assertTrue(material.contains("originalAlpha"));
        assertTrue(material.contains("setAlpha(state.originalAlpha)"));

        String combined = hook + adapter + material;
        assertFalse(combined.contains("setProgressDrawable"));
        assertFalse(combined.contains("new WindowGlassRenderer"));
        assertFalse(combined.contains("HandlerThread"));
        assertFalse(combined.contains("SurfaceTexture"));
        assertFalse(combined.contains("SetPassBlurSurface"));
    }
}
