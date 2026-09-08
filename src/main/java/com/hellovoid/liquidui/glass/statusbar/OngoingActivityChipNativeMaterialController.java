package com.hellovoid.liquidui.glass.statusbar;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import com.hellovoid.liquidui.glass.systemui.NativeMaterialController;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** Native material boundary for the bounded ongoing-activity chip backgroundView. */
public final class OngoingActivityChipNativeMaterialController
        implements NativeMaterialController<View> {
    private final WeakHashMap<View, State> states = new WeakHashMap<>();

    public void observe(View host) {
        if (host == null) return;
        State state = states.computeIfAbsent(host, ignored -> new State());
        GradientDrawable background = requireGradient(host.getBackground());
        if (state.background != background) {
            if (state.suppressed && state.background != null) {
                state.background.setAlpha(state.originalAlpha);
            }
            state.background = background;
            state.originalAlpha = background.getAlpha();
        } else if (!state.suppressed) {
            state.originalAlpha = background.getAlpha();
        }
    }

    /** Re-assert suppression after OngoingActivityChipBinder updates color/stroke or drawable. */
    public boolean refresh(View host) {
        State state = states.get(host);
        if (state == null) return false;
        try {
            GradientDrawable current = requireGradient(host.getBackground());
            if (state.background != current) {
                if (state.suppressed && state.background != null) {
                    state.background.setAlpha(state.originalAlpha);
                }
                state.background = current;
                state.originalAlpha = current.getAlpha();
            }
            if (state.suppressed) current.setAlpha(0);
            host.invalidate();
            return true;
        } catch (RuntimeException error) {
            try { restoreNative(state, host); } catch (RuntimeException ignored) {}
            state.suppressed = false;
            return false;
        }
    }

    public void forget(View host) {
        if (host == null) return;
        State state = states.remove(host);
        if (state == null || !state.suppressed) return;
        try { restoreNative(state, host); } catch (RuntimeException ignored) {}
        state.suppressed = false;
    }

    @Override
    public void suppress(View host, long lifecycleGeneration) {
        observe(host);
        State state = states.get(host);
        if (state == null || state.background == null) {
            throw new IllegalStateException("ongoing chip background not observed");
        }
        state.background.setAlpha(0);
        host.invalidate();
        state.generation = lifecycleGeneration;
        state.suppressed = true;
    }

    @Override
    public void restore(View host, long lifecycleGeneration) {
        State state = states.get(host);
        if (state == null || !state.suppressed || state.generation != lifecycleGeneration) return;
        restoreNative(state, host);
        state.suppressed = false;
    }

    @Override
    public void restoreAll() {
        for (Map.Entry<View, State> entry : new ArrayList<>(states.entrySet())) {
            State state = entry.getValue();
            if (state == null || !state.suppressed) continue;
            try { restoreNative(state, entry.getKey()); } catch (RuntimeException ignored) {}
            state.suppressed = false;
        }
    }

    private static void restoreNative(State state, View host) {
        if (state.background != null) state.background.setAlpha(state.originalAlpha);
        if (host != null) host.invalidate();
    }

    private static GradientDrawable requireGradient(Drawable drawable) {
        if (drawable instanceof GradientDrawable gradientDrawable) return gradientDrawable;
        throw new IllegalStateException("ongoing chip background is not GradientDrawable");
    }

    private static final class State {
        GradientDrawable background;
        int originalAlpha;
        long generation;
        boolean suppressed;
    }
}
