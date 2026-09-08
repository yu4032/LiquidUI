package com.hellovoid.liquidui.glass.keyguard;

import android.graphics.drawable.Drawable;
import android.view.View;

import com.hellovoid.liquidui.glass.systemui.NativeMaterialController;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Native-material boundary for the exact bounded Keyguard quick-affordance buttons. */
public final class KeyguardNativeMaterialController implements NativeMaterialController<View> {
    private final WeakHashMap<View, State> states = new WeakHashMap<>();

    public void observe(View host) {
        Objects.requireNonNull(host, "host");
        Drawable background = host.getBackground();
        if (background == null) {
            throw new IllegalStateException("Keyguard quick affordance has no background");
        }
        State state = states.computeIfAbsent(host, ignored -> new State());
        if (state.background != null && state.background != background && state.suppressed) {
            restoreNative(state);
            state.suppressed = false;
        }
        state.background = background;
    }

    public void forget(View host) {
        if (host == null) return;
        State state = states.remove(host);
        if (state == null || !state.suppressed) return;
        restoreNative(state);
        state.suppressed = false;
    }

    @Override
    public void suppress(View host, long generation) {
        State state = states.get(host);
        if (state == null || state.background == null) {
            throw new IllegalStateException("Keyguard quick affordance material not observed");
        }
        Drawable current = host.getBackground();
        if (current == null) {
            throw new IllegalStateException("Keyguard quick affordance background disappeared");
        }
        if (current != state.background) {
            if (state.suppressed) restoreNative(state);
            state.background = current;
            state.suppressed = false;
        }
        if (!state.suppressed) {
            state.nativeAlpha = state.background.getAlpha();
        }
        state.background.setAlpha(0);
        host.invalidate();
        state.generation = generation;
        state.suppressed = true;
    }

    @Override
    public void restore(View host, long generation) {
        State state = states.get(host);
        if (state == null || !state.suppressed || state.generation != generation) return;
        restoreNative(state);
        state.suppressed = false;
    }

    @Override
    public void restoreAll() {
        for (Map.Entry<View, State> entry : new ArrayList<>(states.entrySet())) {
            State state = entry.getValue();
            if (state == null || !state.suppressed) continue;
            restoreNative(state);
            state.suppressed = false;
        }
    }

    private static void restoreNative(State state) {
        if (state.background == null) return;
        state.background.setAlpha(state.nativeAlpha);
    }

    private static final class State {
        Drawable background;
        int nativeAlpha = 255;
        long generation;
        boolean suppressed;
    }
}
