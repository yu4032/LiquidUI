package com.hellovoid.liquidui.glass.notification;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.hellovoid.liquidui.glass.core.GlassMaterialProfile;
import com.hellovoid.liquidui.glass.core.GlassNode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Geometry-only adapter from the pinned SystemUI notification row contract to generic GlassNode. */
final class NotificationGlassNodeCollector {
    private static final String NATIVE_CARD_RADIUS_NAME = "notification_item_bg_radius";
    private static final float NATIVE_CARD_RADIUS_FALLBACK_DP = 24f;

    private final Class<?> rowClass;
    private final Field backgroundNormalField;
    private final Field actualWidthField;
    private final Field actualHeightField;
    private final Field clipTopField;
    private final Field clipBottomField;
    private final Field expandRunningField;
    private final Field expandWidthField;
    private final Field expandHeightField;
    private final NotificationMaterialTargetRegistry targetRegistry;

    NotificationGlassNodeCollector(
            Class<?> rowClass,
            Field backgroundNormalField,
            Field actualWidthField,
            Field actualHeightField,
            Field clipTopField,
            Field clipBottomField,
            Method ignoredTopCornerRadius,
            Method ignoredBottomCornerRadius,
            Field expandRunningField,
            Field expandWidthField,
            Field expandHeightField,
            NotificationMaterialTargetRegistry targetRegistry) {
        this.rowClass = rowClass;
        this.backgroundNormalField = accessible(backgroundNormalField);
        this.actualWidthField = accessible(actualWidthField);
        this.actualHeightField = accessible(actualHeightField);
        this.clipTopField = accessible(clipTopField);
        this.clipBottomField = accessible(clipBottomField);
        this.expandRunningField = accessible(expandRunningField);
        this.expandWidthField = accessible(expandWidthField);
        this.expandHeightField = accessible(expandHeightField);
        this.targetRegistry = targetRegistry;
    }

    Object backgroundView(Object row) throws IllegalAccessException {
        return rowClass.isInstance(row) ? backgroundNormalField.get(row) : null;
    }

    GlassNode collect(
            Object rowObject,
            View host,
            String id,
            long lifecycleGeneration,
            int zOrder) {
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

            // Match NotificationUtil#setRoundRect's stable native card silhouette instead of
            // ExpandableNotificationRow#getTop/BottomCornerRadius. Those accessors multiply the
            // base radius by transient row roundness, which collapses the glass radius at rest.
            float radius = nativeCardRadiusPx(background);
            radius = Math.min(radius, Math.min(actualWidth, visibleHeight) * 0.5f);
            return new GlassNode(
                    id,
                    lifecycleGeneration,
                    left,
                    top,
                    actualWidth,
                    visibleHeight,
                    radius,
                    radius,
                    radius,
                    radius,
                    row.getAlpha(),
                    zOrder,
                    GlassMaterialProfile.CARD);
        } catch (Throwable ignored) {
            return null;
        }
    }

    int visualZOrder(Object rowObject) {
        if (!(rowObject instanceof View row)) return 0;
        int childIndex = 0;
        ViewParent parent = row.getParent();
        if (parent instanceof ViewGroup group) {
            childIndex = Math.max(0, group.indexOfChild(row));
        }
        float z = row.getZ();
        if (!Float.isFinite(z)) z = 0f;
        int zBucket = Math.round(Math.max(-1000f, Math.min(1000f, z)) * 1000f);
        return zBucket * 10_000 + Math.min(9_999, childIndex);
    }

    private static float nativeCardRadiusPx(View background) {
        int id = background.getResources().getIdentifier(
                NATIVE_CARD_RADIUS_NAME, "dimen", "com.android.systemui");
        if (id != 0) {
            try {
                return Math.max(0f, background.getResources().getDimension(id));
            } catch (Throwable ignored) {
                // Exact target build is pinned by the SystemUI profile; keep its verified 24dp
                // value as the fail-safe rather than falling back to transient row roundness.
            }
        }
        float density = Math.max(0.1f,
                background.getResources().getDisplayMetrics().density);
        return NATIVE_CARD_RADIUS_FALLBACK_DP * density;
    }

    private static int positiveOr(int value, int fallback) {
        return value > -1 ? value : fallback;
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }
}
