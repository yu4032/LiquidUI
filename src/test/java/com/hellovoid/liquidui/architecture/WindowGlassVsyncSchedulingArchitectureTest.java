package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression contract: scene-only requests cannot form an immediate render self-loop. */
public final class WindowGlassVsyncSchedulingArchitectureTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    public void coordinatorOnlySelfSchedulesForUnconsumedSourceFrames() throws Exception {
        String coordinator = read(
                "src/main/java/com/hellovoid/liquidui/glass/core/FrameCoordinator.java");

        assertTrue(coordinator.contains("if (!cancelled && sourcePending)"));
        assertFalse(coordinator.contains("if (!cancelled && (sourcePending || scenePending))"));
    }
}
