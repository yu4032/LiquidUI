package com.hellovoid.liquidui.glass.notification;

import android.view.View;
import android.view.ViewParent;

import java.util.WeakHashMap;

/** Weak registry whose authority is SystemUI's notification material dispatch, not row lifecycle. */
final class NotificationMaterialTargetRegistry {
    record RoundState(boolean topRounded, boolean bottomRounded) {}

    private final Class<?> rowClass;
    private final WeakHashMap<View, Object> targetRows = new WeakHashMap<>();
    private final WeakHashMap<Object, RoundState> rowRoundStates = new WeakHashMap<>();
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
        if (container == null || args == null || args.length == 0 || !(args[0] instanceof Boolean expanded)) {
            return;
        }
        childrenExpanded.put(container, expanded);
    }

    synchronized boolean childrenExpanded(Object container) {
        return Boolean.TRUE.equals(childrenExpanded.get(container));
    }

    synchronized void observeRoundRect(Object[] args) {
        if (args == null || args.length < 3 || !(args[0] instanceof View target)
                || !(args[1] instanceof Boolean topRounded)
                || !(args[2] instanceof Boolean bottomRounded)) {
            return;
        }
        Object row = findRow(target);
        if (row != null) {
            targetRows.put(target, row);
            rowRoundStates.put(row, new RoundState(topRounded, bottomRounded));
        }
    }

    synchronized RoundState roundState(Object row) {
        return rowRoundStates.get(row);
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
