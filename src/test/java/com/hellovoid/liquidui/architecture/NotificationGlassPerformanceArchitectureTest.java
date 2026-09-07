package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class NotificationGlassPerformanceArchitectureTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    public void phaseZeroProfileSkipsExpensiveGaussianWithCoreTuner() throws Exception {
        String material = read("src/main/java/com/hellovoid/liquidui/glass/core/MaterialProfileRegistry.java");
        String tuner = read("src/main/java/com/hellovoid/liquidui/glass/core/PrismalPerformanceTuner.java");
        String compositor = read("src/main/java/com/hellovoid/liquidui/glass/core/GlassCompositor.java");

        assertTrue(material.contains("b.blurRadiusPx = 0f"));
        assertTrue(tuner.contains("FAST_COPY_FRAGMENT"));
        assertTrue(tuner.contains("renderer.createProgram"));
        assertTrue(tuner.contains("renderer.blurHProgram = fastH"));
        assertTrue(tuner.contains("renderer.blurVProgram = fastV"));
        assertTrue(tuner.contains("GLES20.glDeleteProgram(oldH)"));
        assertTrue(tuner.contains("GLES20.glDeleteProgram(oldV)"));
        assertTrue(compositor.contains("PrismalPerformanceTuner.ensureFastBackdrop(renderer)"));
    }

    @Test
    public void gpuOnlyAndRefractionContractsRemainIntactInWindowCore() throws Exception {
        String renderer = read("src/main/java/com/hellovoid/liquidui/glass/core/WindowGlassRenderer.java");
        String material = read("src/main/java/com/hellovoid/liquidui/glass/core/MaterialProfileRegistry.java");

        assertTrue(renderer.contains("GL_TEXTURE_EXTERNAL_OES"));
        assertTrue(renderer.contains("updateTexImage"));
        assertFalse(renderer.contains("glReadPixels"));
        assertTrue(material.contains("b.displacementScale = 1.70f"));
        assertTrue(material.contains("b.lensRefractionScale = 2.20f"));
        assertTrue(material.contains("b.chromaticAberration = 42f"));
    }
}
