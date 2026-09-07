package com.hellovoid.liquidui.architecture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public final class GlassRenderSupportArchitectureTest {
    @Test
    public void materialRegistryOwnsDynamicProfileStateWithoutComponentSpecificOptics() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/core/MaterialProfileRegistry.java"));
        assertTrue(source.contains("GlassMaterialProfile.values()"));
        assertTrue(source.contains("GlassPrismalAdapter.toPrismal"));
        assertTrue(source.contains("GlassPrismalAdapter.toHighlights"));
        assertTrue(source.contains("MaterialState"));
        assertTrue(source.contains("profileVersion"));
        assertFalse(source.contains("buildPhaseZeroProfile"));
        assertFalse(source.contains("Notification"));
    }

    @Test
    public void compositorConsumesResolvedParamsHighlightsAndBlurPerNodeProfile() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/core/GlassCompositor.java"));
        assertTrue(source.contains("GlassSceneState"));
        assertTrue(source.contains("GlassSceneSnapshot"));
        assertTrue(source.contains("GlassNode"));
        assertTrue(source.contains("materialProfiles.stateFor(node.materialProfile())"));
        assertTrue(source.contains("state.params()"));
        assertTrue(source.contains("state.highlights()"));
        assertTrue(source.contains("PrismalPerformanceTuner.prepareNodeBackdrop"));
        assertFalse(source.contains("ensureFastBackdrop"));
        assertFalse(source.contains("REFRACTION_ONLY"));
        assertFalse(source.contains("Notification"));
    }
}
