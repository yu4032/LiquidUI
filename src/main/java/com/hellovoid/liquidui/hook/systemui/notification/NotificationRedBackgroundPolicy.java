package com.hellovoid.liquidui.hook.systemui.notification;

public final class NotificationRedBackgroundPolicy {
    public static final int OPAQUE_RED = 0xFFFF0000;

    private NotificationRedBackgroundPolicy() {}

    public static int rewriteTint(int originalTint) {
        return OPAQUE_RED;
    }
}
