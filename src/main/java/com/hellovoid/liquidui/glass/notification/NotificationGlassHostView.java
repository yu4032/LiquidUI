package com.hellovoid.liquidui.glass.notification;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

/** Transparent shared output layer inserted immediately below NotificationStackScrollLayout. */
final class NotificationGlassHostView extends FrameLayout {
    private Runnable onDetached;

    NotificationGlassHostView(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setOnDetached(Runnable callback) { onDetached = callback; }

    @Override protected void onDetachedFromWindow() {
        try {
            Runnable callback = onDetached;
            if (callback != null) callback.run();
        } finally {
            super.onDetachedFromWindow();
        }
    }
}
