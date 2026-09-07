package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SystemUiGlassCoreArchitectureTest {
    @Test
    public void processCoreOwnsTheOnlyWindowRegistry() throws Exception {
        String core = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/core/SystemUiGlassCore.java"));
        String notification = readTree(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification"));

        assertTrue(core.contains("new WindowGlassRegistry"));
        assertTrue(core.contains("sessionFor(Object root, int displayId)"));
        assertFalse(notification.contains("new SystemUiGlassCore"));
        assertFalse(notification.contains("new WindowGlassRegistry"));
    }

    private static String readTree(Path root) throws Exception {
        StringBuilder joined = new StringBuilder();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                joined.append(Files.readString(file));
            }
        }
        return joined.toString();
    }
}
