package com.hellovoid.liquidui.glass.notification;

import android.view.View;
import android.view.ViewGroup;

import com.hellovoid.liquidui.Api101Bridge;
import com.hellovoid.liquidui.diagnostics.LiquidUiLog;
import com.hellovoid.liquidui.glass.core.SystemUiGlassCore;
import com.hellovoid.liquidui.glass.core.WindowGlassSession;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/**
 * Exact systemui-001 owner of the shared NotificationShade Window renderer.
 *
 * <p>HyperOS places SharedNotificationContainer and ControlCenterContainer as independent page
 * branches under NotificationShadeWindowView. The renderer therefore cannot live under either
 * page. It is inserted directly after the root ShadeBackgroundView, below both pages' native
 * foreground content. The page-level blur/blend backgrounds are neutralized by
 * NotificationSharedGlassHook while their foreground Views remain untouched.</p>
 *
 * <p>Important authority split: notifPassBlur/ctrlPassBlur are native page material state. On the
 * exact target they intentionally become false for ordinary unlocked Shade states, so they are not
 * permission for LiquidUI's own Window-scoped PassBlur consumer. Producer lifetime is owned by
 * this attached Shade Window and demand is still controlled by WindowGlassSession's active adapter
 * bindings.</p>
 */
final class ShadeWindowGlassAuthority implements AutoCloseable {
    private static final String TAG = "[ShadeWindowGlass]";
    private static final String SHADE_WINDOW =
            "com.android.systemui.shade.NotificationShadeWindowView";
    private static final String SHADE_BACKGROUND =
            "com.miui.systemui.shade.ShadeBackgroundView";
    private static final String NOTIFICATION_PANEL =
            "com.android.systemui.shade.NotificationPanelView";
    private static final String SHARED_NOTIFICATION_CONTAINER =
            "com.android.systemui.statusbar.notification.stack.ui.view.SharedNotificationContainer";
    private static final String CONTROL_CENTER_CONTAINER =
            "com.miui.systemui.controlcenter.container.ControlCenterContainer";

    private static final WeakHashMap<View, ShadeWindowGlassAuthority> ACTIVE = new WeakHashMap<>();

    static ShadeWindowGlassAuthority ensure(
            SystemUiGlassCore glassCore,
            View shadeWindow,
            NotificationPassBlurAuthorityState authorityState) {
        if (glassCore == null || shadeWindow == null || authorityState == null) return null;
        if (!SHADE_WINDOW.equals(shadeWindow.getClass().getName())) return null;
        if (!(shadeWindow instanceof ViewGroup root) || !shadeWindow.isAttachedToWindow()) return null;
        synchronized (ACTIVE) {
            ShadeWindowGlassAuthority current = ACTIVE.get(shadeWindow);
            if (current != null && !current.closed && !current.session.isClosed()) return current;
            ShadeWindowGlassAuthority created = new ShadeWindowGlassAuthority(
                    glassCore, root, authorityState);
            ACTIVE.put(shadeWindow, created);
            return created;
        }
    }

    private final WeakReference<View> shadeWindowRef;
    private final WindowGlassSession session;
    private final View.OnAttachStateChangeListener attachListener;
    private boolean closed;

