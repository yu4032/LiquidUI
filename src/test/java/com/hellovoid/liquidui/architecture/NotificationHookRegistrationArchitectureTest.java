package com.hellovoid.liquidui.architecture;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

public class NotificationHookRegistrationArchitectureTest {
    private static final Path MODULE_MAIN =
            Path.of("src/main/java/com/hellovoid/liquidui/ModuleMain.java");

    @Test
    public void moduleMainRegistersMaterialAuthorityHookWithBeforeAfterAndRewriteBackends() throws Exception {
        String source = Files.readString(MODULE_MAIN);
        assertTrue(source.contains("NotificationLiquidGlassHook"));
        assertTrue(source.contains("Api101BeforeMethodHookBackend"));
        assertTrue(source.contains("Api101AfterMethodHookBackend"));
        assertTrue(source.contains("Api101ArgumentRewriteHookBackend"));
        assertTrue(source.contains("config.diagnosticsEnabled()"));
        assertTrue(source.contains("config.notificationGlassEnabled()"));
        assertFalse(source.contains("SystemUiHookRegistry.empty()"));
    }
}
