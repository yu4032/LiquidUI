package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class Api101BooleanArgumentHookBackendArchitectureTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/com/hellovoid/liquidui/xposed/Api101BooleanArgumentHookBackend.java");

    @Test
    public void api101AdapterRewritesBooleanArgumentThroughProceedCopy() throws Exception {
        assertTrue(Files.exists(SOURCE));
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("chain.getArgs().toArray"));
        assertTrue(source.contains("rewriter.applyAsBoolean"));
        assertTrue(source.contains("chain.proceed(args)"));
        assertTrue(source.contains("ExceptionMode.PROTECTIVE"));
    }
}
