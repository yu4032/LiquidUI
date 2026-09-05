package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class ModuleMainArchitectureTest {
    private static final Path MODULE_MAIN =
            Path.of("src/main/java/com/hellovoid/liquidui/ModuleMain.java");

    @Test
    public void moduleMainScopesBootstrapToSystemUiAndDelegatesAuthority() throws Exception {
        String source = Files.readString(MODULE_MAIN);
        assertTrue(source.contains("\"com.android.systemui\""));
        assertFalse(source.contains("com.miui.home"));
        assertTrue(source.contains("SystemUiRuntimeInfoProvider"));
        assertTrue(source.contains("SystemUiTargetResolver"));
        assertTrue(source.contains("SystemUiHookRegistry"));
        assertTrue(source.indexOf("resolve(runtimeInfo") < source.indexOf("installAll(classLoader"));
    }

    @Test
    public void moduleMainUsesDiagnosticsPreferenceToControlBootstrapDetail() throws Exception {
        String source = Files.readString(MODULE_MAIN);
        assertTrue(source.contains("BootstrapDiagnosticsPolicy.targetResolutionMessage"));
        assertTrue(source.contains("BootstrapDiagnosticsPolicy.hookRegistryMessage"));
        assertTrue(source.contains("config.diagnosticsEnabled()"));
    }

    @Test
    public void moduleMainDoesNotResolveVendorClassesItself() throws Exception {
        String source = Files.readString(MODULE_MAIN);
        assertFalse(source.contains("loadClass("));
        assertFalse(source.contains("getDeclaredMethod("));
        assertFalse(source.contains("getDeclaredField("));
    }
}
