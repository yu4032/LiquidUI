package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MiuiSecondaryPanelPluginGlassArchitectureTest {
    @Test
    public void secondaryPanelsUseBoundedContentBgAndExactLifecycleAuthorities() throws Exception {
        String session = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/plugin/MiuiSecondaryPanelPluginGlassSession.java"));
        String adapter = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/plugin/MiuiSecondaryPanelPluginGlassAdapter.java"));
        String module = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/ModuleMain.java"));

        assertTrue(session.contains("miui.systemui.controlcenter.panel.secondary.SecondaryPanelControllerBase"));
        assertTrue(session.contains("miui.systemui.controlcenter.panel.secondary.SecondaryParams"));
        assertTrue(session.contains("\"prepareShow\""));
        assertTrue(session.contains("\"onHidden\""));
        assertTrue(session.contains("\"onStop\""));
        assertTrue(session.contains("\"onDestroy\""));
        assertTrue(session.contains("\"onConfigurationChanged\", int.class"));
        assertTrue(session.contains("\"setContentBgColor\", float.class"));
        assertTrue(session.contains("\"setContentBgRadius\", float.class"));
        assertTrue(session.contains("\"getContentBg\""));
        assertTrue(session.contains("\"getContentBgRadius\""));

        assertTrue(adapter.contains("SystemUiGlassDomain.CONTROL_CENTER"));
        assertTrue(adapter.contains("SystemUiMaterialHostKind.CONTROL_CENTER_PANEL"));
        assertTrue(adapter.contains("isAttachedToWindow"));
        assertTrue(adapter.contains("addOnAttachStateChangeListener"));
        assertTrue(adapter.contains("material.observe"));
        assertTrue(adapter.contains("material.refresh"));
        assertTrue(adapter.contains("controller.unregister"));

        assertTrue(module.contains("MiuiSecondaryPanelPluginGlassSession.install("));

        String combined = session + adapter;
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
