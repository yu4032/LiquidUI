package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public final class PrismalPerProfileBlurArchitectureTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    public void compositorRebuildsOnlyWhenPerNodeBlurRadiusChanges() throws Exception {
        String compositor = read(
                "src/main/java/com/hellovoid/liquidui/glass/core/GlassCompositor.java");

        assertTrue(compositor.contains("Float activeBlurRadius"));
        assertTrue(compositor.contains("Float.compare(activeBlurRadius, state.params().blurRadiusPx)"));
        assertTrue(compositor.contains(
                "PrismalPerformanceTuner.prepareNodeBackdrop(renderer, state.params())"));
        assertFalse(compositor.contains("ensureFastBackdrop"));
    }

    @Test
    public void tunerSwitchesZeroBlurToCopyAndNonZeroToOfficialGaussian() throws Exception {
        String tuner = read(
                "src/main/java/com/hellovoid/liquidui/glass/core/PrismalPerformanceTuner.java");

        assertTrue(tuner.contains("FAST_COPY_FRAGMENT"));
        assertTrue(tuner.contains("GAUSSIAN_FRAGMENT"));
        assertTrue(tuner.contains("params.blurRadiusPx <= 0f"));
        assertTrue(tuner.contains("renderer.renderBlur(params)"));
        assertTrue(tuner.contains("Mode.FAST_COPY"));
        assertTrue(tuner.contains("Mode.GAUSSIAN"));
        assertFalse(tuner.contains("TUNED.containsKey"));
    }

    @Test
    public void blurDerivationStillUsesOneRendererAndNoNewProducer() throws Exception {
        String compositor = read(
                "src/main/java/com/hellovoid/liquidui/glass/core/GlassCompositor.java");
        String tuner = read(
                "src/main/java/com/hellovoid/liquidui/glass/core/PrismalPerformanceTuner.java");

        assertFalse(compositor.contains("new PrismalRenderer"));
        assertFalse(tuner.contains("new PrismalRenderer"));
        assertFalse(tuner.contains("SurfaceTexture"));
        assertFalse(tuner.contains("SetPassBlurSurface"));
        assertFalse(tuner.contains("EGL14"));
    }
}
