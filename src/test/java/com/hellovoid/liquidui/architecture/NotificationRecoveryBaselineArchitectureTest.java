package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Recovery guard for the last device-validated shared Shade baseline.
 *
 * <p>The notification path must stay independent from the later experimental panel/commit
 * authorities until those changes are reintroduced one at a time behind device validation.</p>
 */
public class NotificationRecoveryBaselineArchitectureTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    public void notificationGeometryUsesValidatedStackPredrawBaseline() throws Exception {
        String adapter = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassAdapter.java");
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationSharedGlassHook.java");

        // This is intentionally the last device-validated notification timing contract.
        assertTrue(adapter.contains("installPreDraw(stack)"));
        assertTrue(adapter.contains("refreshScene()"));
        assertFalse(hook.contains("applyCurrentState$1"));
        assertFalse(hook.contains("requestChildrenUpdate"));
        assertFalse(hook.contains("setAnimationRunning"));
    }

    @Test
    public void recoveryBaselineHasNoExperimentalRootBlurLatch() throws Exception {
        assertFalse(Files.exists(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/ShadeRootBlurLatchState.java")));
    }
}
