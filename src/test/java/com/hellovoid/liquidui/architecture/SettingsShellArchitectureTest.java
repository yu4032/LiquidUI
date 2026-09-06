package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class SettingsShellArchitectureTest {
    @Test
    public void applicationBridgeMirrorsOnlySchemaOwnedBootstrapKeys() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/LiquidUiApp.java"));
        assertTrue(source.contains("XposedServiceHelper.registerListener"));
        assertTrue(source.contains("ConfigSchema.ENABLED"));
        assertTrue(source.contains("ConfigSchema.DIAGNOSTICS_ENABLED"));
        assertTrue(source.contains("ConfigSchema.NOTIFICATION_GLASS_ENABLED"));
        assertTrue(source.contains("ConfigSchema.NOTIFICATION_DEBUG_FORCE_RED_BACKGROUND"));
        assertFalse(source.contains("editor.clear()"));
        assertFalse(source.contains("LiquidDock"));
    }

    @Test
    public void settingsUiContainsBootstrapAndNotificationGlassControls() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquidui/SettingsActivity.kt"));
        assertTrue(source.contains("ConfigSchema.ENABLED"));
        assertTrue(source.contains("ConfigSchema.DIAGNOSTICS_ENABLED"));
        assertTrue(source.contains("ConfigSchema.NOTIFICATION_GLASS_ENABLED"));
        assertTrue(source.contains("ConfigSchema.NOTIFICATION_DEBUG_FORCE_RED_BACKGROUND"));
        assertTrue(source.contains("⚠ 调试：强制红色通知背景"));
        assertTrue(source.contains("关闭通知液态玻璃"));
        assertTrue(source.contains("systemui-001"));
        assertTrue(source.contains("SwitchPreference("));
        assertFalse(source.contains("Dock"));
        assertFalse(source.contains("Launcher"));
    }
}
