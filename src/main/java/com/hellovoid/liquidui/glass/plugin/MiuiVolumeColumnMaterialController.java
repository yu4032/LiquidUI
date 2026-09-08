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

/** Native-material boundary for one floating-volume column's backdrop and slider base. */
public final class MiuiVolumeColumnMaterialController
        implements NativeMaterialController<View> {
    private final Method isBlurEnabledAndSupported;
    private final Method setBlurEnabled;
    private final Method setMiViewBlurMode;
    private final Method clearMiBackgroundBlendColor;
    private final Method getMiBackgroundBlendColor;
    private final Method setMiBackgroundBlendColors;
    private final WeakHashMap<View, State> states = new WeakHashMap<>();

    public MiuiVolumeColumnMaterialController(
            Method isBlurEnabledAndSupported,
            Method setBlurEnabled,
            Method setMiViewBlurMode,
            Method clearMiBackgroundBlendColor,
            Method getMiBackgroundBlendColor,
            Method setMiBackgroundBlendColors) {
        this.isBlurEnabledAndSupported = accessible(Objects.requireNonNull(
                isBlurEnabledAndSupported, "isBlurEnabledAndSupported"));
        this.setBlurEnabled = accessible(Objects.requireNonNull(setBlurEnabled, "setBlurEnabled"));
        this.setMiViewBlurMode = accessible(Objects.requireNonNull(
                setMiViewBlurMode, "setMiViewBlurMode"));
        this.clearMiBackgroundBlendColor = accessible(Objects.requireNonNull(
                clearMiBackgroundBlendColor, "clearMiBackgroundBlendColor"));
        this.getMiBackgroundBlendColor = accessible(Objects.requireNonNull(
                getMiBackgroundBlendColor, "getMiBackgroundBlendColor"));
        this.setMiBackgroundBlendColors = accessible(Objects.requireNonNull(
                setMiBackgroundBlendColors, "setMiBackgroundBlendColors"));
    }

    public void observe(View host, View blurRoot, View slider) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(blurRoot, "blurRoot");
        Objects.requireNonNull(slider, "slider");
        State state = states.computeIfAbsent(host, ignored -> new State());
        if ((state.blurRoot != null && state.blurRoot != blurRoot)
                || (state.slider != null && state.slider != slider)) {
            if (state.suppressed) {
                try { restoreNative(state); } catch (RuntimeException ignored) {}
                state.suppressed = false;
            }
        }
        state.host = host;
        state.blurRoot = blurRoot;
        state.slider = slider;
    }

    /** Reconcile after setSliderBlendColor/setSliderResource/updateColumnH vendor material updates. */
    public boolean refresh(View host) {
        State state = states.get(host);
        if (state == null || state.blurRoot == null || state.slider == null) return false;
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
        if (state == null || state.blurRoot == null || state.slider == null) {
            throw new IllegalStateException("volume column material not observed");
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
        boolean blurEnabled = readBackdropEnabled(state.blurRoot);
        if (initial || blurEnabled) state.backdropEnabled = blurEnabled;

        Drawable sliderBackground = state.slider.getBackground();
        if (initial || sliderBackground != state.sliderBackground
                || (sliderBackground != null && sliderBackground.getAlpha() != 0)) {
            state.sliderBackground = sliderBackground;
            state.sliderOriginalAlpha = sliderBackground == null
                    ? 255 : sliderBackground.getAlpha();
        }

        ArrayList<Point> blend = readBlend(state.slider);
        if (initial || !blend.isEmpty()) state.sliderBlend = blend;
    }

    private void applySuppression(State state) {
        try {
            setBlurEnabled.invoke(state.blurRoot, false);
            setMiViewBlurMode.invoke(state.slider, 0);
            clearMiBackgroundBlendColor.invoke(state.slider);
            Drawable sliderBackground = state.slider.getBackground();
            if (sliderBackground != null) sliderBackground.setAlpha(0);
            state.blurRoot.invalidate();
            state.slider.invalidate();
            state.host.invalidate();
        } catch (Throwable error) {
            throw failure("apply volume column suppression failed", error);
        }
    }

    private void restoreNative(State state) {
        try {
            setBlurEnabled.invoke(state.blurRoot, state.backdropEnabled);
            if (state.sliderBackground != null) {
                state.sliderBackground.setAlpha(state.sliderOriginalAlpha);
            }
            if (state.sliderBlend != null && !state.sliderBlend.isEmpty()) {
                setMiViewBlurMode.invoke(state.slider, 1);
                setMiBackgroundBlendColors.invoke(
                        state.slider, new ArrayList<>(state.sliderBlend));
            } else {
                clearMiBackgroundBlendColor.invoke(state.slider);
                setMiViewBlurMode.invoke(state.slider, 0);
            }
            state.blurRoot.invalidate();
            state.slider.invalidate();
            state.host.invalidate();
        } catch (Throwable error) {
            throw failure("restore volume column material failed", error);
        }
    }

    private boolean readBackdropEnabled(View view) {
        try {
            Object value = isBlurEnabledAndSupported.invoke(view);
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable error) {
            throw failure("read volume column backdrop state failed", error);
        }
    }

    private ArrayList<Point> readBlend(View view) {
        ArrayList<Integer> colors = new ArrayList<>();
        ArrayList<Integer> modes = new ArrayList<>();
        try {
            getMiBackgroundBlendColor.invoke(view, colors, modes);
        } catch (Throwable error) {
            throw failure("read volume slider blend failed", error);
        }
        int count = Math.min(colors.size(), modes.size());
        ArrayList<Point> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(new Point(colors.get(index), modes.get(index)));
        }
        return result;
    }

    private static IllegalStateException failure(String message, Throwable error) {
        if (error instanceof IllegalStateException illegalStateException) return illegalStateException;
        return new IllegalStateException(message, error);
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }

    private static final class State {
        View host;
        View blurRoot;
        View slider;
        Drawable sliderBackground;
        int sliderOriginalAlpha = 255;
        boolean backdropEnabled;
        ArrayList<Point> sliderBlend = new ArrayList<>();
        long generation;
        boolean suppressed;
    }
}
