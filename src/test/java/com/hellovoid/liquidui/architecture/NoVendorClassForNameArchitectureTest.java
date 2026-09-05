package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class NoVendorClassForNameArchitectureTest {
    @Test
    public void productionCodeDoesNotUseClassForName() throws Exception {
        Path root = Path.of("src/main/java/com/hellovoid/liquidui");
        StringBuilder violations = new StringBuilder();
        try (var paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String source = Files.readString(path);
                    if (source.contains("Class.forName(")) {
                        violations.append(path).append('\n');
                    }
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
        }
        assertEquals("", violations.toString());
    }
}
