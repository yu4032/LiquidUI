package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class Api101IntArgumentHookBackendArchitectureTest {
    private static final Path BACKEND = Path.of(
            "src/main/java/com/hellovoid/liquidui/xposed/Api101IntArgumentHookBackend.java");

    @Test
    public void adapterUsesApi101InterceptorChainAndReturnsUnhookRegistration() throws Exception {
        assertTrue(Files.exists(BACKEND));
        String source = Files.readString(BACKEND);
        assertTrue(source.contains("Api101Bridge.module()"));
        assertTrue(source.contains(".hook(method)"));
        assertTrue(source.contains("setPriority(priority)"));
        assertTrue(source.contains("ExceptionMode.PROTECTIVE"));
        assertTrue(source.contains("chain.getArgs().toArray"));
        assertTrue(source.contains("rewriter.applyAsInt"));
        assertTrue(source.contains("chain.proceed(args)"));
        assertTrue(source.contains("handle::unhook"));
    }
}
