package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/** Active shared Shade hook must preserve only the Window-level backdrop material. */
public class ShadeRootBackdropArchitectureTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    public void sharedHookPreservesRootAndNeutralizesOnlyPageLocalBackdrops() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationSharedGlassHook.java");

        assertTrue(hook.contains("SharedNotificationContainer"));
        assertTrue(hook.contains("NotificationShadeBlurPolicy.rootBlurRatio(requested)"));
        assertTrue(hook.contains("NotificationShadeBlurPolicy.pageBlurRatio(requested)"));
        assertTrue(hook.contains("NotificationShadeBlurPolicy.rootBlendEnabled(requested)"));
        assertTrue(hook.contains("NotificationShadeBlurPolicy.pageBlendEnabled(requested)"));
        assertTrue(hook.contains("NotificationShadeBlurPolicy.rootWindowBlurRadius(requested)"));
        assertTrue(hook.contains("isRootShadeBlendTarget"));
        assertTrue(hook.contains("isPageShadeBlendTarget"));
        assertFalse(hook.contains("NotificationShadeBlurPolicy.blurRadius(true, requested)"));
    }

    @Test
    public void pageLocalSuppressionStillOwnsOnlyNotificationAndControlCenterBranches() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationSharedGlassHook.java");

        assertTrue(hook.contains("notificationPanelClass.isInstance(value)"));
        assertTrue(hook.contains("sharedNotificationContainerClass.isInstance(parent)"));
        assertTrue(hook.contains("isRootControlCenterContainer"));
        assertFalse(hook.contains("return shadeWindowClass.isInstance(value)\n                || notificationPanelClass.isInstance(value)"));
    }
}
