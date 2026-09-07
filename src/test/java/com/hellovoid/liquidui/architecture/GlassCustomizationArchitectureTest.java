package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GlassCustomizationArchitectureTest {
    @Test
    public void remotePreferencesPublishCompleteStylesIntoOneProcessCore() throws Exception {
        String module = read("src/main/java/com/hellovoid/liquidui/ModuleMain.java");
        String runtime = read("src/main/java/com/hellovoid/liquidui/config/GlassConfigRuntime.java");
        String core = read("src/main/java/com/hellovoid/liquidui/glass/core/SystemUiGlassCore.java");

        assertTrue(module.contains("preferences::getBoolean, preferences::getInt"));
        assertTrue(module.contains("GlassStyleConfig.read(configReader)"));
        assertTrue(module.contains("new GlassConfigRuntime("));
        assertTrue(module.contains("processGlassCore.updateGlassStyles("));

        assertTrue(runtime.contains("registerOnSharedPreferenceChangeListener(this)"));
        assertTrue(runtime.contains("unregisterOnSharedPreferenceChangeListener(this)"));
        assertTrue(runtime.contains("ConfigSchema.isGlassStyleKey(key)"));
        assertTrue(runtime.contains("GlassStyleConfig.read("));

        assertTrue(core.contains("GlassStyleUpdateQueue"));
        assertTrue(core.contains("public long updateGlassStyles(GlassStyleConfig style)"));
        assertTrue(core.contains("windows.updateGlassStyles(snapshot.style(), snapshot.version())"));
    }

    @Test
    public void liveAndFutureWindowSessionsReceiveLatestStyleWithoutProducerRecreation() throws Exception {
        String registry = read("src/main/java/com/hellovoid/liquidui/glass/core/WindowGlassRegistry.java");
        String session = read("src/main/java/com/hellovoid/liquidui/glass/core/WindowGlassSession.java");
        String renderer = read("src/main/java/com/hellovoid/liquidui/glass/core/WindowGlassRenderer.java");

        assertTrue(registry.contains("created.updateGlassStyles(latestStyle, latestStyleVersion)"));
        assertTrue(registry.contains("void updateGlassStyles(GlassStyleConfig style, long version)"));
        assertTrue(registry.contains("session.updateGlassStyles(style, version)"));

        assertTrue(session.contains("public synchronized void updateGlassStyles("));
        assertTrue(session.contains("renderer.updateGlassStyles(style, version)"));
        assertTrue(session.contains("nextRenderer.updateGlassStyles(styleConfig, styleVersion)"));

        String updateMethod = methodBody(renderer, "void updateGlassStyles(", "void requestScene(");
        assertTrue(updateMethod.contains("materialProfiles.update(style, version)"));
        assertTrue(updateMethod.contains("presentationState.styleVersion(version)"));
        assertTrue(updateMethod.contains("frameCoordinator.requestScene()"));
        assertTrue(updateMethod.contains("samplingAutoEnabled()"));
        assertFalse(updateMethod.contains("rebindProducer"));
        assertFalse(updateMethod.contains("recreateInputProducer"));
        assertFalse(updateMethod.contains("SystemUiPassBlurBridge.bind"));
        assertFalse(updateMethod.contains("producerRecovery"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static String methodBody(String source, String startNeedle, String endNeedle) {
        int start = source.indexOf(startNeedle);
        if (start < 0) return "";
        int end = source.indexOf(endNeedle, start + startNeedle.length());
        return end < 0 ? source.substring(start) : source.substring(start, end);
    }
}
