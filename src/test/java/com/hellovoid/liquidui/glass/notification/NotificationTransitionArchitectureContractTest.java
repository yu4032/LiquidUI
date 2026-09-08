package com.hellovoid.liquidui.glass.notification;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/** Locks notification glass to HyperOS transition authority instead of generic pre-draw polling. */
public class NotificationTransitionArchitectureContractTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of(relative));
    }

    @Test
    public void notificationGeometryIsDrivenByNativeExpansionTicksNotPreDrawPolling() throws Exception {
        String adapter = source("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassAdapter.java");
        String hook = source("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationSharedGlassHook.java");
        String runtime = source("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassRuntime.java");

        assertFalse(adapter.contains("OnPreDrawListener"));
        assertFalse(adapter.contains("addOnPreDrawListener"));
        assertTrue(hook.contains("updateExpandedHeight"));
        assertTrue(hook.contains("mExpandedFraction"));
        assertTrue(hook.contains("onPanelExpansion"));
        assertTrue(runtime.contains("onPanelExpansion"));
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

        // Shared presentation intentionally hides only the native material target with alpha=0.
        assertTrue(material.contains("background.setAlpha(0f)"));

        // Effective visibility must therefore start at the row/page hierarchy, not at the
        // background View that LiquidUI itself suppresses after authorization.
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
