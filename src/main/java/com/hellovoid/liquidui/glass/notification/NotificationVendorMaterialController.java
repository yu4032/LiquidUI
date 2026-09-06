package com.hellovoid.liquidui.glass.notification;

import android.view.View;

import com.hellovoid.liquidui.Api101Bridge;
import com.hellovoid.liquidui.diagnostics.LiquidUiLog;

import java.lang.reflect.Method;

/** Takes over the exact SystemUI material target with View/HWUI-owned PassBlur. */
final class NotificationVendorMaterialController {
    private static final String TAG = "[NotifGlass][Material]";
    // HyperLight's direct View PassBlur route uses the same native radius preference default.
    private static final int SYSTEM_PASS_BLUR_RADIUS_PX = 100;

    private final Method clearMiBackgroundBlendColor;
    private final Method setMiBackgroundBlurMode;
    private final Method setMiViewBlurMode;
    private final Method setMiBackgroundBlurRadius;
    private final Method setPassWindowBlurEnabled;
    private final Method setMiBackgroundBlendColors;
    private final ThreadLocal<Boolean> selfMaterialApply;

    NotificationVendorMaterialController(
            Method clearMiBackgroundBlendColor,
            Method setMiBackgroundBlurMode,
            Method setMiViewBlurMode,
            Method setMiBackgroundBlurRadius,
            Method setPassWindowBlurEnabled,
            Method setMiBackgroundBlendColors,
            ThreadLocal<Boolean> selfMaterialApply) {
        this.clearMiBackgroundBlendColor = accessible(clearMiBackgroundBlendColor);
        this.setMiBackgroundBlurMode = accessible(setMiBackgroundBlurMode);
        this.setMiViewBlurMode = accessible(setMiViewBlurMode);
        this.setMiBackgroundBlurRadius = accessible(setMiBackgroundBlurRadius);
        this.setPassWindowBlurEnabled = accessible(setPassWindowBlurEnabled);
        this.setMiBackgroundBlendColors = accessible(setMiBackgroundBlendColors);
        this.selfMaterialApply = selfMaterialApply;
    }

    void takeOver(View target, int[] blendColors) {
        if (target == null) return;
        try {
            clearNativeMaterial(target);

            // PassBlur ownership now lives on this exact View/RenderNode. No NotificationShade
            // SurfaceControl endpoint is supplied by LiquidUI.
            setPassWindowBlurEnabled.invoke(target, true);
            setMiViewBlurMode.invoke(target, 1);
            setMiBackgroundBlurMode.invoke(target, 1);
            setMiBackgroundBlurRadius.invoke(target, SYSTEM_PASS_BLUR_RADIUS_PX);

            // Preserve SystemUI's material color authority without interpreting the opaque int[].
            if (blendColors != null && blendColors.length > 0) {
                selfMaterialApply.set(Boolean.TRUE);
                try {
                    setMiBackgroundBlendColors.invoke(null, target, blendColors.clone());
                } finally {
                    selfMaterialApply.remove();
                }
            }
            log("view-passblur target=" + target.getClass().getName()
                    + " colors=" + (blendColors == null ? 0 : blendColors.length));
        } catch (Throwable error) {
            safeClear(target);
            log("view-passblur failed target=" + target.getClass().getName() + " error=" + error);
        }
    }

    private void clearNativeMaterial(View target) throws Throwable {
        // HyperLight's exact five-state reset, applied to the SystemUI-dispatched target View.
        clearMiBackgroundBlendColor.invoke(target);
        setMiBackgroundBlurMode.invoke(target, 0);
        setMiViewBlurMode.invoke(target, 0);
        setMiBackgroundBlurRadius.invoke(target, 0);
        setPassWindowBlurEnabled.invoke(target, false);
    }

    private void safeClear(View target) {
        try { clearMiBackgroundBlendColor.invoke(target); } catch (Throwable ignored) {}
        try { setMiBackgroundBlurMode.invoke(target, 0); } catch (Throwable ignored) {}
        try { setMiViewBlurMode.invoke(target, 0); } catch (Throwable ignored) {}
        try { setMiBackgroundBlurRadius.invoke(target, 0); } catch (Throwable ignored) {}
        try { setPassWindowBlurEnabled.invoke(target, false); } catch (Throwable ignored) {}
    }

    private static void log(String message) {
        try {
            Api101Bridge.log(LiquidUiLog.format(TAG + " " + message));
        } catch (Throwable ignored) {
            android.util.Log.i("LiquidUI", "[LUI]" + TAG + " " + message);
        }
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }
}
