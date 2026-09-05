package com.hellovoid.liquidui.architecture;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

public class Api101BeforeMethodHookBackendArchitectureTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/com/hellovoid/liquidui/xposed/Api101BeforeMethodHookBackend.java");

    @Test
    public void adapterUsesThisObjectThenProceedsAndLogsFirstHitOnlyWhenDiagnosticsEnabled() throws Exception {
        assertTrue(Files.exists(SOURCE));
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("chain.getThisObject()"));
        assertTrue(source.contains("before.before"));
        assertTrue(source.contains("chain.proceed()"));
        assertTrue(source.contains("ExceptionMode.PROTECTIVE"));
        assertTrue(source.contains("compareAndSet(false, true)"));
        assertTrue(source.contains("diagnosticsEnabled"));
    }
}
