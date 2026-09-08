package com.hellovoid.liquidui.glass.media;

import android.graphics.drawable.Drawable;
import android.view.View;

import com.hellovoid.liquidui.glass.systemui.NativeMaterialController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Native-material boundary for the verified Miui notification media background layer.
 *
 * <p>The media player content, album art, seekbar and actions remain vendor-owned. Only the
 * dedicated {@code mediaBg} view's background drawable and Mi background blend material are
 * suppressed after shared-glass presentation authorization.</p>
 */
public final class MediaNativeMaterialController implements NativeMaterialController<View> {
    private final Method setMiViewBlurMode;
    private final Method clearMiBackgroundBlendColor;
    private final Method getMiBackgroundBlendColor;
    private final Method setMiBackgroundBlendColors;
    private final WeakHashMap<View, State> states = new WeakHashMap<>();

    public MediaNativeMaterialController(
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
            try {
                restoreNative(state);
            } catch (RuntimeException ignored) {
                // The replacement host will be re-authorized from a fresh lifecycle below.
            }
            state.suppressed = false;
        }
        state.materialView = materialView;
    }

    /** Called after SystemUI rebuilt/reblended mediaBg while this host remains authorized. */
    public boolean refresh(View host) {
        State state = states.get(host);
        if (state == null || state.materialView == null) return false;
        if (!state.suppressed) return true;
        try {
            captureNative(state);
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
            throw new IllegalStateException("media material view not observed");
        }
        try {
            captureNative(state);
            applySuppression(state);
            state.generation = generation;
            state.suppressed = true;
        } catch (Throwable error) {
            throw failure("media native suppression failed", error);
        }
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

    private void captureNative(State state) {
        View materialView = state.materialView;
        Drawable background = materialView.getBackground();
        if (background == null) {
            throw new IllegalStateException("Miui mediaBg has no background drawable");
        }
        state.background = background;
        state.originalAlpha = background.getAlpha();

        ArrayList<Integer> colors = new ArrayList<>();
        ArrayList<Integer> modes = new ArrayList<>();
        try {
            getMiBackgroundBlendColor.invoke(materialView, colors, modes);
        } catch (Throwable error) {
            throw failure("read Mi background blend colors failed", error);
        }
        int count = Math.min(colors.size(), modes.size());
        ArrayList<Integer> blendConfig = new ArrayList<>(count * 2);
        for (int index = 0; index < count; index++) {
            blendConfig.add(colors.get(index));
            blendConfig.add(modes.get(index));
        }
        state.blendConfig = blendConfig;
    }

    private void applySuppression(State state) {
        try {
            setMiViewBlurMode.invoke(state.materialView, 0);
            clearMiBackgroundBlendColor.invoke(state.materialView);
            state.background.setAlpha(0);
            state.materialView.invalidate();
        } catch (Throwable error) {
            throw failure("apply Mi media suppression failed", error);
        }
    }

    private void restoreNative(State state) {
        try {
            if (state.background != null) state.background.setAlpha(state.originalAlpha);
            ArrayList<Integer> blendConfig = state.blendConfig;
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
            throw failure("restore Mi media material failed", error);
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
        Drawable background;
        ArrayList<Integer> blendConfig;
        int originalAlpha;
        long generation;
        boolean suppressed;
    }
}
