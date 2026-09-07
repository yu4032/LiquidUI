package com.hellovoid.liquidui.glass.controlcenter;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.widget.SeekBar;

import com.hellovoid.liquidui.glass.systemui.NativeMaterialController;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** Native material boundary for the verified classic-QS brightness seekbar track. */
public final class BrightnessSliderNativeMaterialController
        implements NativeMaterialController<View> {
    private final WeakHashMap<View, State> states = new WeakHashMap<>();

    @Override
    public void suppress(View host, long generation) {
        State state = states.computeIfAbsent(host, ignored -> new State());
        state.generation = generation;
        try {
            applySuppression(host, state);
            state.suppressed = true;
        } catch (Throwable error) {
            state.suppressed = false;
            throw materialFailure("suppress", error);
        }
    }

    @Override
    public void restore(View host, long generation) {
        State state = states.get(host);
        if (state == null || state.generation != generation) return;
        try {
            restoreState(state);
        } finally {
            state.suppressed = false;
            state.background = null;
        }
    }

    @Override
    public void restoreAll() {
        for (Map.Entry<View, State> entry : new ArrayList<>(states.entrySet())) {
            State state = entry.getValue();
            if (state == null) continue;
            try { restoreState(state); } catch (Throwable ignored) {}
            state.suppressed = false;
            state.background = null;
        }
    }

    /** Rebind suppression after MiuiQSContainer replaced the whole progress drawable. */
    public boolean refresh(View host) {
        State state = states.get(host);
        if (state == null || !state.suppressed) return true;
        try {
            Drawable previous = state.background;
            int previousAlpha = state.originalAlpha;
            Drawable current = background(host);
            if (current == previous) {
                current.setAlpha(0);
                host.invalidate();
                return true;
            }
            // The old drawable is no longer attached to the seekbar, but restore it so retaining
            // references elsewhere never observe a LiquidUI-owned alpha.
            if (previous != null) previous.setAlpha(previousAlpha);
            state.background = current;
            state.originalAlpha = current.getAlpha();
            current.setAlpha(0);
            host.invalidate();
            return true;
        } catch (Throwable error) {
            try { restoreState(state); } catch (Throwable ignored) {}
            state.suppressed = false;
            state.background = null;
            return false;
        }
    }

    public void forget(View host) {
        if (host == null) return;
        State state = states.remove(host);
        if (state == null) return;
        try { restoreState(state); } catch (Throwable ignored) {}
    }

    private static void applySuppression(View host, State state) {
        Drawable current = background(host);
        if (state.background != current) {
            if (state.background != null) state.background.setAlpha(state.originalAlpha);
            state.background = current;
            state.originalAlpha = current.getAlpha();
        }
        current.setAlpha(0);
        host.invalidate();
    }

    private static void restoreState(State state) {
        if (state.suppressed && state.background != null) {
            state.background.setAlpha(state.originalAlpha);
        }
    }

    private static Drawable background(View host) {
        if (!(host instanceof SeekBar seekBar)) {
            throw new IllegalStateException("brightness host is not SeekBar");
        }
        Drawable progress = seekBar.getProgressDrawable();
        if (!(progress instanceof LayerDrawable layers)) {
            throw new IllegalStateException("brightness progress drawable is not LayerDrawable");
        }
        Drawable background = layers.findDrawableByLayerId(android.R.id.background);
        if (background == null) {
            throw new IllegalStateException("brightness progress drawable has no background layer");
        }
        return background;
    }

    private static RuntimeException materialFailure(String operation, Throwable error) {
        if (error instanceof RuntimeException runtime) return runtime;
        return new IllegalStateException("brightness native material " + operation + " failed", error);
    }

    private static final class State {
        long generation;
        boolean suppressed;
        Drawable background;
        int originalAlpha = 255;
    }
}
