package com.hellovoid.liquidui.glass.notification;

import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.view.View;

import com.hellovoid.liquidui.Api101Bridge;
import com.hellovoid.liquidui.diagnostics.LiquidUiLog;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** Applies LiquidUI material and native PassBlur backdrop to the exact notification target. */
final class NotificationVendorMaterialController {
    private static final String TAG = "[NotifGlass][Material]";
    private static final float CARD_PASS_BLUR_RADIUS_DP = 2.0f;
    private static final boolean PROBE_DISABLE_BLOOM_STROKE = true;
    private static final float PROBE_REFRACTION_AMOUNT_PX = 32.0f;
    private static final float PROBE_CHROMATIC_ABERRATION_PX = 10.0f;

    private static final String AGSL_REFRACTION_PROBE = """
            uniform shader content;
            uniform float2 size;
            uniform float refractionAmount;
            uniform float chromaticAberration;

            half4 main(float2 p) {
                float2 halfSize = max(size * 0.5, float2(1.0));
                float2 n = (p - halfSize) / halfSize;
                float radius = length(n);
                float edge = smoothstep(0.42, 0.98, radius);
                float2 dir = radius > 0.0001 ? n / radius : float2(0.0);
                float2 bend = dir * (edge * refractionAmount);
                float2 tangent = float2(-dir.y, dir.x);
                float chroma = edge * chromaticAberration;

                half4 base = content.eval(p - bend);
                half red = content.eval(p - bend + tangent * chroma).r;
                half blue = content.eval(p - bend - tangent * chroma).b;
                return half4(red, base.g, blue, base.a);
            }
            """;

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
    private final AtomicBoolean agslLogged = new AtomicBoolean();
    private RuntimeShader refractionShader;
    private RenderEffect refractionEffect;

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
            applyAgslRefractionProbe(target);
            setMixEffectEnabled.invoke(target, true);
            setMiViewBlurMode.invoke(target, 1);
            setMiBackgroundBlendColors.invoke(
                    target, buildBlendConfig(materialColors, materialModes));
            if (!PROBE_DISABLE_BLOOM_STROKE && setMiBloomStroke != null) {
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
                    + " bloomApi=" + (setMiBloomStroke != null)
                    + " bloomApplied=" + (!PROBE_DISABLE_BLOOM_STROKE && setMiBloomStroke != null));
        }
    }

    /**
     * Feasibility probe only: apply an intentionally strong AGSL displacement to this exact View.
     * If vendor PassBlur is part of the View render input, the sharp backdrop pattern will visibly
     * bend near the card edge and split into RGB fringes. No screenshot or capture texture is used.
     */
    private void applyAgslRefractionProbe(View target) {
        int width = Math.max(target.getWidth(), target.getMeasuredWidth());
        int height = Math.max(target.getHeight(), target.getMeasuredHeight());
        if (width <= 0 || height <= 0) {
            if (agslLogged.compareAndSet(false, true)) {
                log("AGSL refraction probe skipped zero-size target=" + target.getClass().getName()
                        + " width=" + width + " height=" + height);
            }
            return;
        }

        if (refractionShader == null) {
            refractionShader = new RuntimeShader(AGSL_REFRACTION_PROBE);
            refractionEffect = RenderEffect.createRuntimeShaderEffect(refractionShader, "content");
        }
        refractionShader.setFloatUniform("size", (float) width, (float) height);
        refractionShader.setFloatUniform("refractionAmount", PROBE_REFRACTION_AMOUNT_PX);
        refractionShader.setFloatUniform("chromaticAberration", PROBE_CHROMATIC_ABERRATION_PX);
        target.setRenderEffect(refractionEffect);

        if (agslLogged.compareAndSet(false, true)) {
            log("applied AGSL refraction probe target=" + target.getClass().getName()
                    + " size=" + width + "x" + height
                    + " refractionPx=" + PROBE_REFRACTION_AMOUNT_PX
                    + " chromaticPx=" + PROBE_CHROMATIC_ABERRATION_PX);
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
