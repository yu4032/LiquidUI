package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class SettingsShellArchitectureTest {
    @Test
    public void applicationBridgeMirrorsTheCompleteTypedSchema() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/LiquidUiApp.java"));
        assertTrue(source.contains("XposedServiceHelper.registerListener"));
        assertTrue(source.contains("ConfigSchema.all()"));
        assertTrue(source.contains("ConfigKey.Kind.BOOLEAN"));
        assertTrue(source.contains("ConfigKey.Kind.INT"));
        assertTrue(source.contains("putBoolean"));
        assertTrue(source.contains("putInt"));
        assertFalse(source.contains("editor.clear()"));
        assertFalse(source.contains("LiquidDock"));
    }

    @Test
    public void settingsUiExposesCompleteSchemaDrivenGlassEditor() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquidui/SettingsActivity.kt"));
        assertTrue(source.contains("ConfigSchema.ENABLED"));
        assertTrue(source.contains("ConfigSchema.DIAGNOSTICS_ENABLED"));
        assertTrue(source.contains("ConfigSchema.NOTIFICATION_GLASS_ENABLED"));
        assertTrue(source.contains("systemui-001"));

        assertTrue(source.contains("GlassParameter.values()"));
        assertTrue(source.contains("GlassHighlight.values()"));
        assertTrue(source.contains("GlassMaterialProfile.values()"));
        assertTrue(source.contains("ConfigSchema.globalGlassKey"));
        assertTrue(source.contains("ConfigSchema.globalHighlightKey"));
        assertTrue(source.contains("ConfigSchema.profileOverrideKey"));
        assertTrue(source.contains("ConfigSchema.profileGlassKey"));
        assertTrue(source.contains("ConfigSchema.profileHighlightKey"));
        assertTrue(source.contains("ConfigSchema.SAMPLING_AUTO_ENABLED"));
        assertTrue(source.contains("ConfigSchema.SAMPLING_EXTRA_TOP"));
        assertTrue(source.contains("ConfigSchema.SAMPLING_EXTRA_BOTTOM"));
        assertTrue(source.contains("ConfigSchema.SAMPLING_EXTRA_LEFT"));
        assertTrue(source.contains("ConfigSchema.SAMPLING_EXTRA_RIGHT"));

        assertTrue(source.contains("SliderPreference("));
        assertTrue(source.contains("ArrowPreference("));
        assertTrue(source.contains("SwitchPreference("));
        assertTrue(source.contains("resetProfile"));
        assertTrue(source.contains("resetGlobal"));
        assertTrue(source.contains("GLOBAL"));
        assertTrue(source.contains("CARD"));
        assertTrue(source.contains("TILE"));
        assertTrue(source.contains("SLIDER"));
        assertTrue(source.contains("PANEL"));
        assertTrue(source.contains("FLOATING"));
        assertFalse(source.contains("LiquidDock"));
        assertFalse(source.contains("Launcher"));
    }
}
