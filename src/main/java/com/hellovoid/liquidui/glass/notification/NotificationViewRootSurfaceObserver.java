package com.hellovoid.liquidui.glass.notification;

import android.view.View;

import com.hellovoid.liquidui.Api101Bridge;
import com.hellovoid.liquidui.diagnostics.LiquidUiLog;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * ViewRoot lifecycle signal only. Vendor PassBlur transactions are deliberately executed after
 * these callbacks return; invoking SetPassBlurSurface from the callback transaction has crashed
 * this SystemUI build in libgui.
 */
final class NotificationViewRootSurfaceObserver {
    interface Listener {
        void onSurfaceDestroyed();
        void onSurfaceReady(String event);
    }

    private static final String TAG = "[NotifGlass][RootSurface]";

    private final View host;
    private final Listener listener;
    private Object viewRoot;
    private Object callback;
    private Method removeCallback;

    NotificationViewRootSurfaceObserver(View host, Listener listener) {
        this.host = host;
        this.listener = listener;
    }

    void attach() {
        if (host == null || listener == null || !host.isAttachedToWindow()) return;
        try {
            Object nextRoot = SystemUiPassBlurBridge.getViewRootImpl(host);
            if (nextRoot == null) return;
            if (nextRoot == viewRoot && callback != null) return;
            detach();

            Class<?> callbackType = findCallbackType(nextRoot.getClass());
            Method add = nextRoot.getClass().getMethod("addSurfaceChangedCallback", callbackType);
            Method remove = nextRoot.getClass().getMethod("removeSurfaceChangedCallback", callbackType);
            Object nextCallback = Proxy.newProxyInstance(
                    callbackType.getClassLoader(),
                    new Class<?>[]{callbackType},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if (method.getDeclaringClass() == Object.class) {
                            if ("toString".equals(name)) return TAG;
                            if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                            if ("equals".equals(name)) {
                                return args != null && args.length == 1 && proxy == args[0];
                            }
                            return null;
                        }
                        if ("surfaceDestroyed".equals(name)) {
                            // Revocation is UI-only and must happen immediately so a stale glass
                            // frame never remains presentation-authoritative.
                            listener.onSurfaceDestroyed();
                        } else if ("surfaceCreated".equals(name)
                                || "surfaceReplaced".equals(name)) {
                            // Defer all producer/rebind work until after ViewRoot's transaction.
                            host.post(() -> listener.onSurfaceReady(name));
                        }
                        return null;
                    });
            add.invoke(nextRoot, nextCallback);
            viewRoot = nextRoot;
            callback = nextCallback;
            removeCallback = remove;
            log("attached viewRoot=" + System.identityHashCode(nextRoot));
        } catch (Throwable error) {
            log("attach unavailable error=" + error);
        }
    }

    void detach() {
        Object oldRoot = viewRoot;
        Object oldCallback = callback;
        Method oldRemove = removeCallback;
        viewRoot = null;
        callback = null;
        removeCallback = null;
        if (oldRoot == null || oldCallback == null || oldRemove == null) return;
        try {
            oldRemove.invoke(oldRoot, oldCallback);
        } catch (Throwable ignored) {}
    }

    private static Class<?> findCallbackType(Class<?> type) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            for (Class<?> nested : current.getDeclaredClasses()) {
                if ("SurfaceChangedCallback".equals(nested.getSimpleName())) return nested;
            }
            current = current.getSuperclass();
        }
        throw new NoSuchMethodException(type.getName() + "$SurfaceChangedCallback");
    }

    private static void log(String message) {
        try {
            Api101Bridge.log(LiquidUiLog.format(TAG + " " + message));
        } catch (Throwable ignored) {
            android.util.Log.i("LiquidUI", "[LUI]" + TAG + " " + message);
        }
    }
}
