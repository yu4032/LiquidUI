package com.hellovoid.liquidui.glass.notification;

/** Pure rewrite policy: active notification glass owns blur, otherwise preserve HyperOS values. */
final class NotificationShadeBlurPolicy {
    private NotificationShadeBlurPolicy() {}

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
