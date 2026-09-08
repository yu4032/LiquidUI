package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MiuiControlCenterMediaPluginGlassArchitectureTest {
    @Test
    public void mainMediaCardUsesExactHolderRootAndVendorMaterialAuthorities() throws Exception {
        String session = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/plugin/MiuiControlCenterMediaPluginGlassSession.java"));
        String adapter = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/plugin/MiuiControlCenterMediaPluginGlassAdapter.java"));
        String module = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/ModuleMain.java"));

        assertTrue(session.contains("miui.systemui.controlcenter.panel.main.media.MediaPlayerController$MediaPlayerViewHolder"));
        assertTrue(session.contains("miui.systemui.controlcenter.panel.main.media.MediaPlayerController"));
        assertTrue(session.contains("miui.systemui.controlcenter.panel.main.recyclerview.ControlCenterViewHolder"));
        assertTrue(session.contains("\"getContentView\""));
        assertTrue(session.contains("\"getCornerRadius\""));
        assertTrue(session.contains("\"updateBlendBlur\""));
        assertTrue(session.contains("\"onConfigurationChanged\", int.class"));
        assertTrue(session.contains("\"onViewAttachedToWindow\""));
        assertTrue(session.contains("\"onViewDetachedFromWindow\""));
        assertTrue(session.contains("\"recycle\""));
        assertTrue(session.contains("\"onUnbindViewHolder\""));

        assertTrue(adapter.contains("SystemUiGlassDomain.MEDIA"));
        assertTrue(adapter.contains("SystemUiMaterialHostKind.MEDIA_CARD"));
        assertTrue(adapter.contains("material.observe"));
        assertTrue(adapter.contains("material.refresh"));
        assertTrue(adapter.contains("controller.unregister"));
        assertTrue(adapter.contains("isAttachedToWindow"));

        assertTrue(module.contains("MiuiControlCenterMediaPluginGlassSession.install("));

        String combined = session + adapter;
        assertFalse(combined.contains("findViewById"));
        assertFalse(combined.contains("getChildAt("));
        assertFalse(combined.contains("new WindowGlassRenderer"));
        assertFalse(combined.contains("new SystemUiGlassCore"));
        assertFalse(combined.contains("HandlerThread"));
        assertFalse(combined.contains("SurfaceTexture"));
        assertFalse(combined.contains("SetPassBlurSurface"));
        assertFalse(combined.contains("PixelCopy"));
        assertFalse(combined.contains("ScreenCapture"));
    }
}
