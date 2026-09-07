package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ControlCenterGlassArchitectureTest {
    @Test
    public void builtInQuickSettingsUsesExactReconcileAndIconUpdateAuthorities() throws Exception {
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/controlcenter/ControlCenterGlassHook.java"));
        String adapter = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/controlcenter/ControlCenterGlassAdapter.java"));
        String material = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/controlcenter/ControlCenterNativeMaterialController.java"));
        String module = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/ModuleMain.java"));

        assertTrue(hook.contains("com.android.systemui.qs.MiuiQSPanel"));
        assertTrue(hook.contains("com.android.systemui.qs.tileimpl.MiuiQSTileBaseView"));
        assertTrue(hook.contains("com.android.systemui.qs.tileimpl.MiuiQSIconViewImpl"));
        assertTrue(hook.contains("getDeclaredMethod(\"setTiles\", Collection.class, boolean.class)"));
        assertTrue(hook.contains("getDeclaredMethod(\"updateIcon\""));
        assertTrue(hook.contains("mRecords"));
        assertTrue(hook.contains("mIconFrame"));
        assertTrue(hook.contains("mIcon"));
        assertTrue(hook.contains("mAnimator"));

        assertTrue(adapter.contains("SystemUiGlassHostController"));
        assertTrue(adapter.contains("SystemUiMaterialHostKind.QS_TILE"));
        assertTrue(material.contains("LayerDrawable"));
        assertTrue(material.contains("setAlpha(0)"));
        assertTrue(material.contains("restoreAll"));
        assertTrue(module.contains("new ControlCenterGlassHook("));

        String combined = hook + adapter + material;
        assertFalse(combined.contains("new WindowGlassRenderer"));
        assertFalse(combined.contains("new SystemUiGlassCore"));
        assertFalse(combined.contains("HandlerThread"));
        assertFalse(combined.contains("SurfaceTexture"));
        assertFalse(combined.contains("SetPassBlurSurface"));
        assertFalse(combined.contains("PixelCopy"));
        assertFalse(combined.contains("ScreenCapture"));
    }

    @Test
    public void newHyperOsControlCenterRemainsUnhookedWithoutPluginProvenance() throws Exception {
        String contract = Files.readString(Path.of(
                "docs/reverse-engineering/systemui-001/control-center-glass-contract.md"));
        assertTrue(contract.contains("com.android.systemui.action.PLUGIN_MIUI_CONTROL_CENTER"));
        assertTrue(contract.contains("com.miui.systemui.plugin"));
        assertTrue(contract.contains("Production hooks for the new Control Center remain deliberately absent"));
    }
}
