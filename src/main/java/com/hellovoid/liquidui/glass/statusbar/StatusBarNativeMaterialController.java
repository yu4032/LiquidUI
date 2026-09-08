package com.hellovoid.liquidui.glass.statusbar;

import android.graphics.drawable.Drawable;
import android.view.View;

import com.hellovoid.liquidui.glass.systemui.NativeMaterialController;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** Native-material boundary for the verified ongoing-activity chip background only. */
public final class StatusBarNativeMaterialController implements NativeMaterialController<View> {
    private final WeakHashMap<View, State> states = new WeakHashMap<>();

    public void observe(View host) {
        if (host == null) throw new IllegalArgumentException("host == null");
        Drawable current = requireBackground(host);
        State state = states.computeIfAbsent(host, ignored -> new State());
        if (state.drawable != current) {
            if (state.suppressed && state.drawable != null) {
                try { state.drawable.setAlpha(state.nativeAlpha); } catch (RuntimeException ignored) {}
            }
            state.drawable = current;
            state.nativeAlpha = current.getAlpha();
            if (state.suppressed) current.setAlpha(0);
            return;
        }
        if (!state.suppressed) state.nativeAlpha = current.getAlpha();
    }

    /** Reconcile after the vendor binder rewrites the chip color/stroke or replaces the drawable. */
    public boolean refresh(View host) {
        State state = states.get(host);
        if (state == null) return false;
        try {
            observe(host);
            if (state.suppressed && state.drawable != null) {
                state.drawable.setAlpha(0);
                host.invalidate();
            }
            return true;
        } catch (RuntimeException error) {
            tryRestore(state);
            state.suppressed = false;
            return false;
        }
    }

    public void forget(View host) {
        if (host == null) return;
        State state = states.remove(host);
        if (state == null) return;
        tryRestore(state);
        state.suppressed = false;
    }

    @Override
    public void suppress(View host, long generation) {
        observe(host);
        State state = states.get(host);
        if (state == null || state.drawable == null) {
            throw new IllegalStateException("status capsule material not observed");
        }
        if (!state.suppressed) state.nativeAlpha = state.drawable.getAlpha();
        state.drawable.setAlpha(0);
        host.invalidate();
        state.generation = generation;
        state.suppressed = true;
    }

    @Override
    public void restore(View host, long generation) {
        State state = states.get(host);
        if (state == null || !state.suppressed || state.generation != generation) return;
        tryRestore(state);
        state.suppressed = false;
        if (host != null) host.invalidate();
    }

    @Override
    public void restoreAll() {
        for (Map.Entry<View, State> entry : new ArrayList<>(states.entrySet())) {
            State state = entry.getValue();
            if (state == null || !state.suppressed) continue;
            tryRestore(state);
            state.suppressed = false;
            View host = entry.getKey();
            if (host != null) host.invalidate();
        }
    }

    private static Drawable requireBackground(View host) {
        Drawable background = host.getBackground();
        if (background == null) {
            throw new IllegalStateException("ongoing activity chip background is null");
        }
        return background;
    }

    private static void tryRestore(State state) {
        if (state == null || state.drawable == null) return;
        try { state.drawable.setAlpha(state.nativeAlpha); } catch (RuntimeException ignored) {}
    }

    private static final class State {
        Drawable drawable;
        int nativeAlpha = 255;
        long generation;
        boolean suppressed;
    }
}
