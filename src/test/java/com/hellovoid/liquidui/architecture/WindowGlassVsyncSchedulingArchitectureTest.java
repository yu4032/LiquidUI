package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression contract: GL scene/source drains are display-vsync bounded, never immediate-looped. */
public final class WindowGlassVsyncSchedulingArchitectureTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    public void coordinatorUsesDisplayChoreographerBeforeRenderThreadDrain() throws Exception {
        String coordinator = read(
                "src/main/java/com/hellovoid/liquidui/glass/core/FrameCoordinator.java");
        String renderer = read(
                "src/main/java/com/hellovoid/liquidui/glass/core/WindowGlassRenderer.java");

        assertTrue(coordinator.contains("Choreographer.getInstance()"));
        assertTrue(coordinator.contains("postFrameCallback"));
        assertTrue(renderer.contains("command -> this.renderHandler.post(command)"));
        assertFalse(coordinator.contains("poster.post(this::drainOnce);\n        }\n    }\n\n    private void drainOnce"));
    }
}
