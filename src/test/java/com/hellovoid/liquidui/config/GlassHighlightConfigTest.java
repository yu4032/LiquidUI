package com.hellovoid.liquidui.config;

import com.hellovoid.liquidui.glass.core.GlassMaterialProfile;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GlassHighlightConfigTest {
    @Test
    public void phaseZeroDefaultsKeepEveryHighlightDisabled() {
        GlassStyleConfig.ResolvedStyle style =
                GlassStyleConfig.defaults().resolved(GlassMaterialProfile.CARD);
        for (GlassHighlight highlight : GlassHighlight.values()) {
            assertFalse(style.highlight(highlight));
        }
    }

    @Test
    public void disabledProfilesInheritGlobalHighlightFlags() {
        Map<String, Boolean> booleans = new HashMap<>();
        booleans.put(ConfigSchema.globalHighlightKey(GlassHighlight.SPECULAR).name(), true);
        booleans.put(ConfigSchema.globalHighlightKey(GlassHighlight.CAUSTICS).name(), true);

        GlassStyleConfig config = GlassStyleConfig.read(reader(booleans));
        GlassStyleConfig.ResolvedStyle tile = config.resolved(GlassMaterialProfile.TILE);

        assertTrue(tile.highlight(GlassHighlight.SPECULAR));
        assertTrue(tile.highlight(GlassHighlight.CAUSTICS));
        assertFalse(tile.highlight(GlassHighlight.RIM_LIT));
    }

    @Test
    public void enabledProfileUsesItsOwnCompleteHighlightSet() {
        Map<String, Boolean> booleans = new HashMap<>();
        booleans.put(ConfigSchema.globalHighlightKey(GlassHighlight.SPECULAR).name(), true);
        booleans.put(ConfigSchema.profileOverrideKey(GlassMaterialProfile.CARD).name(), true);
        booleans.put(
                ConfigSchema.profileHighlightKey(
                        GlassMaterialProfile.CARD, GlassHighlight.CORNER_RIM).name(),
                true);

        GlassStyleConfig config = GlassStyleConfig.read(reader(booleans));
        GlassStyleConfig.ResolvedStyle card = config.resolved(GlassMaterialProfile.CARD);

        assertTrue(card.highlight(GlassHighlight.CORNER_RIM));
        assertFalse(card.highlight(GlassHighlight.SPECULAR));
    }

    private static ConfigReader reader(Map<String, Boolean> booleans) {
        return new ConfigReader(
                (name, fallback) -> booleans.getOrDefault(name, fallback),
                (name, fallback) -> fallback);
    }
}
