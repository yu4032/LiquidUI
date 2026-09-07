package com.hellovoid.liquidui.glass.core;

import com.hellovoid.liquidui.config.ConfigReader;
import com.hellovoid.liquidui.config.ConfigSchema;
import com.hellovoid.liquidui.config.GlassHighlight;
import com.hellovoid.liquidui.config.GlassParameter;
import com.hellovoid.liquidui.config.GlassStyleConfig;
import com.hellovoid.prismal.PrismalHighlightProfile;
import com.hellovoid.prismal.PrismalParams;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GlassPrismalAdapterTest {
    private static final float EPS = 0.0001f;

    @Test
    public void phaseZeroDefaultsMapExactlyAtDensityTwo() {
        GlassStyleConfig.ResolvedStyle style =
                GlassStyleConfig.defaults().resolved(GlassMaterialProfile.CARD);
        PrismalParams p = GlassPrismalAdapter.toPrismal(style, 2f);

        assertEquals(1.55f, p.ior, EPS);
        assertEquals(44f, p.glassThicknessPx, EPS);
        assertEquals(1.35f, p.normalStrength, EPS);
        assertEquals(1.70f, p.displacementScale, EPS);
        assertEquals(40f, p.heightTransitionWidthPx, EPS);
        assertEquals(1.8f, p.sminSmoothingPx, EPS);
        assertEquals(20f, p.refractionInsetPx, EPS);
        assertEquals(4f, p.edgeRefractionFalloff, EPS);
        assertEquals(1.35f, p.liquidDome, EPS);
        assertEquals(1f, p.fresnelReflect, EPS);
        assertEquals(2.20f, p.lensRefractionScale, EPS);
        assertEquals(1f, p.lensDepthEffect, EPS);
        assertEquals(42f, p.chromaticAberration, EPS);
        assertEquals(1f, p.dispersionR, EPS);
        assertEquals(1f, p.dispersionB, EPS);
        assertEquals(1.30f, p.vibrancy, EPS);
        assertEquals(0f, p.plainHighlight, EPS);
        assertEquals(1f, p.brightness, EPS);
        assertEquals(0f, p.highlightWidth, EPS);
        assertEquals(-0.5f, p.lightDirX, EPS);
        assertEquals(-0.8f, p.lightDirY, EPS);
        assertEquals(0f, p.specular, EPS);
        assertEquals(88f, p.shininess, EPS);
        assertEquals(0f, p.rimStrength, EPS);
        assertEquals(0f, p.causticIntensity, EPS);
        assertEquals(0f, p.shadowSoftness, EPS);
        assertEquals(1f, p.transmittance, EPS);
        assertEquals(1f, p.backdropScaleX, EPS);
        assertEquals(1f, p.backdropScaleY, EPS);
        assertEquals(0f, p.parallaxScale, EPS);
        assertEquals(0f, p.blurRadiusPx, EPS);
        assertEquals(0f, p.tintA, EPS);
        assertEquals(0f, p.shadowA, EPS);
        assertFalse(p.showNormals);
    }

    @Test
    public void activeValuesMapWithLiquidDockUnits() {
        Map<String, Integer> ints = new HashMap<>();
        ints.put(ConfigSchema.globalGlassKey(GlassParameter.THICKNESS).name(), 185);
        ints.put(ConfigSchema.globalGlassKey(GlassParameter.HEIGHT_TRANSITION_WIDTH).name(), 195);
        ints.put(ConfigSchema.globalGlassKey(GlassParameter.SMIN_SMOOTHING).name(), 23);
        ints.put(ConfigSchema.globalGlassKey(GlassParameter.REFRACTION_INSET).name(), 125);
        ints.put(ConfigSchema.globalGlassKey(GlassParameter.TINT_R).name(), 128);
        ints.put(ConfigSchema.globalGlassKey(GlassParameter.TINT_ALPHA).name(), 64);

        GlassStyleConfig.ResolvedStyle style = GlassStyleConfig.read(new ConfigReader(
                (name, fallback) -> fallback,
                (name, fallback) -> ints.getOrDefault(name, fallback))).global();
        PrismalParams p = GlassPrismalAdapter.toPrismal(style, 2f);

        assertEquals(37f, p.glassThicknessPx, EPS);
        assertEquals(39f, p.heightTransitionWidthPx, EPS);
        assertEquals(2.3f, p.sminSmoothingPx, EPS);
        assertEquals(12.5f, p.refractionInsetPx, EPS);
        assertEquals(128f / 255f, p.tintR, EPS);
        assertEquals(64f / 255f, p.tintA, EPS);
    }

    @Test
    public void highlightFlagsMapInPrismalConstructorOrder() {
        Map<String, Boolean> bools = new HashMap<>();
        bools.put(ConfigSchema.globalHighlightKey(GlassHighlight.SPECULAR).name(), true);
        bools.put(ConfigSchema.globalHighlightKey(GlassHighlight.RIM_LIT).name(), true);
        bools.put(ConfigSchema.globalHighlightKey(GlassHighlight.CAUSTICS).name(), true);
        GlassStyleConfig.ResolvedStyle style = GlassStyleConfig.read(new ConfigReader(
                (name, fallback) -> bools.getOrDefault(name, fallback),
                (name, fallback) -> fallback)).global();

        PrismalHighlightProfile h = GlassPrismalAdapter.toHighlights(style);
        assertFalse(h.skyHaze);
        assertTrue(h.specular);
        assertTrue(h.litRim);
        assertFalse(h.oppositeRim);
        assertFalse(h.cornerRim);
        assertFalse(h.faceSheen);
        assertFalse(h.plainHighlight);
        assertTrue(h.caustics);
        assertFalse(h.pressGlow);
    }
}
