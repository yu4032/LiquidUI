package com.hellovoid.liquidui.glass.notification;

import android.content.res.Configuration;
import android.graphics.Point;
import android.view.View;

import com.hellovoid.liquidui.Api101Bridge;
import com.hellovoid.liquidui.diagnostics.LiquidUiLog;

import java.lang.reflect.Method;
import java.util.ArrayList;

/** Applies HyperLight's verified element-material path to SystemUI's exact notification target. */
final class NotificationVendorMaterialController {
    private static final String TAG = "[NotifGlass][Material]";

    // HyperLight 1.1.7 defaults, decompiled from miuix.theme.token.ColorBlendToken.
    private static final int[] LIGHT_MATERIAL_COLORS = {
            -428575628, -1722658222, 869388753
    };
    private static final int[] LIGHT_MATERIAL_MODES = {15, 18, 3};
    private static final int[] DARK_MATERIAL_COLORS = {
            -942879540, 777146962
    };
    private static final int[] DARK_MATERIAL_MODES = {19, 3};

    // HyperLight defaults, decompiled from BloomStrokeToken. Values 0 and 6 are density-scaled
    // exactly like SystemUI's miuix.core.util.HyperBloomStrokeUtils#setBloomStrokeConfig.
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
    private final Method setMiBackgroundBlendColors;
    private final Method setMiBloomStroke;

    NotificationVendorMaterialController(
            Method setMixEffectEnabled,
            Method setMiViewBlurMode,
            Method setMiBackgroundBlendColors,
            Method setMiBloomStroke) {
        this.setMixEffectEnabled = setMixEffectEnabled;
        this.setMiViewBlurMode = setMiViewBlurMode;
        this.setMiBackgroundBlendColors = setMiBackgroundBlendColors;
        this.setMiBloomStroke = setMiBloomStroke;
    }

    void applyHyperLightElementMaterial(
            View target, Object row, int[] systemBlendColors, float systemBlendRatio) {
        if (target == null) return;

        boolean light = isLight(target);
        int[] materialColors = light ? LIGHT_MATERIAL_COLORS : DARK_MATERIAL_COLORS;
        int[] materialModes = light ? LIGHT_MATERIAL_MODES : DARK_MATERIAL_MODES;

        try {
            // HyperLight's colorBlend-only HyperMaterialUtils path. These are element APIs; do not
            // claim background/container/pass-window PassBlur ownership on mBackgroundNormal.
            setMixEffectEnabled.invoke(target, true);
            setMiViewBlurMode.invoke(target, 1);
            setMiBackgroundBlendColors.invoke(
                    target, buildBlendConfig(materialColors, materialModes));

            if (setMiBloomStroke != null) {
                setMiBloomStroke.invoke(target, (Object) scaledBloomStroke(target, light));
            }

            // HyperLight clips its material. Preserve SystemUI's already-installed outline provider
            // instead of replacing it with HyperLight's hard-coded 24dp provider.
            target.setClipToOutline(true);

            log("applied element-material target=" + target.getClass().getName()
                    + " row=" + (row == null ? "<none>" : row.getClass().getName())
                    + " theme=" + (light ? "light" : "dark")
                    + " systemColors=" + (systemBlendColors == null ? 0 : systemBlendColors.length)
                    + " ratio=" + systemBlendRatio
                    + " bloom=" + (setMiBloomStroke != null));
        } catch (Throwable error) {
            // This is a BEFORE hook. Fail open so SystemUI's original material setter always runs.
            logError("element-material failed target=" + target.getClass().getName(), error);
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
        try {
            Api101Bridge.log(formatted);
        } catch (Throwable ignored) {
            // Direct logcat above is the device-side diagnostic authority.
        }
    }

    private static void logError(String message, Throwable error) {
        String formatted = LiquidUiLog.format(TAG + " " + message);
        android.util.Log.e("LiquidUI", formatted, error);
        try {
            Api101Bridge.log(formatted, error);
        } catch (Throwable ignored) {
            // Direct logcat above is the device-side diagnostic authority.
        }
    }
}
