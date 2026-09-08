package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MiuiControlCenterPluginGlassArchitectureTest {
    @Test
    public void pluginTilesCardsAndBrightnessUseExactAuthorities() throws Exception {
        String session = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/plugin/MiuiControlCenterPluginGlassSession.java"));
        String adapter = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/plugin/MiuiControlCenterPluginGlassAdapter.java"));
        String tileMaterial = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/plugin/MiuiControlCenterTileMaterialController.java"));
        String surfaceMaterial = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/plugin/MiuiControlCenterSurfaceMaterialController.java"));
        String module = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/ModuleMain.java"));

        assertTrue(session.contains("miui.systemui.controlcenter.qs.tileview.QSTileItemView"));
        assertTrue(session.contains("miui.systemui.controlcenter.qs.tileview.QSTileItemIconView"));
        assertTrue(session.contains("\"init\""));
        assertTrue(session.contains("\"updateIcon\""));
        assertTrue(session.contains("\"recycle\""));

        assertTrue(session.contains("miui.systemui.controlcenter.qs.tileview.QSCardItemView"));
        assertTrue(session.contains("\"updateBackground\", boolean.class"));

        assertTrue(session.contains("miui.systemui.controlcenter.panel.main.recyclerview.ToggleSliderViewHolder"));
        assertTrue(session.contains("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelItemViewHolder"));
        assertTrue(session.contains("miui.systemui.controlcenter.panel.main.recyclerview.ControlCenterViewHolder"));
        assertTrue(session.contains("\"onViewAttachedToWindow\""));
        assertTrue(session.contains("\"onViewDetachedFromWindow\""));
        assertTrue(session.contains("\"updateBlendBlur\""));
        assertTrue(session.contains("\"onConfigurationChanged\", int.class"));

        assertTrue(adapter.contains("SystemUiGlassDomain.CONTROL_CENTER"));
        assertTrue(adapter.contains("SystemUiMaterialHostKind.QS_TILE"));
        assertTrue(adapter.contains("SystemUiMaterialHostKind.BRIGHTNESS_SLIDER"));
        assertTrue(adapter.contains("iconFrame"));
        assertTrue(adapter.contains("toggleSliderInner"));
        assertTrue(adapter.contains("progressBg"));
        assertTrue(adapter.contains("control_center_universal_corner_radius"));

        assertTrue(tileMaterial.contains("android.graphics.drawable.LayerDrawable"));
        assertTrue(tileMaterial.contains("clearMiBackgroundBlendColor"));
        assertTrue(tileMaterial.contains("setMiViewBlurMode"));
        assertTrue(tileMaterial.contains("setAlpha(0)"));
        assertTrue(tileMaterial.contains("ArrayList<Point>"));

        assertTrue(surfaceMaterial.contains("originalAlpha"));
        assertTrue(surfaceMaterial.contains("clearMiBackgroundBlendColor"));
        assertTrue(surfaceMaterial.contains("ArrayList<Point>"));
        assertTrue(surfaceMaterial.contains("setAlpha(0)"));

        assertTrue(module.contains("MiuiControlCenterPluginGlassSession.install("));

        String combined = session + adapter + tileMaterial + surfaceMaterial;
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
