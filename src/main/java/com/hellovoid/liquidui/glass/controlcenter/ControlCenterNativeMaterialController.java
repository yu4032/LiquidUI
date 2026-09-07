package com.hellovoid.liquidui.glass.controlcenter;

import android.animation.ObjectAnimator;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.widget.ImageView;

import com.hellovoid.liquidui.glass.systemui.NativeMaterialController;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Native-material boundary for the verified built-in QS tile icon background.
 *
 * <p>The foreground icon remains in the original LayerDrawable so its exact layer size, gravity,
 * tint and AnimatedVectorDrawable behavior stay vendor-owned. Only background-layer alpha is
 * suppressed after shared-glass presentation authorization.</p>
 */
public final class ControlCenterNativeMaterialController implements NativeMaterialController<View> {
    private final Field imageField;
    private final Field animatorField;
    private final WeakHashMap<View, State> states = new WeakHashMap<>();
    private final WeakHashMap<Object, View> wrapperHosts = new WeakHashMap<>();

    public ControlCenterNativeMaterialController(Field imageField, Field animatorField) {
        this.imageField = accessible(Objects.requireNonNull(imageField, "imageField"));
        this.animatorField = accessible(Objects.requireNonNull(animatorField, "animatorField"));
    }

    public void observe(View host, Object iconWrapper) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(iconWrapper, "iconWrapper");
        State state = states.computeIfAbsent(host, ignored -> new State());
        if (state.iconWrapper != null && state.iconWrapper != iconWrapper) {
            wrapperHosts.remove(state.iconWrapper);
        }
        state.iconWrapper = iconWrapper;
        wrapperHosts.put(iconWrapper, host);
    }

    /** Called after the vendor rebuilt the LayerDrawable for an exact tile state update. */
    public void onIconUpdated(Object iconWrapper, boolean active) {
        View host = wrapperHosts.get(iconWrapper);
        if (host == null) return;
        State state = states.get(host);
        if (state == null || state.iconWrapper != iconWrapper) return;
        state.active = active;
        if (!state.suppressed) return;
        try {
            applySuppression(state);
        } catch (Throwable error) {
            // Keep this callback local: the current authorized frame was already validated during
            // initial suppress. A later vendor drawable anomaly must not crash SystemUI.
            try { restoreNativeFinalState(state); } catch (Throwable ignored) {}
            state.suppressed = false;
        }
    }

    public void forget(View host) {
        if (host == null) return;
        State state = states.remove(host);
        if (state != null && state.iconWrapper != null) wrapperHosts.remove(state.iconWrapper);
    }

    @Override
    public void suppress(View host, long generation) throws Throwable {
        State state = states.computeIfAbsent(host, ignored -> new State());
        state.generation = generation;
        applySuppression(state);
        state.suppressed = true;
    }

    @Override
    public void restore(View host, long generation) throws Throwable {
        State state = states.get(host);
        if (state == null || state.generation != generation) return;
        if (state.suppressed) restoreNativeFinalState(state);
        state.suppressed = false;
    }

    @Override
    public void restoreAll() {
        for (Map.Entry<View, State> entry : new ArrayList<>(states.entrySet())) {
            State state = entry.getValue();
            if (state == null || !state.suppressed) continue;
            try { restoreNativeFinalState(state); } catch (Throwable ignored) {}
            state.suppressed = false;
        }
    }

    private void applySuppression(State state) throws Throwable {
        cancelBackgroundAnimator(state.iconWrapper);
        ImageView image = imageView(state.iconWrapper);
        Drawable drawable = image.getDrawable();
        if (!(drawable instanceof LayerDrawable layers)) {
            throw new IllegalStateException("QS icon drawable is not LayerDrawable");
        }
        int last = layers.getNumberOfLayers() - 1;
        if (last < 1) throw new IllegalStateException("QS icon LayerDrawable has no background");
        for (int index = 0; index < last; index++) {
            layers.getDrawable(index).setAlpha(0);
        }
        image.invalidate();
    }

    private void restoreNativeFinalState(State state) throws Throwable {
        cancelBackgroundAnimator(state.iconWrapper);
        ImageView image = imageView(state.iconWrapper);
        Drawable drawable = image.getDrawable();
        if (!(drawable instanceof LayerDrawable layers)) return;
        int count = layers.getNumberOfLayers();
        int last = count - 1;
        if (last < 1) return;

        for (int index = 0; index < last; index++) {
            layers.getDrawable(index).setAlpha(255);
        }
        // Vendor animated transition shape is [disabledBg, enabledBg, foreground]. Restore the
        // final target state rather than the alpha at the instant LiquidUI cancelled the animator.
        if (count == 3) layers.getDrawable(1).setAlpha(state.active ? 255 : 0);
        image.invalidate();
    }

    private ImageView imageView(Object iconWrapper) throws IllegalAccessException {
        Object value = imageField.get(iconWrapper);
        if (!(value instanceof ImageView image)) {
            throw new IllegalStateException("MiuiQSIconViewImpl.mIcon is not ImageView");
        }
        return image;
    }

    private void cancelBackgroundAnimator(Object iconWrapper) throws IllegalAccessException {
        if (iconWrapper == null) throw new IllegalStateException("missing MiuiQSIconViewImpl");
        Object value = animatorField.get(iconWrapper);
        if (value instanceof ObjectAnimator animator) animator.cancel();
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }

    private static final class State {
        Object iconWrapper;
        long generation;
        boolean active;
        boolean suppressed;
    }
}