    private ShadeWindowGlassAuthority(
            SystemUiGlassCore glassCore,
            ViewGroup root,
            NotificationPassBlurAuthorityState authorityState) {
        verifyExactShadeHierarchy(root);
        this.shadeWindowRef = new WeakReference<>(root);

        // Preserve exact native page state for diagnostics/material policy. These values are NOT
        // the gate for LiquidUI's own Window producer; unlocked Shade legitimately reports false.
        observeCurrentPassBlurAuthority(root, authorityState);

        this.session = glassCore.sessionFor(root);
        int insertionIndex = rendererInsertionIndex(root);
        View sceneHost = session.attachRenderer(
                root, root, insertionIndex, true);
        if (sceneHost == null) {
            throw new IllegalStateException("NotificationShade Window renderer unavailable");
        }

        this.attachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}
            @Override public void onViewDetachedFromWindow(View v) { close(); }
        };
        root.addOnAttachStateChangeListener(attachListener);
        log("renderer attached root=" + root.getClass().getName()
                + " index=" + insertionIndex
                + " producerGate=shade-window-owned"
                + " nativeNotifPassBlur=" + authorityState.isNotificationEnabled()
                + " nativeCtrlPassBlur=" + authorityState.isControlCenterEnabled());
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        View shadeWindow = shadeWindowRef.get();
        if (shadeWindow != null) {
            try { shadeWindow.removeOnAttachStateChangeListener(attachListener); } catch (Throwable ignored) {}
            synchronized (ACTIVE) { ACTIVE.remove(shadeWindow, this); }
        }
    }

    private static void observeCurrentPassBlurAuthority(
            ViewGroup root, NotificationPassBlurAuthorityState authorityState) {
        View notificationPanel = directChild(root, NOTIFICATION_PANEL);
        View controlCenter = directChild(root, CONTROL_CENTER_CONTAINER);
        if (notificationPanel == null || controlCenter == null) {
            throw new IllegalStateException(
                    "systemui-001 PassBlur roots unavailable notificationPanel="
                            + (notificationPanel != null)
                            + " controlCenter=" + (controlCenter != null));
        }
        try {
            Method getPassWindowBlurEnabled =
                    View.class.getMethod("getPassWindowBlurEnabled");
            boolean notificationEnabled = booleanValue(
                    getPassWindowBlurEnabled.invoke(notificationPanel));
            boolean controlCenterEnabled = booleanValue(
                    getPassWindowBlurEnabled.invoke(controlCenter));
            authorityState.observeNotification(notificationEnabled);
            authorityState.observeControlCenter(controlCenterEnabled);
            log("initial native page state notifPassBlur=" + notificationEnabled
                    + " ctrlPassBlur=" + controlCenterEnabled);
        } catch (Throwable error) {
            throw new IllegalStateException(
                    "systemui-001 current PassBlur page state unavailable", error);
        }
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean enabled) return enabled;
        throw new IllegalStateException("PassBlur getter returned " + value);
    }

    private static View directChild(ViewGroup root, String className) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (className.equals(child.getClass().getName())) return child;
        }
        return null;
    }

    private static void verifyExactShadeHierarchy(ViewGroup root) {
        boolean hasNotificationPanel = false;
        boolean hasNotifications = false;
        boolean hasControlCenter = false;
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            String name = child.getClass().getName();
            if (NOTIFICATION_PANEL.equals(name)) hasNotificationPanel = true;
            if (SHARED_NOTIFICATION_CONTAINER.equals(name)) hasNotifications = true;
            if (CONTROL_CENTER_CONTAINER.equals(name)) hasControlCenter = true;
        }
        if (!hasNotificationPanel || !hasNotifications || !hasControlCenter) {
            throw new IllegalStateException(
                    "systemui-001 Shade hierarchy mismatch notificationPanel=" + hasNotificationPanel
                            + " notifications=" + hasNotifications
                            + " controlCenter=" + hasControlCenter);
        }
    }

    private static int rendererInsertionIndex(ViewGroup root) {
        int backgroundIndex = -1;
        int firstPageIndex = root.getChildCount();
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            String name = child.getClass().getName();
            if (SHADE_BACKGROUND.equals(name) && backgroundIndex < 0) backgroundIndex = i;
            if (SHARED_NOTIFICATION_CONTAINER.equals(name) || CONTROL_CENTER_CONTAINER.equals(name)) {
                firstPageIndex = Math.min(firstPageIndex, i);
            }
        }
        if (backgroundIndex < 0 || backgroundIndex >= firstPageIndex) {
            throw new IllegalStateException(
                    "systemui-001 renderer lane unavailable background=" + backgroundIndex
                            + " firstPage=" + firstPageIndex);
        }
        return backgroundIndex + 1;
    }

    private static void log(String message) {
        try {
            Api101Bridge.log(LiquidUiLog.format(TAG + " " + message));
        } catch (Throwable ignored) {
            android.util.Log.i("LiquidUI", "[LUI]" + TAG + " " + message);
        }
    }
}
