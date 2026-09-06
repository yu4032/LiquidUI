package com.hellovoid.liquidui.glass.notification;

import android.view.View;
import android.view.ViewParent;

import java.util.WeakHashMap;

/** Weak registry whose authority is SystemUI's notification material dispatch, not row lifecycle. */
final class NotificationMaterialTargetRegistry {
    /**
     * Exact semantics of NotificationUtil#setRoundRect(View, boolean, boolean) in the supplied
     * MiuiSystemUI.apk: first flag chooses NotificationBackgroundView actualHeight geometry; second
     * flag selects flip_notification_item_bg_radius versus notification_item_bg_radius.
     */
    record OutlineState(boolean useActualHeightGeometry, boolean useFlipRadius) {}

    private final Class<?> rowClass;
    private final WeakHashMap<View, Object> targetRows = new WeakHashMap<>();
    private final WeakHashMap<Object, OutlineState> rowOutlineStates = new WeakHashMap<>();
    private final WeakHashMap<Object, Boolean> childrenExpanded = new WeakHashMap<>();

    NotificationMaterialTargetRegistry(Class<?> rowClass) {
        this.rowClass = rowClass;
    }

    synchronized Object observeMaterialTarget(View target) {
        if (target == null) return null;
        Object row = findRow(target);
        if (row != null) targetRows.put(target, row);
        return row;
    }

    synchronized Object rowForTarget(View target) {
        Object row = targetRows.get(target);
        if (row != null) return row;
        return observeMaterialTarget(target);
    }

    synchronized void observeChildrenExpanded(Object container, Object[] args) {
        if (container == null || args == null || args.length == 0
                || !(args[0] instanceof Boolean expanded)) {
            return;
        }
        childrenExpanded.put(container, expanded);
    }

    synchronized boolean childrenExpanded(Object container) {
        return Boolean.TRUE.equals(childrenExpanded.get(container));
    }

    synchronized void observeRoundRect(Object[] args) {
        if (args == null || args.length < 3 || !(args[0] instanceof View target)
                || !(args[1] instanceof Boolean useActualHeightGeometry)
                || !(args[2] instanceof Boolean useFlipRadius)) {
            return;
        }
        Object row = findRow(target);
        if (row != null) {
            targetRows.put(target, row);
            rowOutlineStates.put(
                    row, new OutlineState(useActualHeightGeometry, useFlipRadius));
        }
    }

    synchronized OutlineState outlineState(Object row) {
        return rowOutlineStates.get(row);
    }

    private Object findRow(View start) {
        View current = start;
        while (current != null) {
            if (rowClass.isInstance(current)) return current;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }
}
