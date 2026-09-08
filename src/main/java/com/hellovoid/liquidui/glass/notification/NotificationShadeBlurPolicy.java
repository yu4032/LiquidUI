package com.hellovoid.liquidui.glass.notification;

/**
 * Pure rewrite policy for HyperOS Shade background material.
 *
 * <p>The Window-level combined backdrop stays vendor-owned and is preserved. Only page-local
 * notification/control-center backdrops are neutralized because they sit above LiquidUI's shared
 * Shade renderer and would otherwise obscure bounded glass nodes.</p>
 */
final class NotificationShadeBlurPolicy {
    private NotificationShadeBlurPolicy() {}

    static float rootBlurRatio(float requested) {
        return requested;
    }

    static int rootWindowBlurRadius(int requested) {
        return requested;
    }

    static boolean rootBlendEnabled(boolean requested) {
        return requested;
    }

    static float pageBlurRatio(float requested) {
        return 0f;
    }

    static boolean pageBlendEnabled(boolean requested) {
        return false;
    }

    // Legacy policy kept for the inactive pre-shared implementation contracts. The production
    // NotificationSharedGlassHook must use the explicit root/page APIs above.
    static float blurRatio(boolean glassActive, float requested) {
        return glassActive ? 0f : requested;
    }

    static int blurRadius(boolean glassActive, int requested) {
        return glassActive ? 0 : requested;
    }

    static boolean enabled(boolean glassActive, boolean requested) {
        return glassActive ? false : requested;
    }
}
