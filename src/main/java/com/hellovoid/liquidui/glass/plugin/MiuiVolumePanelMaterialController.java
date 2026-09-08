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

/** Native-material boundary for the expanded HyperOS floating volume panel. */
public final class MiuiVolumePanelMaterialController
        implements NativeMaterialController<View> {
    private final Method isBlurEnabledAndSupported;
    private final Method setBlurEnabled;
    private final Method setMiViewBlurMode;
    private final Method clearMiBackgroundBlendColor;
    private final Method getMiBackgroundBlendColor;
    private final Method setMiBackgroundBlendColors;
    private final WeakHashMap<View, State> states = new WeakHashMap<>();

    public MiuiVolumePanelMaterialController(
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

    public void observe(View host, View contentView) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(contentView, "contentView");
        State state = states.computeIfAbsent(host, ignored -> new State());
        if (state.contentView != null && state.contentView != contentView && state.suppressed) {
            try { restoreNative(state); } catch (RuntimeException ignored) {}
            state.suppressed = false;
        }
        state.host = host;
        state.contentView = contentView;
    }

    /** Re-capture vendor material after expand/theme/blur-mode updates while glass stays active. */
    public boolean refresh(View host) {
        State state = states.get(host);
        if (state == null || state.contentView == null) return false;
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
        if (state == null || state.contentView == null) {
            throw new IllegalStateException("volume panel material not observed");
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
        Drawable panelBackground = state.host.getBackground();
        if (initial || panelBackground != state.panelBackground
                || (panelBackground != null && panelBackground.getAlpha() != 0)) {
            state.panelBackground = panelBackground;
            state.panelOriginalAlpha = panelBackground == null ? 255 : panelBackground.getAlpha();
        }

        Drawable contentBackground = state.contentView.getBackground();
        if (initial || contentBackground != state.contentBackground
                || (contentBackground != null && contentBackground.getAlpha() != 0)) {
            state.contentBackground = contentBackground;
            state.contentOriginalAlpha = contentBackground == null
                    ? 255 : contentBackground.getAlpha();
        }

        boolean blurEnabled = readBackdropEnabled(state.host);
        if (initial || blurEnabled) state.backdropEnabled = blurEnabled;

        ArrayList<Point> blend = readBlend(state.host);
        if (initial || !blend.isEmpty()) state.blendConfig = blend;
    }

    private void applySuppression(State state) {
        try {
            setBlurEnabled.invoke(state.host, false);
            setMiViewBlurMode.invoke(state.host, 0);
            clearMiBackgroundBlendColor.invoke(state.host);
            Drawable panelBackground = state.host.getBackground();
            if (panelBackground != null) panelBackground.setAlpha(0);
            Drawable contentBackground = state.contentView.getBackground();
            if (contentBackground != null) contentBackground.setAlpha(0);
            state.host.invalidate();
            state.contentView.invalidate();
        } catch (Throwable error) {
            throw failure("apply volume panel suppression failed", error);
        }
    }

    private void restoreNative(State state) {
        try {
            setBlurEnabled.invoke(state.host, state.backdropEnabled);
            if (state.panelBackground != null) {
                state.panelBackground.setAlpha(state.panelOriginalAlpha);
            }
            if (state.contentBackground != null) {
                state.contentBackground.setAlpha(state.contentOriginalAlpha);
            }
            if (state.blendConfig != null && !state.blendConfig.isEmpty()) {
                setMiViewBlurMode.invoke(state.host, 1);
                setMiBackgroundBlendColors.invoke(
                        state.host, new ArrayList<>(state.blendConfig));
            } else {
                clearMiBackgroundBlendColor.invoke(state.host);
                setMiViewBlurMode.invoke(state.host, 0);
            }
            state.host.invalidate();
            state.contentView.invalidate();
        } catch (Throwable error) {
            throw failure("restore volume panel material failed", error);
        }
    }

    private boolean readBackdropEnabled(View view) {
        try {
            Object value = isBlurEnabledAndSupported.invoke(view);
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable error) {
            throw failure("read volume panel backdrop state failed", error);
        }
    }

    private ArrayList<Point> readBlend(View view) {
        ArrayList<Integer> colors = new ArrayList<>();
        ArrayList<Integer> modes = new ArrayList<>();
        try {
            getMiBackgroundBlendColor.invoke(view, colors, modes);
        } catch (Throwable error) {
            throw failure("read volume panel blend failed", error);
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
        View contentView;
        Drawable panelBackground;
        Drawable contentBackground;
        int panelOriginalAlpha = 255;
        int contentOriginalAlpha = 255;
        boolean backdropEnabled;
        ArrayList<Point> blendConfig = new ArrayList<>();
        long generation;
        boolean suppressed;
    }
}
