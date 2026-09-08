package com.hellovoid.liquidui.glass.plugin;

import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.widget.ImageView;

import com.hellovoid.liquidui.glass.systemui.NativeMaterialController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Native-material boundary for plugin-owned Control Center ordinary-tile icon backgrounds. */
public final class MiuiControlCenterTileMaterialController
        implements NativeMaterialController<View> {
    private final Method setMiViewBlurMode;
    private final Method clearMiBackgroundBlendColor;
    private final Method getMiBackgroundBlendColor;
    private final Method setMiBackgroundBlendColors;
    private final WeakHashMap<View, State> states = new WeakHashMap<>();

    public MiuiControlCenterTileMaterialController(
            Method setMiViewBlurMode,
            Method clearMiBackgroundBlendColor,
            Method getMiBackgroundBlendColor,
            Method setMiBackgroundBlendColors) {
        this.setMiViewBlurMode = accessible(Objects.requireNonNull(
                setMiViewBlurMode, "setMiViewBlurMode"));
        this.clearMiBackgroundBlendColor = accessible(Objects.requireNonNull(
                clearMiBackgroundBlendColor, "clearMiBackgroundBlendColor"));
        this.getMiBackgroundBlendColor = accessible(Objects.requireNonNull(
                getMiBackgroundBlendColor, "getMiBackgroundBlendColor"));
        this.setMiBackgroundBlendColors = accessible(Objects.requireNonNull(
                setMiBackgroundBlendColors, "setMiBackgroundBlendColors"));
    }

    public void observe(View host, View materialView) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(materialView, "materialView");
        if (!(materialView instanceof ImageView)) {
            throw new IllegalArgumentException("Control Center tile material is not ImageView");
        }
        State state = states.computeIfAbsent(host, ignored -> new State());
        if (state.materialView != null && state.materialView != materialView && state.suppressed) {
            try { restoreNative(state); } catch (RuntimeException ignored) {}
            state.suppressed = false;
        }
        state.materialView = materialView;
    }

    /** Reconcile after QSTileItemIconView.updateIcon() rebuilt Mi blur or LayerDrawable state. */
    public boolean refresh(View host) {
        State state = states.get(host);
        if (state == null || state.materialView == null) return false;
        if (!state.suppressed) return true;
        try {
            captureNative(state, false);
            applySuppression(state);
            return true;
        } catch (RuntimeException error) {
            try { restoreNative(state); } catch (RuntimeException ignored) {}
            state.suppressed = false;
            return false;
        }
    }

    public void forget(View host) {
        if (host == null) return;
        State state = states.remove(host);
        if (state == null || !state.suppressed) return;
        try { restoreNative(state); } catch (RuntimeException ignored) {}
        state.suppressed = false;
    }

    @Override
    public void suppress(View host, long generation) {
        State state = states.get(host);
        if (state == null || state.materialView == null) {
            throw new IllegalStateException("Control Center tile material not observed");
        }
        captureNative(state, true);
        applySuppression(state);
        state.generation = generation;
        state.suppressed = true;
    }

    @Override
    public void restore(View host, long generation) {
        State state = states.get(host);
        if (state == null || state.generation != generation || !state.suppressed) return;
        restoreNative(state);
        state.suppressed = false;
    }

    @Override
    public void restoreAll() {
        for (Map.Entry<View, State> entry : new ArrayList<>(states.entrySet())) {
            State state = entry.getValue();
            if (state == null || !state.suppressed) continue;
            try { restoreNative(state); } catch (RuntimeException ignored) {}
            state.suppressed = false;
        }
    }

    private void captureNative(State state, boolean initial) {
        ImageView imageView = (ImageView) state.materialView;
        Drawable current = imageView.getDrawable();
        if (initial || current != state.imageDrawable || nativeLayersWereRebuilt(current, state)) {
            state.imageDrawable = current;
            state.backgroundLayerAlphas = captureBackgroundLayerAlphas(current);
        }

        ArrayList<Point> currentBlend = readBlend(state.materialView);
        // While already suppressed, an empty list normally means updateIcon() returned without
        // rebuilding native blur. Preserve the last real native tuple in that case.
        if (initial || !currentBlend.isEmpty()) {
            state.blendConfig = currentBlend;
        }
    }

    private void applySuppression(State state) {
        try {
            setMiViewBlurMode.invoke(state.materialView, 0);
            clearMiBackgroundBlendColor.invoke(state.materialView);
            Drawable drawable = ((ImageView) state.materialView).getDrawable();
            if (drawable instanceof LayerDrawable layers) {
                int backgroundCount = Math.max(0, layers.getNumberOfLayers() - 1);
                for (int index = 0; index < backgroundCount; index++) {
                    Drawable layer = layers.getDrawable(index);
                    if (layer != null) layer.setAlpha(0);
                }
            }
            state.materialView.invalidate();
        } catch (Throwable error) {
            throw failure("apply Control Center tile suppression failed", error);
        }
    }

    private void restoreNative(State state) {
        try {
            restoreBackgroundLayerAlphas(state.imageDrawable, state.backgroundLayerAlphas);
            ArrayList<Point> blendConfig = state.blendConfig;
            if (blendConfig != null && !blendConfig.isEmpty()) {
                setMiViewBlurMode.invoke(state.materialView, 1);
                setMiBackgroundBlendColors.invoke(
                        state.materialView, new ArrayList<>(blendConfig));
            } else {
                clearMiBackgroundBlendColor.invoke(state.materialView);
                setMiViewBlurMode.invoke(state.materialView, 0);
            }
            state.materialView.invalidate();
        } catch (Throwable error) {
            throw failure("restore Control Center tile material failed", error);
        }
    }

    private ArrayList<Point> readBlend(View view) {
        ArrayList<Integer> colors = new ArrayList<>();
        ArrayList<Integer> modes = new ArrayList<>();
        try {
            getMiBackgroundBlendColor.invoke(view, colors, modes);
        } catch (Throwable error) {
            throw failure("read Control Center tile blend failed", error);
        }
        int count = Math.min(colors.size(), modes.size());
        ArrayList<Point> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(new Point(colors.get(index), modes.get(index)));
        }
        return result;
    }

    private static int[] captureBackgroundLayerAlphas(Drawable drawable) {
        if (!(drawable instanceof LayerDrawable layers)) return new int[0];
        int backgroundCount = Math.max(0, layers.getNumberOfLayers() - 1);
        int[] alphas = new int[backgroundCount];
        for (int index = 0; index < backgroundCount; index++) {
            Drawable layer = layers.getDrawable(index);
            alphas[index] = layer == null ? 255 : layer.getAlpha();
        }
        return alphas;
    }

    private static boolean nativeLayersWereRebuilt(Drawable drawable, State state) {
        if (!(drawable instanceof LayerDrawable layers)) {
            return state.backgroundLayerAlphas.length != 0;
        }
        int backgroundCount = Math.max(0, layers.getNumberOfLayers() - 1);
        if (backgroundCount != state.backgroundLayerAlphas.length) return true;
        for (int index = 0; index < backgroundCount; index++) {
            Drawable layer = layers.getDrawable(index);
            if (layer != null && layer.getAlpha() != 0) return true;
        }
        return false;
    }

    private static void restoreBackgroundLayerAlphas(Drawable drawable, int[] alphas) {
        if (!(drawable instanceof LayerDrawable layers) || alphas == null) return;
        int count = Math.min(alphas.length, Math.max(0, layers.getNumberOfLayers() - 1));
        for (int index = 0; index < count; index++) {
            Drawable layer = layers.getDrawable(index);
            if (layer != null) layer.setAlpha(alphas[index]);
        }
    }

    private static IllegalStateException failure(String message, Throwable error) {
        if (error instanceof IllegalStateException illegalStateException) {
            return illegalStateException;
        }
        return new IllegalStateException(message, error);
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }

    private static final class State {
        View materialView;
        Drawable imageDrawable;
        int[] backgroundLayerAlphas = new int[0];
        ArrayList<Point> blendConfig = new ArrayList<>();
        long generation;
        boolean suppressed;
    }
}
