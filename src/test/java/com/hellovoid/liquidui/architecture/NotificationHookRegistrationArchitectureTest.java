package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class NotificationHookRegistrationArchitectureTest {
    private static final Path MODULE_MAIN =
            Path.of("src/main/java/com/hellovoid/liquidui/ModuleMain.java");

    @Test
    public void moduleMainRegistersRedNotificationHookThroughApi101Backend() throws Exception {
        String source = Files.readString(MODULE_MAIN);
        assertTrue(source.contains("NotificationRedBackgroundHook"));
        assertTrue(source.contains("Api101IntArgumentHookBackend.INSTANCE"));
        assertTrue(source.contains("Api101BooleanArgumentHookBackend.INSTANCE"));
        assertFalse(source.contains("SystemUiHookRegistry.empty()"));
    }
}
