package com.hellovoid.liquidui.glass.notification;

import android.content.res.Configuration;
import android.graphics.Point;
import android.view.View;

import com.hellovoid.liquidui.Api101Bridge;
import com.hellovoid.liquidui.diagnostics.LiquidUiLog;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** Applies LiquidUI material and native PassBlur backdrop to the exact notification target. */
final class NotificationVendorMaterialController {
    private static final String TAG = "[NotifGlass][Material]";
    private static final float CARD_PASS_BLUR_RADIUS_DP = 24.0f;

    private static final int[] LIGHT_MATERIAL_COLORS = {
            -428575628, -1722658222, 869388753
    };
    private static final int[] LIGHT_MATERIAL_MODES = {15, 18, 3};
    private static final int[] DARK_MATERIAL_COLORS = {
            -942879540, 777146962
    };
    private static final int[] DARK_MATERIAL_MODES = {19, 3};

    private static final float[] LIGHT_BLOOM_STROKE = {
            0.8f, 180.0f, 1.0f, 1.0f, 1.0f, 0.05f, 2.6f,
            0.5f, 0.5f, -0.5f, 1.0f, 1.0f, 1.0f, 0.6f,
            0.5f, 0.95f, -0.5f, 1.0f, 1.0f, 1.0f, 0.35f
    };
    private static final float[] DARK_BLOOM_STROKE = {
            0.8f, 180.0f, 1.0f, 1.0f, 1.0f, 0.08f, 2.3f,
            0.5f, 0.5f, -0.5f, 1.0f, 1.0f, 1.0f, 0.6f,
            0.5f, 0.95f, -0.36f, 1.0f, 1.0f, 1.0f, 0.25f
    };

    private final Method setMixEffectEnabled;
    private final Method setMiViewBlurMode;
    private final Method clearMiBackgroundBlendColor;
    private final Method setMiBackgroundBlendColors;
    private final Method setMiBloomStroke;
    private final Method setMiBackgroundBlurMode;
    private final Method setMiBackgroundBlurRadius;
    private final Method setPassWindowBlurEnabled;
    private final AtomicBoolean passBlurLogged = new AtomicBoolean();

    NotificationVendorMaterialController(
            Method setMixEffectEnabled,
            Method setMiViewBlurMode,
            Method clearMiBackgroundBlendColor,
            Method setMiBackgroundBlendColors,
            Method setMiBloomStroke) throws NoSuchMethodException {
        this.setMixEffectEnabled = setMixEffectEnabled;
        this.setMiViewBlurMode = setMiViewBlurMode;
        this.clearMiBackgroundBlendColor = clearMiBackgroundBlendColor;
        this.setMiBackgroundBlendColors = setMiBackgroundBlendColors;
        this.setMiBloomStroke = setMiBloomStroke;
        this.setMiBackgroundBlurMode = View.class.getMethod("setMiBackgroundBlurMode", int.class);
        this.setMiBackgroundBlurRadius = View.class.getMethod("setMiBackgroundBlurRadius", int.class);
        this.setPassWindowBlurEnabled = View.class.getMethod("setPassWindowBlurEnabled", boolean.class);
    }

    /** Remove SystemUI's default notification element blend before LiquidUI owns this target. */
    void suppressSystemUiElementMaterial(View target) {
        if (target == null) return;
        try {
            setMiViewBlurMode.invoke(target, 0);
            clearMiBackgroundBlendColor.invoke(target);
        } catch (Throwable error) {
            logError("system-ui element blur suppression failed target="
                    + target.getClass().getName(), error);
        }
    }

    void applyHyperLightElementMaterial(View target, Object row) {
        if (target == null) return;
        boolean light = isLight(target);
        int[] materialColors = light ? LIGHT_MATERIAL_COLORS : DARK_MATERIAL_COLORS;
        int[] materialModes = light ? LIGHT_MATERIAL_MODES : DARK_MATERIAL_MODES;

        try {
            enableCardBackdrop(target);
            setMixEffectEnabled.invoke(target, true);
            setMiViewBlurMode.invoke(target, 1);
            setMiBackgroundBlendColors.invoke(
                    target, buildBlendConfig(materialColors, materialModes));
            if (setMiBloomStroke != null) {
                setMiBloomStroke.invoke(target, (Object) scaledBloomStroke(target, light));
            }
            target.setClipToOutline(true);
        } catch (Throwable error) {
            logError("element-material failed target=" + target.getClass().getName(), error);
        }
    }

    /**
     * Supplied MiuiSystemUI.apk uses the same native backdrop tuple in
     * NotificationUtil#applyContainerViewBlur and ElementSurfaceModel#updateBlurContainer:
     * backgroundBlurMode=1 + positive radius + passWindowBlur=true.
     */
    private void enableCardBackdrop(View target) throws Exception {
        int radiusPx = Math.max(1, Math.round(
                CARD_PASS_BLUR_RADIUS_DP * target.getResources().getDisplayMetrics().density));
        setMiBackgroundBlurMode.invoke(target, 1);
        setMiBackgroundBlurRadius.invoke(target, radiusPx);
        setPassWindowBlurEnabled.invoke(target, true);
        if (passBlurLogged.compareAndSet(false, true)) {
            log("enabled card pass-blur target=" + target.getClass().getName()
                    + " radiusDp=" + CARD_PASS_BLUR_RADIUS_DP
                    + " radiusPx=" + radiusPx
                    + " bloom=" + (setMiBloomStroke != null));
        }
    }

    private static boolean isLight(View target) {
        int night = target.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night != Configuration.UI_MODE_NIGHT_YES;
    }

    private static ArrayList<Point> buildBlendConfig(int[] colors, int[] modes) {
        int count = Math.min(colors.length, modes.length);
        ArrayList<Point> config = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            config.add(new Point(colors[index], modes[index]));
        }
        return config;
    }

    private static float[] scaledBloomStroke(View target, boolean light) {
        float[] values = (light ? LIGHT_BLOOM_STROKE : DARK_BLOOM_STROKE).clone();
        float density = target.getResources().getDisplayMetrics().density;
        values[0] = (values[0] * density) + 0.5f;
        values[6] = (values[6] * density) + 0.5f;
        return values;
    }

    private static void log(String message) {
        String formatted = LiquidUiLog.format(TAG + " " + message);
        android.util.Log.i("LiquidUI", formatted);
        try { Api101Bridge.log(formatted); } catch (Throwable ignored) {}
    }

    private static void logError(String message, Throwable error) {
        String formatted = LiquidUiLog.format(TAG + " " + message);
        android.util.Log.e("LiquidUI", formatted, error);
        try { Api101Bridge.log(formatted, error); } catch (Throwable ignored) {}
    }
}
