package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/** Hidden/translated page descendants must never consume Prismal node work. */
public final class SystemUiEffectiveVisibilityArchitectureTest {
    @Test
    public void hostCollectionUsesAncestorAlphaAndViewportIntersection() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/systemui/SystemUiGlassHostController.java"));

        assertTrue(source.contains("effectiveWindowAlpha(host)"));
        assertTrue(source.contains("if (effectiveAlpha <= 0.001f) return null;"));
        assertTrue(source.contains("if (right <= 0f || bottom <= 0f"));
        assertTrue(source.contains("|| left >= sceneHost.getWidth() || top >= sceneHost.getHeight()) return null;"));
        assertTrue(source.contains("opacity = Math.max(0f, Math.min(1f, g.opacity() * effectiveAlpha))"));
    }
}
