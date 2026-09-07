package com.hellovoid.liquidui.glass.core;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

/** Transparent output container owned by one Window glass session. It owns no GPU resources. */
final class GlassHostView extends FrameLayout {
    private Runnable onDetached;

    GlassHostView(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setOnDetached(Runnable callback) {
        onDetached = callback;
    }

    @Override
    protected void onDetachedFromWindow() {
        try {
            Runnable callback = onDetached;
            if (callback != null) callback.run();
        } finally {
            super.onDetachedFromWindow();
        }
    }
}
