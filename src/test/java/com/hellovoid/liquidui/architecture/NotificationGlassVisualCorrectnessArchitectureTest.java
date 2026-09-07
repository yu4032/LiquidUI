package com.hellovoid.liquidui.architecture;

import com.hellovoid.liquidui.config.GlassParameter;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class NotificationGlassVisualCorrectnessArchitectureTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    public void prismalOpacityMaskUsesActualPerCornerRadius() throws Exception {
        String gate = read("prismal/src/main/java/com/hellovoid/prismal/PrismalComponentGateShader.java");

        assertTrue(gate.contains(
                "float distMask = sdRoundBox(pPx, halfSz, crMask, u_sminSmoothing);"));
        assertTrue(gate.contains("float distMask = sdKy;"));
        assertTrue(gate.contains("Prismal per-corner opacity mask"));
    }

    @Test
    public void phaseZeroProfilePreservesValidatedRefractionWithoutParallax() throws Exception {
        String material = read(
                "src/main/java/com/hellovoid/liquidui/glass/core/MaterialProfileRegistry.java");

        assertTrue(material.contains("GlassPrismalAdapter.toPrismal"));
        assertEquals(0L, GlassParameter.PARALLAX_SCALE.defaultRaw());
        assertEquals(170L, GlassParameter.DISPLACEMENT_SCALE.defaultRaw());
        assertEquals(220L, GlassParameter.LENS_REFRACTION.defaultRaw());
        assertEquals(42L, GlassParameter.CHROMATIC.defaultRaw());
    }

    @Test
    public void phaseZeroWindowRendererPreservesValidatedZeroSamplingInset() throws Exception {
        String renderer = read(
                "src/main/java/com/hellovoid/liquidui/glass/core/WindowGlassRenderer.java");

        assertTrue(renderer.contains("topSamplingExtraPx = Integer.MIN_VALUE"));
        assertTrue(renderer.contains("bottomSamplingExtraPx = Integer.MIN_VALUE"));
        assertTrue(renderer.contains("leftSamplingExtraPx = Integer.MIN_VALUE"));
        assertTrue(renderer.contains("rightSamplingExtraPx = Integer.MIN_VALUE"));
        assertTrue(renderer.contains("combineAutoGuardAndUserExtra"));
    }

    @Test
    public void glassUsesSameFixedRadiusAuthorityAsNativeMiuiPassBlurOutline() throws Exception {
        String collector = read(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassNodeCollector.java");

        assertTrue(collector.contains("notification_item_bg_radius"));
        assertTrue(collector.contains("NATIVE_CARD_RADIUS_FALLBACK_DP = 24f"));
        assertTrue(collector.contains("nativeCardRadiusPx(background)"));
        assertTrue(collector.contains("radius,\n                    radius,\n                    radius,\n                    radius"));
        assertFalse(collector.contains("topCornerRadius.invoke(rowObject)"));
        assertFalse(collector.contains("bottomCornerRadius.invoke(rowObject)"));
    }
}
