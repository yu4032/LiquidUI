package com.hellovoid.liquidui.glass.notification;

/**
 * Pure rewrite policy for HyperOS Shade background material.
 *
 * <p>The Window-level MIUI blur remains vendor-owned, but its radius is latched to one stable
 * value while Shade is visible. HyperOS otherwise rewrites the root blur radius every drag frame,
 * which forces repeated compositor blur reconfiguration and adds presentation latency. Page-local
 * notification/control-center backdrops remain neutralized because they sit above LiquidUI's
 * shared Shade renderer.</p>
 */
final class NotificationShadeBlurPolicy {
    private NotificationShadeBlurPolicy() {}

    /**
     * Keep one stable full-strength root blur for the lifetime of a visible Shade gesture.
     * Blend/background alpha can still animate independently; the expensive blur kernel does not.
     */
    static float rootBlurRatio(float requested) {
        return requested > 0f ? 1f : 0f;
    }

    /** AOSP window blur is HyperOS's fallback when MIUI blur is unavailable; preserve that path. */
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
