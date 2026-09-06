package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class Api101AfterMethodHookBackendArchitectureTest {
    @Test
    public void afterCallbackWrapsProceedInFinallySoScopeClearsOnThrows() throws Exception {
        Path path = Path.of("src/main/java/com/hellovoid/liquidui/xposed/Api101AfterMethodHookBackend.java");
        assertTrue(Files.exists(path));
        String source = Files.readString(path);
        assertTrue(source.contains("try"));
        assertTrue(source.contains("return chain.proceed()"));
        assertTrue(source.contains("finally"));
        assertTrue(source.contains("after.after(thisObject, args)"));
        assertTrue(source.contains("ExceptionMode.PROTECTIVE"));
    }
}
