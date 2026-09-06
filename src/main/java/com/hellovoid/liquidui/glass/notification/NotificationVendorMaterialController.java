package com.hellovoid.liquidui.glass.notification;

import android.graphics.drawable.Drawable;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/** Owns reversible suppression of the vendor notification material after GPU activation. */
final class NotificationVendorMaterialController {
    private final NotificationGlassNodeCollector collector;
    private final Class<?> rowClass;
    private final Field wrapperViewField;
    private final Field wrapperRowField;
    private final Method disableBlur;
    private final Method clearBlend;

    private final WeakHashMap<View, Float> backgroundAlpha = new WeakHashMap<>();
    private final WeakHashMap<View, Drawable> contentBackground = new WeakHashMap<>();

    NotificationVendorMaterialController(
            NotificationGlassNodeCollector collector,
            Class<?> rowClass,
            Field wrapperViewField,
            Field wrapperRowField,
            Method disableBlur,
            Method clearBlend) {
        this.collector = collector;
        this.rowClass = rowClass;
        this.wrapperViewField = accessible(wrapperViewField);
        this.wrapperRowField = accessible(wrapperRowField);
        this.disableBlur = accessible(disableBlur);
        this.clearBlend = accessible(clearBlend);
    }

    NotificationVendorMaterialController fork() {
        return new NotificationVendorMaterialController(
                collector, rowClass, wrapperViewField, wrapperRowField, disableBlur, clearBlend);
    }

    void suppressRow(Object row) {
        try {
            Object backgroundObject = collector.backgroundView(row);
            if (!(backgroundObject instanceof View background)) return;
            backgroundAlpha.putIfAbsent(background, background.getAlpha());
            disableBlur.invoke(null, 0, background);
            clearBlend.invoke(null, background);
            if (background.getAlpha() != 0f) background.setAlpha(0f);
        } catch (Throwable ignored) {
        }
    }

    void restoreRow(Object row) {
        try {
            Object backgroundObject = collector.backgroundView(row);
            if (!(backgroundObject instanceof View background)) return;
            Float alpha = backgroundAlpha.remove(background);
            if (alpha != null) background.setAlpha(alpha);
        } catch (Throwable ignored) {
        }
    }

    View wrapperView(Object wrapper) {
        try {
            Object value = wrapperViewField.get(wrapper);
            return value instanceof View ? (View) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    Object wrapperRow(Object wrapper) {
        try {
            Object row = wrapperRowField.get(wrapper);
            return rowClass.isInstance(row) ? row : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    void suppressWrapper(Object wrapper) {
        try {
            Object row = wrapperRow(wrapper);
            if (row == null) return;
            Object viewObject = wrapperViewField.get(wrapper);
            if (!(viewObject instanceof View view)) return;
            if (!contentBackground.containsKey(view)) {
                contentBackground.put(view, view.getBackground());
            }
            if (view.getBackground() != null) view.setBackground(null);
        } catch (Throwable ignored) {
        }
    }

    void restoreWrapper(Object wrapper) {
        try {
            Object viewObject = wrapperViewField.get(wrapper);
            if (!(viewObject instanceof View view)) return;
            if (!contentBackground.containsKey(view)) return;
            Drawable original = contentBackground.remove(view);
            view.setBackground(original);
        } catch (Throwable ignored) {
        }
    }

    void restoreAll() {
        for (var entry : new WeakHashMap<>(backgroundAlpha).entrySet()) {
            View view = entry.getKey();
            if (view != null) view.setAlpha(entry.getValue());
        }
        backgroundAlpha.clear();
        for (var entry : new WeakHashMap<>(contentBackground).entrySet()) {
            View view = entry.getKey();
            if (view != null) view.setBackground(entry.getValue());
        }
        contentBackground.clear();
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }
}
