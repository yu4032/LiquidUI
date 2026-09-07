package com.hellovoid.liquidui.glass.core;

import com.hellovoid.liquidui.config.ConfigReader;
import com.hellovoid.liquidui.config.GlassStyleConfig;

import org.junit.Test;

import static org.junit.Assert.*;

public final class MaterialProfileRegistryTest {
    @Test
    public void defaultsPreservePhaseZeroMaterialStateAtVersionOne() {
        MaterialProfileRegistry registry = new MaterialProfileRegistry(2f);

        MaterialProfileRegistry.MaterialState card =
                registry.stateFor(GlassMaterialProfile.CARD);
        assertEquals(1L, registry.profileVersion());
        assertEquals(1.55f, card.params().ior, 0.0001f);
        assertEquals(44f, card.params().glassThicknessPx, 0.0001f);
        assertEquals(42f, card.params().chromaticAberration, 0.0001f);
        assertEquals(0f, card.params().parallaxScale, 0.0001f);
        assertEquals(0f, card.params().blurRadiusPx, 0.0001f);
        assertFalse(card.highlights().skyHaze);
        assertFalse(card.highlights().specular);
        assertFalse(card.highlights().caustics);
    }

    @Test
    public void updateResolvesIndependentProfileParamsAndHighlightsInOneRegistry() {
        GlassStyleConfig style = GlassStyleConfig.read(new ConfigReader(
                (name, fallback) -> {
                    if (name.equals("glass_card_override")) return true;
                    if (name.equals("glass_global_highlight_specular")) return true;
                    if (name.equals("glass_card_highlight_caustics")) return true;
                    return fallback;
                },
                (name, fallback) -> {
                    if (name.equals("glass_global_brightness")) return 125;
                    if (name.equals("glass_card_brightness")) return 80;
                    return fallback;
                }));
        MaterialProfileRegistry registry = new MaterialProfileRegistry(2f);

        assertTrue(registry.update(style, 2L));

        MaterialProfileRegistry.MaterialState card =
                registry.stateFor(GlassMaterialProfile.CARD);
        MaterialProfileRegistry.MaterialState tile =
                registry.stateFor(GlassMaterialProfile.TILE);
        assertEquals(2L, registry.profileVersion());
        assertEquals(0.80f, card.params().brightness, 0.0001f);
        assertEquals(1.25f, tile.params().brightness, 0.0001f);
        assertFalse(card.highlights().specular);
        assertTrue(card.highlights().caustics);
        assertTrue(tile.highlights().specular);
        assertFalse(tile.highlights().caustics);
    }

    @Test
    public void staleOrDuplicateVersionsCannotOverwriteNewerStyles() {
        GlassStyleConfig newer = GlassStyleConfig.read(new ConfigReader(
                (name, fallback) -> fallback,
                (name, fallback) -> name.equals("glass_global_brightness") ? 140 : fallback));
        GlassStyleConfig stale = GlassStyleConfig.read(new ConfigReader(
                (name, fallback) -> fallback,
                (name, fallback) -> name.equals("glass_global_brightness") ? 70 : fallback));
        MaterialProfileRegistry registry = new MaterialProfileRegistry(1f);

        assertTrue(registry.update(newer, 5L));
        assertFalse(registry.update(stale, 4L));
        assertFalse(registry.update(stale, 5L));

        assertEquals(5L, registry.profileVersion());
        assertEquals(1.40f,
                registry.stateFor(GlassMaterialProfile.PANEL).params().brightness,
                0.0001f);
    }
}
