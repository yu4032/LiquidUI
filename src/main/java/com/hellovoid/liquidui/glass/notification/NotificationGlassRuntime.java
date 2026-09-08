package com.hellovoid.liquidui.glass.notification;

import android.view.View;
import android.view.ViewParent;

import com.hellovoid.liquidui.Api101Bridge;
import com.hellovoid.liquidui.diagnostics.LiquidUiLog;
import com.hellovoid.liquidui.glass.core.SystemUiGlassCore;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/** UI-thread routing from notification hooks into Window-scoped glass adapters. */
final class NotificationGlassRuntime {
    private static final String TAG = "[NotifGlass][Runtime]";

    private final SystemUiGlassCore glassCore;
    private final Class<?> stackClass;
    private final NotificationGlassNodeCollector collector;
    private final LegacyNotificationVendorMaterialController materialControllerPrototype;
    private final NotificationGlassActivityState activityState;
    private final NotificationPassBlurAuthorityState authorityState;
    private final WeakHashMap<View, NotificationGlassAdapter> adapters = new WeakHashMap<>();
    private final WeakHashMap<Object, NotificationGlassAdapter> rowOwners = new WeakHashMap<>();
    private final WeakHashMap<Object, List<WeakReference<Object>>> pendingWrappers = new WeakHashMap<>();
    private final WeakHashMap<View, Boolean> nativeAnimationRunning = new WeakHashMap<>();
    private long nativeCommitCount;

    NotificationGlassRuntime(
            SystemUiGlassCore glassCore,
            Class<?> stackClass,
            NotificationGlassNodeCollector collector,
            LegacyNotificationVendorMaterialController materialController,
            NotificationGlassActivityState activityState,
            NotificationPassBlurAuthorityState authorityState) {
        this.glassCore = glassCore;
        this.stackClass = stackClass;
        this.collector = collector;
        this.materialControllerPrototype = materialController;
        this.activityState = activityState;
        this.authorityState = authorityState;
    }

    /** Called after HyperOS NSSL.applyCurrentState$1() has committed row ViewState. */
    void onNativeStateCommitted(View stack) {
        if (stack == null) return;
        long count = ++nativeCommitCount;
        if (count <= 5 || count % 120 == 0) {
            log("native commit count=" + count
                    + " stack=" + Integer.toHexString(System.identityHashCode(stack)));
        }
        NotificationGlassAdapter adapter = adapters.get(stack);
        if (adapter != null && !adapter.isShutdown()) {
            adapter.onNativeStateCommitted();
        }
    }

    /** Mirrors the exact NSSL animation lifecycle so per-frame geometry work is bounded. */
    void onAnimationRunning(View stack, boolean running) {
        if (stack == null) return;
        if (running) nativeAnimationRunning.put(stack, Boolean.TRUE);
        else nativeAnimationRunning.remove(stack);
        NotificationGlassAdapter adapter = adapters.get(stack);
        if (adapter != null && !adapter.isShutdown()) {
            adapter.setNativeAnimationRunning(running);
        }
    }

    void onRowAttached(Object rowObject) {
        if (!(rowObject instanceof View row) || !row.isAttachedToWindow()) return;
        View stack = findStack(row);
        if (stack == null) {
            log("row attach ignored: NSSL unavailable");
            return;
        }
        View root = stack.getRootView();
        ShadeWindowGlassAuthority authority =
                ShadeWindowGlassAuthority.ensure(glassCore, root, authorityState);
        if (authority == null) {
            log("row attach ignored: Shade Window renderer authority unavailable");
            return;
        }
        NotificationGlassAdapter adapter = adapters.get(stack);
        if (adapter == null || adapter.isShutdown()) {
            try {
                adapter = new NotificationGlassAdapter(
                        glassCore,
                        stack,
                        collector,
                        materialControllerPrototype.fork(),
                        activityState);
                adapter.setNativeAnimationRunning(
                        Boolean.TRUE.equals(nativeAnimationRunning.get(stack)));
            } catch (Throwable error) {
                log("row attach native fallback: " + error);
                return;
            }
            adapters.put(stack, adapter);
        }
        rowOwners.put(rowObject, adapter);
        adapter.registerRow(rowObject);
        List<WeakReference<Object>> pending = pendingWrappers.remove(rowObject);
        if (pending != null) {
            for (WeakReference<Object> ref : pending) {
                Object wrapper = ref.get();
                if (wrapper != null) adapter.registerWrapper(wrapper);
            }
        }
    }

    void onRowDetached(Object rowObject) {
        NotificationGlassAdapter adapter = rowOwners.remove(rowObject);
        if (adapter != null) adapter.unregisterRow(rowObject);
    }

    void onWrapperObserved(Object wrapper) {
        if (wrapper == null) return;
        Object row = materialControllerPrototype.wrapperRow(wrapper);
        if (row == null) return;
        NotificationGlassAdapter adapter = rowOwners.get(row);
        if (adapter != null && !adapter.isShutdown()) {
            adapter.registerWrapper(wrapper);
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
