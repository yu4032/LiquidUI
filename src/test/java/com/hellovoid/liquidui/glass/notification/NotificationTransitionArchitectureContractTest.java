package com.hellovoid.liquidui.glass.notification;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/** Locks notification glass to the exact HyperOS NSSL geometry-update authority. */
public class NotificationTransitionArchitectureContractTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of(relative));
    }

    @Test
    public void notificationGeometryFollowsNativeNsslChildrenUpdateAuthority() throws Exception {
        String adapter = source("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassAdapter.java");
        String hook = source("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationSharedGlassHook.java");
        String runtime = source("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassRuntime.java");

        // Exact 16.03.251211.r decompilation: setOwnScrollY(), setExpandedHeight(), and
        // onChildHeightChanged() converge on NotificationStackScrollLayout.requestChildrenUpdate().
        // That method installs the native mChildrenUpdater for the next pre-draw frame.
        assertTrue(hook.contains("requestChildrenUpdate"));
        assertTrue(runtime.contains("onChildrenUpdateRequested"));
        assertTrue(adapter.contains("scheduleAfterNativeChildrenUpdate"));

        // LiquidUI may attach a coalesced one-shot listener after the native updater; it must not
        // restore the old permanent notification pre-draw poll installed from the adapter ctor.
        assertTrue(adapter.contains("OnPreDrawListener"));
        assertTrue(adapter.contains("addOnPreDrawListener"));
        assertTrue(adapter.contains("removeOnPreDrawListener"));
        assertFalse(adapter.contains("installPreDraw(stack)"));

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

        // Exact NSSL exposes setAnimationRunning(boolean) around its property-animation phase.
        // Per-frame glass geometry refresh is allowed only while this native lifecycle is active.
        assertTrue(hook.contains("setAnimationRunning"));
        assertTrue(runtime.contains("onAnimationRunning"));
        assertTrue(adapter.contains("setNativeAnimationRunning"));
        assertTrue(adapter.contains("removeAnimationPreDraw"));
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
