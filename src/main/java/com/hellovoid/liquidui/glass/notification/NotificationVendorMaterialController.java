package com.hellovoid.liquidui.glass.notification;

import android.view.View;

import com.hellovoid.liquidui.Api101Bridge;
import com.hellovoid.liquidui.diagnostics.LiquidUiLog;

/**
 * Observes SystemUI's exact notification element material target without mutating its native blur
 * ownership. The decompiled target SystemUI proves ordinary notification rows use mBackgroundNormal
 * as a view-blur/blend consumer; background blur mode/radius/pass-window blur belong to separate
 * container paths and must not be invented here.
 */
final class NotificationVendorMaterialController {
    private static final String TAG = "[NotifGlass][Material]";

    void observeSystemMaterial(View target, Object row, int[] blendColors, float blendRatio) {
        if (target == null) return;
        log("system-material target=" + target.getClass().getName()
                + " row=" + (row == null ? "<none>" : row.getClass().getName())
                + " colors=" + (blendColors == null ? 0 : blendColors.length)
                + " ratio=" + blendRatio);
    }

    private static void log(String message) {
        String formatted = LiquidUiLog.format(TAG + " " + message);
        // libxposed module logs are not guaranteed to appear in adb logcat. Mirror notification
        // authority diagnostics directly so device-side validation has an unambiguous channel.
        android.util.Log.i("LiquidUI", formatted);
        try {
            Api101Bridge.log(formatted);
        } catch (Throwable ignored) {
            // The direct logcat record above is the fallback authority for this diagnostic path.
        }
    }
}
