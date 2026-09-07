package com.hellovoid.liquidui.architecture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public final class ViewRootSurfaceObserverArchitectureTest {
    @Test
    public void rootObserverOnlySignalsLifecycleAndDefersReadyWork() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/core/ViewRootSurfaceObserver.java"));

        assertTrue(source.contains("addSurfaceChangedCallback"));
        assertTrue(source.contains("surfaceDestroyed"));
        assertTrue(source.contains("surfaceCreated"));
        assertTrue(source.contains("surfaceReplaced"));
        assertTrue(source.contains("host.post"));
        assertFalse(source.contains("SetPassBlurSurface"));
        assertFalse(source.contains("Notification"));
    }
}
