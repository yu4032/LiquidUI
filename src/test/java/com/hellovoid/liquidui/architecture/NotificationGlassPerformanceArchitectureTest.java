package com.hellovoid.liquidui.architecture;

import com.hellovoid.liquidui.config.GlassParameter;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class NotificationGlassPerformanceArchitectureTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    public void phaseZeroProfileSkipsExpensiveGaussianWithPerNodeBlurPolicy() throws Exception {
        String material = read("src/main/java/com/hellovoid/liquidui/glass/core/MaterialProfileRegistry.java");
        String tuner = read("src/main/java/com/hellovoid/liquidui/glass/core/PrismalPerformanceTuner.java");
        String compositor = read("src/main/java/com/hellovoid/liquidui/glass/core/GlassCompositor.java");

        assertEquals(0L, GlassParameter.BLUR.defaultRaw());
        assertTrue(material.contains("GlassPrismalAdapter.toPrismal"));
        assertTrue(tuner.contains("FAST_COPY_FRAGMENT"));
        assertTrue(tuner.contains("GAUSSIAN_FRAGMENT"));
        assertTrue(tuner.contains("params.blurRadiusPx <= 0f"));
        assertTrue(tuner.contains("renderer.renderBlur(params)"));
        assertTrue(compositor.contains("PrismalPerformanceTuner.prepareNodeBackdrop(renderer, state.params())"));
    }

    @Test
    public void gpuOnlyAndRefractionContractsRemainIntactInWindowCore() throws Exception {
        String renderer = read("src/main/java/com/hellovoid/liquidui/glass/core/WindowGlassRenderer.java");
        String material = read("src/main/java/com/hellovoid/liquidui/glass/core/MaterialProfileRegistry.java");

        assertTrue(renderer.contains("GL_TEXTURE_EXTERNAL_OES"));
        assertTrue(renderer.contains("updateTexImage"));
        assertFalse(renderer.contains("glReadPixels"));
        assertTrue(material.contains("GlassPrismalAdapter.toPrismal"));
        assertEquals(170L, GlassParameter.DISPLACEMENT_SCALE.defaultRaw());
        assertEquals(220L, GlassParameter.LENS_REFRACTION.defaultRaw());
        assertEquals(42L, GlassParameter.CHROMATIC.defaultRaw());
    }
}
