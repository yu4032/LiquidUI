package com.hellovoid.liquidui.config;

import com.hellovoid.liquidui.glass.core.GlassMaterialProfile;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GlassStyleConfigTest {
    private static final float EPS = 0.0001f;

    @Test
    public void defaultsPreservePhaseZeroOpticsAndSamplingMode() {
        GlassStyleConfig config = GlassStyleConfig.defaults();
        GlassStyleConfig.ResolvedStyle card = config.resolved(GlassMaterialProfile.CARD);

        assertEquals(1.55f, card.value(GlassParameter.IOR), EPS);
        assertEquals(22f, card.value(GlassParameter.THICKNESS), EPS);
        assertEquals(1.35f, card.value(GlassParameter.NORMAL_STRENGTH), EPS);
        assertEquals(1.70f, card.value(GlassParameter.DISPLACEMENT_SCALE), EPS);
        assertEquals(20f, card.value(GlassParameter.HEIGHT_TRANSITION_WIDTH), EPS);
        assertEquals(1.8f, card.value(GlassParameter.SMIN_SMOOTHING), EPS);
        assertEquals(20f, card.value(GlassParameter.REFRACTION_INSET), EPS);
        assertEquals(4f, card.value(GlassParameter.EDGE_REFRACTION_FALLOFF), EPS);
        assertEquals(1.35f, card.value(GlassParameter.DOME), EPS);
        assertEquals(1f, card.value(GlassParameter.FRESNEL_REFLECT), EPS);
        assertEquals(2.20f, card.value(GlassParameter.LENS_REFRACTION), EPS);
        assertEquals(1f, card.value(GlassParameter.LENS_DEPTH), EPS);
        assertEquals(42f, card.value(GlassParameter.CHROMATIC), EPS);
        assertEquals(1.30f, card.value(GlassParameter.VIBRANCY), EPS);
        assertEquals(1f, card.value(GlassParameter.BRIGHTNESS), EPS);
        assertEquals(0f, card.value(GlassParameter.BLUR), EPS);
        assertEquals(0f, card.value(GlassParameter.SPECULAR_STRENGTH), EPS);
        assertEquals(0f, card.value(GlassParameter.RIM_LIGHT), EPS);
        assertEquals(0f, card.value(GlassParameter.CAUSTICS), EPS);
        assertEquals(0f, card.value(GlassParameter.TINT_ALPHA), EPS);
        assertEquals(0f, card.value(GlassParameter.SHADOW_ALPHA), EPS);
        assertEquals(0f, card.value(GlassParameter.SHOW_NORMALS), EPS);

        assertFalse(config.samplingAutoEnabled());
        assertEquals(0L, config.samplingExtraTopPx());
        assertEquals(0L, config.samplingExtraBottomPx());
        assertEquals(0L, config.samplingExtraLeftPx());
        assertEquals(0L, config.samplingExtraRightPx());
    }

    @Test
    public void disabledProfilesInheritGlobalAndEnabledProfilesAreIndependent() {
        Map<String, Boolean> booleans = new HashMap<>();
        Map<String, Integer> integers = new HashMap<>();
        integers.put(ConfigSchema.globalGlassKey(GlassParameter.IOR).name(), 165);
        booleans.put(ConfigSchema.profileOverrideKey(GlassMaterialProfile.CARD).name(), true);
        integers.put(ConfigSchema.profileGlassKey(GlassMaterialProfile.CARD, GlassParameter.IOR).name(), 180);

        GlassStyleConfig config = GlassStyleConfig.read(reader(booleans, integers));

        assertEquals(1.80f,
                config.resolved(GlassMaterialProfile.CARD).value(GlassParameter.IOR), EPS);
        assertEquals(1.65f,
                config.resolved(GlassMaterialProfile.TILE).value(GlassParameter.IOR), EPS);
        assertTrue(config.overrides(GlassMaterialProfile.CARD));
        assertFalse(config.overrides(GlassMaterialProfile.TILE));
    }

    @Test
    public void numericReadsClampBeforeNormalization() {
        Map<String, Integer> integers = new HashMap<>();
        integers.put(ConfigSchema.globalGlassKey(GlassParameter.IOR).name(), 9999);
        integers.put(ConfigSchema.globalGlassKey(GlassParameter.LIGHT_DIR_X).name(), -9999);
        integers.put(ConfigSchema.SAMPLING_EXTRA_TOP.name(), 9999);

        GlassStyleConfig config = GlassStyleConfig.read(reader(Map.of(), integers));
        GlassStyleConfig.ResolvedStyle global = config.resolved(GlassMaterialProfile.PANEL);

        assertEquals(2.0f, global.value(GlassParameter.IOR), EPS);
        assertEquals(-2.0f, global.value(GlassParameter.LIGHT_DIR_X), EPS);
        assertEquals(256L, config.samplingExtraTopPx());
    }

    @Test
    public void samplingAuthorityIsGlobalNotPerProfile() {
        Map<String, Boolean> booleans = new HashMap<>();
        Map<String, Integer> integers = new HashMap<>();
        booleans.put(ConfigSchema.SAMPLING_AUTO_ENABLED.name(), true);
        integers.put(ConfigSchema.SAMPLING_EXTRA_TOP.name(), 17);
        integers.put(ConfigSchema.SAMPLING_EXTRA_BOTTOM.name(), -9);
        integers.put(ConfigSchema.SAMPLING_EXTRA_LEFT.name(), 5);
        integers.put(ConfigSchema.SAMPLING_EXTRA_RIGHT.name(), 11);

        GlassStyleConfig config = GlassStyleConfig.read(reader(booleans, integers));

        assertTrue(config.samplingAutoEnabled());
        assertEquals(17L, config.samplingExtraTopPx());
        assertEquals(-9L, config.samplingExtraBottomPx());
        assertEquals(5L, config.samplingExtraLeftPx());
        assertEquals(11L, config.samplingExtraRightPx());
    }

    private static ConfigReader reader(
            Map<String, Boolean> booleans,
            Map<String, Integer> integers) {
        return new ConfigReader(
                (name, fallback) -> booleans.getOrDefault(name, fallback),
                (name, fallback) -> integers.getOrDefault(name, fallback));
    }
}
