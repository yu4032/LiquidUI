package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class BootstrapSourceArchitectureTest {
    @Test
    public void frameworkVersionReaderUsesFrameworkAuthorityWithoutClassForName() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/target/FrameworkPackageVersionReader.java"));
        assertTrue(source.contains("android.app.AppGlobals"));
        assertTrue(source.contains("getPackageInfo"));
        assertTrue(source.contains("getLongVersionCode"));
        assertTrue(source.contains("versionName"));
        assertFalse(source.contains("Class.forName("));
    }

    @Test
    public void apiBridgeRetainsRemotePreferencesAndLiquidUiLogTag() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/Api101Bridge.java"));
        assertTrue(source.contains("getRemotePreferences"));
        assertTrue(source.contains("\"LiquidUI\""));
        assertFalse(source.contains("LiquidDock"));
    }
}
