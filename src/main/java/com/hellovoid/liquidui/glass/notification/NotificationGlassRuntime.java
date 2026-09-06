package com.hellovoid.liquidui.glass.notification;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.hellovoid.liquidui.Api101Bridge;
import com.hellovoid.liquidui.diagnostics.LiquidUiLog;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/** UI-thread authority that maps every attached notification row to one shared NSSL session. */
final class NotificationGlassRuntime {
    private static final String TAG = "[NotifGlass][Runtime]";

    private final Class<?> stackClass;
    private final NotificationGlassNodeCollector collector;
    private final NotificationVendorMaterialController materialControllerPrototype;
    private final NotificationGlassActivityState activityState;
    private final NotificationPassBlurAuthorityState authorityState;
    private final NotificationPassBlurSourceState sourceState;
    private final NotificationPassBlurContentAuthorityState contentAuthorityState;
    private final WeakHashMap<View, NotificationGlassSession> sessions = new WeakHashMap<>();
    private final WeakHashMap<Object, NotificationGlassSession> rowOwners = new WeakHashMap<>();
    private final WeakHashMap<Object, List<WeakReference<Object>>> pendingWrappers = new WeakHashMap<>();

    NotificationGlassRuntime(
            Class<?> stackClass,
            NotificationGlassNodeCollector collector,
            NotificationVendorMaterialController materialController,
            NotificationGlassActivityState activityState,
            NotificationPassBlurAuthorityState authorityState,
            NotificationPassBlurSourceState sourceState,
            NotificationPassBlurContentAuthorityState contentAuthorityState) {
        this.stackClass = stackClass;
        this.collector = collector;
        this.materialControllerPrototype = materialController;
        this.activityState = activityState;
        this.authorityState = authorityState;
        this.sourceState = sourceState;
        this.contentAuthorityState = contentAuthorityState;
    }

    void onRowAttached(Object rowObject) {
        if (!(rowObject instanceof View row) || !row.isAttachedToWindow()) return;
        View stack = findStack(row);
        if (stack == null || !(stack.getParent() instanceof ViewGroup parent)) {
            log("row attach ignored: NSSL parent unavailable");
            return;
        }
        NotificationGlassSession session = sessions.get(stack);
        if (session == null || session.isShutdown()) {
            session = new NotificationGlassSession(
                    stack, parent, collector, materialControllerPrototype.fork(),
                    activityState, authorityState, sourceState, contentAuthorityState);
            sessions.put(stack, session);
        }
        rowOwners.put(rowObject, session);
        session.registerRow(rowObject);
        List<WeakReference<Object>> pending = pendingWrappers.remove(rowObject);
        if (pending != null) {
            for (WeakReference<Object> ref : pending) {
                Object wrapper = ref.get();
                if (wrapper != null) session.registerWrapper(wrapper);
            }
        }
    }

    void onRowDetached(Object rowObject) {
        NotificationGlassSession session = rowOwners.remove(rowObject);
        if (session != null) session.unregisterRow(rowObject);
    }

    void onWrapperObserved(Object wrapper) {
        if (wrapper == null) return;
        Object row = materialControllerPrototype.wrapperRow(wrapper);
        if (row == null) return;
        NotificationGlassSession session = rowOwners.get(row);
        if (session != null && !session.isShutdown()) {
            session.registerWrapper(wrapper);
            return;
        }
        pendingWrappers.computeIfAbsent(row, ignored -> new ArrayList<>())
                .add(new WeakReference<>(wrapper));
    }

    private View findStack(View row) {
        ViewParent parent = row.getParent();
        while (parent instanceof View view) {
            if (stackClass.isInstance(view)) return view;
            parent = view.getParent();
        }
        return null;
    }

    private static void log(String message) {
        try {
            Api101Bridge.log(LiquidUiLog.format(TAG + " " + message));
        } catch (Throwable ignored) {
            android.util.Log.i("LiquidUI", "[LUI]" + TAG + " " + message);
        }
    }
}
