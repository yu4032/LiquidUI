package com.hellovoid.liquidui.glass.notification;

import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Exact systemui-001 geometry adapter for ExpandableNotificationRow#mBackgroundNormal. */
final class NotificationGlassNodeCollector {
    private final Class<?> rowClass;
    private final Field backgroundNormalField;
    private final Field actualWidthField;
    private final Field actualHeightField;
    private final Field clipTopField;
    private final Field clipBottomField;
    private final Method topCornerRadius;
    private final Method bottomCornerRadius;
    private final Field expandRunningField;
    private final Field expandWidthField;
    private final Field expandHeightField;

    NotificationGlassNodeCollector(
            Class<?> rowClass,
            Field backgroundNormalField,
            Field actualWidthField,
            Field actualHeightField,
            Field clipTopField,
            Field clipBottomField,
            Method topCornerRadius,
            Method bottomCornerRadius,
            Field expandRunningField,
            Field expandWidthField,
            Field expandHeightField) {
        this.rowClass = rowClass;
        this.backgroundNormalField = accessible(backgroundNormalField);
        this.actualWidthField = accessible(actualWidthField);
        this.actualHeightField = accessible(actualHeightField);
        this.clipTopField = accessible(clipTopField);
        this.clipBottomField = accessible(clipBottomField);
        this.topCornerRadius = accessible(topCornerRadius);
        this.bottomCornerRadius = accessible(bottomCornerRadius);
        this.expandRunningField = accessible(expandRunningField);
        this.expandWidthField = accessible(expandWidthField);
        this.expandHeightField = accessible(expandHeightField);
    }

    Object backgroundView(Object row) throws IllegalAccessException {
        return rowClass.isInstance(row) ? backgroundNormalField.get(row) : null;
    }

    NotificationGlassNode collect(Object rowObject, View host) {
        if (!rowClass.isInstance(rowObject) || host == null || !host.isAttachedToWindow()) return null;
        View row = (View) rowObject;
        if (!row.isAttachedToWindow() || !row.isShown() || row.getAlpha() <= 0.001f) return null;
        try {
            Object backgroundObject = backgroundNormalField.get(rowObject);
            if (!(backgroundObject instanceof View background)) return null;
            if (!background.isAttachedToWindow() || background.getVisibility() != View.VISIBLE) return null;

            boolean expand = expandRunningField.getBoolean(backgroundObject);
            int viewWidth = background.getWidth();
            int viewHeight = background.getHeight();
            int actualWidth = expand && expandWidthField.getInt(backgroundObject) > -1
                    ? expandWidthField.getInt(backgroundObject)
                    : positiveOr(actualWidthField.getInt(backgroundObject), viewWidth);
            int actualHeight = expand && expandHeightField.getInt(backgroundObject) > -1
                    ? expandHeightField.getInt(backgroundObject)
                    : positiveOr(actualHeightField.getInt(backgroundObject), viewHeight);
            int clipTop = Math.max(0, clipTopField.getInt(backgroundObject));
            int clipBottom = Math.max(0, clipBottomField.getInt(backgroundObject));
            // Mirrors NotificationBackgroundView.onDraw(): top clip participates only in the
            // whole-background empty check, while bottom clip constrains the actual Canvas region.
            if (!expand && clipTop + clipBottom >= actualHeight) return null;

            int leftOffset;
            if (expand) {
                leftOffset = Math.round((viewWidth - actualWidth) * 0.5f);
            } else if (background.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
                leftOffset = viewWidth - actualWidth;
            } else {
                leftOffset = 0;
            }
            int visibleHeight = expand ? actualHeight : Math.max(0, actualHeight - clipBottom);
            if (actualWidth <= 0 || visibleHeight <= 0) return null;

            int[] bgScreen = new int[2];
            int[] hostScreen = new int[2];
            background.getLocationOnScreen(bgScreen);
            host.getLocationOnScreen(hostScreen);
            float left = bgScreen[0] - hostScreen[0] + leftOffset;
            float top = bgScreen[1] - hostScreen[1];

            float topRadius = number(topCornerRadius.invoke(rowObject));
            float bottomRadius = number(bottomCornerRadius.invoke(rowObject));
            float opacity = row.getAlpha();
            return new NotificationGlassNode(
                    left, top, actualWidth, visibleHeight,
                    topRadius, topRadius, bottomRadius, bottomRadius, opacity);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int positiveOr(int value, int fallback) {
        return value > -1 ? value : fallback;
    }

    private static float number(Object value) {
        return value instanceof Number ? Math.max(0f, ((Number) value).floatValue()) : 0f;
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }
}
