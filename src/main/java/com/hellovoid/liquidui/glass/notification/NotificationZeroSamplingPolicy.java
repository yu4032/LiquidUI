package com.hellovoid.liquidui.glass.notification;

import java.lang.reflect.Field;

/**
 * Notification-only policy that cancels every automatic/compatibility sampling guard.
 *
 * The shared renderer covers the whole NotificationShade window, so sampling beyond that visible
 * host is neither necessary nor desirable. NotificationPassBlurTextureView combines each signed
 * user extra with its automatic guard and clamps the result at zero; Integer.MIN_VALUE therefore
 * deterministically collapses the total inset to zero for every realistic texture size.
 */
final class NotificationZeroSamplingPolicy {
    private static final String[] EXTRA_FIELDS = {
            "topSamplingExtraPx",
            "bottomSamplingExtraPx",
            "leftSamplingExtraPx",
            "rightSamplingExtraPx"
    };

    private NotificationZeroSamplingPolicy() {}

    static void apply(NotificationPassBlurTextureView renderer) {
        if (renderer == null) return;
        try {
            for (String name : EXTRA_FIELDS) {
                Field field = NotificationPassBlurTextureView.class.getDeclaredField(name);
                field.setAccessible(true);
                field.setInt(renderer, Integer.MIN_VALUE);
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("notification zero-sampling contract unavailable", error);
        }
    }
}
