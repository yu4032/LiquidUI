package com.hellovoid.liquidui.architecture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public final class GlassRenderSupportArchitectureTest {
    @Test
    public void materialRegistryKeepsValidatedPhaseZeroOpticsComponentNeutral() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/core/MaterialProfileRegistry.java"));
        assertTrue(source.contains("GlassMaterialProfile.values()"));
        assertTrue(source.contains("b.ior = 1.55f"));
        assertTrue(source.contains("b.chromaticAberration = 42f"));
        assertTrue(source.contains("b.parallaxScale = 0f"));
        assertTrue(source.contains("b.blurRadiusPx = 0f"));
        assertFalse(source.contains("Notification"));
    }

    @Test
    public void compositorConsumesGenericSceneAndProfiles() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/core/GlassCompositor.java"));
        assertTrue(source.contains("GlassSceneState"));
        assertTrue(source.contains("GlassSceneSnapshot"));
        assertTrue(source.contains("GlassNode"));
        assertTrue(source.contains("node.materialProfile()"));
        assertTrue(source.contains("PrismalPerformanceTuner.ensureFastBackdrop"));
        assertFalse(source.contains("Notification"));
    }
}
