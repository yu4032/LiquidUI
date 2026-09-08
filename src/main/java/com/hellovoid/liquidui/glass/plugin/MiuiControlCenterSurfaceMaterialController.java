package com.hellovoid.liquidui.glass.plugin;

import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.hellovoid.liquidui.glass.systemui.NativeMaterialController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Native-material boundary for card and slider background surfaces in the SystemUI plugin. */
public final class MiuiControlCenterSurfaceMaterialController
        implements NativeMaterialController<View> {
    private final Method setMiViewBlurMode;
    private final Method clearMiBackgroundBlendColor;
    private final Method getMiBackgroundBlendColor;
    private final Method setMiBackgroundBlendColors;
    private final WeakHashMap<View, State> states = new WeakHashMap<>();

    public MiuiControlCenterSurfaceMaterialController(
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
        State state = states.computeIfAbsent(host, ignored -> new State());
        if (state.materialView != null && state.materialView != materialView && state.suppressed) {
            try { restoreNative(state); } catch (RuntimeException ignored) {}
            state.suppressed = false;
        }
        state.materialView = materialView;
    }

    /** Reconcile after updateBackground/updateBlendBlur/configuration rebuilt native material. */
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
            throw new IllegalStateException("Control Center surface material not observed");
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
        Drawable current = state.materialView.getBackground();
        if (initial || current != state.background
                || (current != null && current.getAlpha() != 0)) {
            state.background = current;
            state.originalAlpha = current == null ? 255 : current.getAlpha();
        }

        ArrayList<Point> currentBlend = readBlend(state.materialView);
        // When the vendor update path really ran it republishes blend tuples. If the list is still
        // empty while already suppressed, keep the previously captured native tuple.
        if (initial || !currentBlend.isEmpty()) {
            state.blendConfig = currentBlend;
        }
    }

    private void applySuppression(State state) {
        try {
            setMiViewBlurMode.invoke(state.materialView, 0);
            clearMiBackgroundBlendColor.invoke(state.materialView);
            Drawable background = state.materialView.getBackground();
            if (background != null) background.setAlpha(0);
            state.materialView.invalidate();
        } catch (Throwable error) {
            throw failure("apply Control Center surface suppression failed", error);
        }
    }

    private void restoreNative(State state) {
        try {
            if (state.background != null) state.background.setAlpha(state.originalAlpha);
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
            throw failure("restore Control Center surface material failed", error);
        }
    }

    private ArrayList<Point> readBlend(View view) {
        ArrayList<Integer> colors = new ArrayList<>();
        ArrayList<Integer> modes = new ArrayList<>();
        try {
            getMiBackgroundBlendColor.invoke(view, colors, modes);
        } catch (Throwable error) {
            throw failure("read Control Center surface blend failed", error);
        }
        int count = Math.min(colors.size(), modes.size());
        ArrayList<Point> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(new Point(colors.get(index), modes.get(index)));
        }
        return result;
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
        Drawable background;
        int originalAlpha = 255;
        ArrayList<Point> blendConfig = new ArrayList<>();
        long generation;
        boolean suppressed;
    }
}
