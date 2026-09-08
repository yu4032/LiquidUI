package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MiuiVolumePluginGlassArchitectureTest {
    @Test
    public void floatingVolumePanelAndColumnsUseExactPluginAuthorities() throws Exception {
        String session = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/plugin/MiuiVolumePluginGlassSession.java"));
        String adapter = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/plugin/MiuiVolumePluginGlassAdapter.java"));
        String panelMaterial = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/plugin/MiuiVolumePanelMaterialController.java"));
        String columnMaterial = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/plugin/MiuiVolumeColumnMaterialController.java"));
        String module = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/ModuleMain.java"));

        assertTrue(session.contains("com.android.systemui.miui.volume.VolumePanelViewController"));
        assertTrue(session.contains("com.android.systemui.miui.volume.VolumeColumn"));
        assertTrue(session.contains("\"initPanelView\""));
        assertTrue(session.contains("\"reInit\""));
        assertTrue(session.contains("\"updateExpandedH\", boolean.class, boolean.class, boolean.class"));
        assertTrue(session.contains("\"updateColumnH\", volumeColumnClass"));
        assertTrue(session.contains("\"initColumn\", Context.class, ViewGroup.class, int.class"));
        assertTrue(session.contains("\"setSliderBlendColor\", boolean.class"));
        assertTrue(session.contains("\"setSliderResource\", boolean.class"));
        assertTrue(session.contains("\"release\""));

        assertTrue(adapter.contains("SystemUiGlassDomain.VOLUME"));
        assertTrue(adapter.contains("SystemUiMaterialHostKind.VOLUME_PANEL"));
        assertTrue(adapter.contains("SystemUiMaterialHostKind.VOLUME_SLIDER"));
        assertTrue(adapter.contains("mExpandBgView"));
        assertTrue(adapter.contains("mVolumeContentView"));
        assertTrue(adapter.contains("getView"));
        assertTrue(adapter.contains("getSlider"));
        assertTrue(adapter.contains("getProgressViewBg"));
        assertTrue(adapter.contains("getRadius"));

        assertTrue(panelMaterial.contains("isBlurEnabledAndSupported"));
        assertTrue(panelMaterial.contains("setBlurEnabled"));
        assertTrue(panelMaterial.contains("clearMiBackgroundBlendColor"));
        assertTrue(panelMaterial.contains("ArrayList<Point>"));
        assertTrue(panelMaterial.contains("contentOriginalAlpha"));

        assertTrue(columnMaterial.contains("isBlurEnabledAndSupported"));
        assertTrue(columnMaterial.contains("setBlurEnabled"));
        assertTrue(columnMaterial.contains("clearMiBackgroundBlendColor"));
        assertTrue(columnMaterial.contains("ArrayList<Point>"));
        assertTrue(columnMaterial.contains("sliderOriginalAlpha"));
        assertFalse(columnMaterial.contains("progressView.setAlpha(0)"));

        assertTrue(module.contains("MiuiControlCenterPluginGlassSession.install("));
        assertTrue(module.contains("MiuiVolumePluginGlassSession.install("));

        String combined = session + adapter + panelMaterial + columnMaterial;
        assertFalse(combined.contains("getChildAt("));
        assertFalse(combined.contains("findViewById"));
        assertFalse(combined.contains("new WindowGlassRenderer"));
        assertFalse(combined.contains("new SystemUiGlassCore"));
        assertFalse(combined.contains("HandlerThread"));
        assertFalse(combined.contains("SurfaceTexture"));
        assertFalse(combined.contains("SetPassBlurSurface"));
        assertFalse(combined.contains("PixelCopy"));
        assertFalse(combined.contains("ScreenCapture"));
    }
}
