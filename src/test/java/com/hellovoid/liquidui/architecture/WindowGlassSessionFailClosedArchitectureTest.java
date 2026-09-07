package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/** Fail-closed source-order contracts for the Android-backed per-Window session shell. */
public final class WindowGlassSessionFailClosedArchitectureTest {
    private static final Path SESSION = Path.of(
            "src/main/java/com/hellovoid/liquidui/glass/core/WindowGlassSession.java");

    @Test
    public void vendorAuthorityOffRevokesPresentationBeforeAsyncRendererRetirement() throws Exception {
        String source = Files.readString(SESSION);
        int start = source.indexOf("public synchronized void setVendorPassBlurEnabled");
        int end = source.indexOf("public boolean isClosed()", start);
        assertTrue(start >= 0 && end > start);
        String method = source.substring(start, end);

        int generation = method.indexOf(
                "long producerGeneration = presentationState.producerGeneration();");
        int revoke = method.indexOf("presentationState.sourceLost(producerGeneration)");
        int dispatch = method.indexOf("handleRendererPresentation(");
        int retire = method.indexOf("renderer.setVendorPassBlurEnabled(enabled, reason)");

        assertTrue(generation >= 0);
        assertTrue(revoke > generation);
        assertTrue(dispatch >= 0 && dispatch < retire);
        assertTrue(revoke < retire);
    }

    @Test
    public void terminalRendererFailureClosesSessionAfterFeatureCallbacks() throws Exception {
        String source = Files.readString(SESSION);
        int start = source.indexOf("private void handleRendererTerminalFailure");
        int end = source.indexOf("private static Map<String, Long> lifecycleMap", start);
        assertTrue(start >= 0 && end > start);
        String method = source.substring(start, end);

        assertTrue(method.contains("binding.listener.onTerminalFailure(stage, error)"));
        assertTrue(method.contains("close();"));
        assertTrue(method.indexOf("binding.listener.onTerminalFailure(stage, error)")
                < method.lastIndexOf("close();"));
    }
}
