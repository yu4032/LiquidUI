package com.hellovoid.liquidui.glass.media;

import android.graphics.drawable.Drawable;
import android.view.View;

import com.hellovoid.liquidui.glass.systemui.NativeMaterialController;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Native-material boundary for the verified media-output dialog panel.
 *
 * <p>The dialog root and device-list container are one visual panel. SystemUI dynamically tints
 * the root drawable and replaces the list background ColorDrawable, so LiquidUI preserves both
 * drawable identities and restores their exact native alpha rather than replacing backgrounds.</p>
 */
public final class MediaOutputDialogNativeMaterialController
        implements NativeMaterialController<View> {
    private final WeakHashMap<View, State> states = new WeakHashMap<>();

    public void observe(View root, View deviceList) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(deviceList, "deviceList");
        State state = states.computeIfAbsent(root, ignored -> new State());
        if (state.deviceList != null && state.deviceList != deviceList && state.suppressed) {
            try { restoreNative(state); } catch (RuntimeException ignored) {}
            state.suppressed = false;
        }
        state.deviceList = deviceList;
    }

    /** Re-apply suppression after updateDialogBackgroundColor() tinted/replaced native layers. */
    public boolean refresh(View root) {
        State state = states.get(root);
        if (state == null || state.deviceList == null) return false;
        if (!state.suppressed) return true;
        try {
            captureReplacements(root, state);
            applySuppression(root, state);
            return true;
        } catch (RuntimeException error) {
            try { restoreNative(state); } catch (RuntimeException ignored) {}
            state.suppressed = false;
            return false;
        }
    }

    public void forget(View root) {
        if (root == null) return;
        State state = states.remove(root);
        if (state == null || !state.suppressed) return;
        try { restoreNative(state); } catch (RuntimeException ignored) {}
        state.suppressed = false;
    }

    @Override
    public void suppress(View root, long generation) {
        State state = states.get(root);
        if (state == null || state.deviceList == null) {
            throw new IllegalStateException("media output dialog material not observed");
        }
        captureInitial(root, state);
        applySuppression(root, state);
        state.generation = generation;
        state.suppressed = true;
    }

    @Override
    public void restore(View root, long generation) {
        State state = states.get(root);
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

    private static void captureInitial(View root, State state) {
        Drawable rootBackground = root.getBackground();
        if (rootBackground == null) {
            throw new IllegalStateException("media output dialog root has no background");
        }
        state.rootBackground = rootBackground;
        state.rootOriginalAlpha = rootBackground.getAlpha();

        Drawable listBackground = state.deviceList.getBackground();
        state.listBackground = listBackground;
        state.listOriginalAlpha = listBackground == null ? 255 : listBackground.getAlpha();
    }

    private static void captureReplacements(View root, State state) {
        Drawable currentRoot = root.getBackground();
        if (currentRoot == null) {
            throw new IllegalStateException("media output dialog root background disappeared");
        }
        if (currentRoot != state.rootBackground) {
            state.rootBackground = currentRoot;
            state.rootOriginalAlpha = currentRoot.getAlpha();
        }

        Drawable currentList = state.deviceList.getBackground();
        if (currentList != state.listBackground) {
            state.listBackground = currentList;
            state.listOriginalAlpha = currentList == null ? 255 : currentList.getAlpha();
        }
    }

    private static void applySuppression(View root, State state) {
        state.rootBackground.setAlpha(0);
        if (state.listBackground != null) state.listBackground.setAlpha(0);
        root.invalidate();
        state.deviceList.invalidate();
    }

    private static void restoreNative(State state) {
        if (state.rootBackground != null) {
            state.rootBackground.setAlpha(state.rootOriginalAlpha);
        }
        if (state.listBackground != null) {
            state.listBackground.setAlpha(state.listOriginalAlpha);
        }
        if (state.deviceList != null) state.deviceList.invalidate();
    }

    private static final class State {
        View deviceList;
        Drawable rootBackground;
        Drawable listBackground;
        int rootOriginalAlpha;
        int listOriginalAlpha;
        long generation;
        boolean suppressed;
    }
}
