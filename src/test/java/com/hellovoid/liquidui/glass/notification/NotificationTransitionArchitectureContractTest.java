package com.hellovoid.liquidui.glass.notification;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/** Locks notification glass to the exact HyperOS NSSL committed-ViewState authority. */
public class NotificationTransitionArchitectureContractTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of(relative));
    }

    @Test
    public void notificationGeometryFollowsCommittedNsslViewStateAuthority() throws Exception {
        String adapter = source("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassAdapter.java");
        String hook = source("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationSharedGlassHook.java");
        String runtime = source("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassRuntime.java");

        // HyperOS applies all current ExpandableViewState values inside applyCurrentState$1().
        // LiquidUI must capture after that commit, not when requestChildrenUpdate merely schedules it.
        assertTrue(hook.contains("applyCurrentState$1"));
        assertTrue(runtime.contains("onNativeStateCommitted"));
        assertTrue(adapter.contains("onNativeStateCommitted"));
        assertFalse(hook.contains("getDeclaredMethod(\"requestChildrenUpdate\")"));
        assertFalse(runtime.contains("onChildrenUpdateRequested"));
        assertFalse(adapter.contains("scheduleAfterNativeChildrenUpdate"));

        // Stable commits are direct captures after native state application; they must not install
        // an extra one-shot pre-draw listener. Per-frame pre-draw remains animation-only.
        assertFalse(adapter.contains("childrenUpdateObserver"));
        assertFalse(adapter.contains("childrenUpdateListener"));
        assertTrue(adapter.contains("animationPreDrawListener"));
        assertTrue(adapter.contains("addOnPreDrawListener"));
        assertTrue(adapter.contains("removeOnPreDrawListener"));

        // NotificationPanelViewController expansion fraction is not the notification geometry
        // authority: a fully expanded list can still scroll, reorder, resize, and animate.
        assertFalse(hook.contains("updateExpandedHeight"));
        assertFalse(hook.contains("mExpandedFraction"));
        assertFalse(runtime.contains("onPanelExpansion"));
    }

    @Test
    public void notificationAnimationRefreshIsBoundedByNativeAnimationLifecycle() throws Exception {
        String adapter = source("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassAdapter.java");
        String hook = source("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationSharedGlassHook.java");
        String runtime = source("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassRuntime.java");

        assertTrue(hook.contains("setAnimationRunning"));
        assertTrue(runtime.contains("onAnimationRunning"));
        assertTrue(adapter.contains("setNativeAnimationRunning"));
        assertTrue(adapter.contains("removeAnimationPreDraw"));
        assertTrue(adapter.contains("refreshScene();"));
    }

    @Test
    public void rootLevelGlassRespectsNativeAncestorVisibility() throws Exception {
        String collector = source("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassNodeCollector.java");

        assertTrue(collector.contains("getGlobalVisibleRect"));
        assertTrue(collector.contains("effectiveAncestorAlpha"));
        assertFalse(collector.contains("background.getLocationOnScreen(bgScreen)"));
    }

    @Test
    public void liquidUiSuppressedBackgroundAlphaCannotRevokeItsOwnGlassNode() throws Exception {
        String collector = source("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassNodeCollector.java");
        String material = source("src/main/java/com/hellovoid/liquidui/glass/notification/LegacyNotificationVendorMaterialController.java");

        assertTrue(material.contains("background.setAlpha(0f)"));
        assertTrue(collector.contains("effectiveAncestorAlpha(row)"));
        assertFalse(collector.contains("effectiveAncestorAlpha(background)"));
    }

    @Test
    public void shadeWindowAuthorityDoesNotPrewarmBlurOnOpeningCriticalFrame() throws Exception {
        String authority = source("src/main/java/com/hellovoid/liquidui/glass/notification/ShadeWindowGlassAuthority.java");

        assertFalse(authority.contains("prewarmRootBackdrop"));
        assertFalse(authority.contains("OnPreDrawListener"));
        assertFalse(authority.contains("setMiBackgroundBlurRadius"));
        assertFalse(authority.contains("setMiBackgroundBlurScaleRatio"));
    }
}
