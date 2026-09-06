package com.hellovoid.liquidui.glass.notification;

import android.view.View;

import com.hellovoid.liquidui.Api101Bridge;
import com.hellovoid.liquidui.diagnostics.LiquidUiLog;
import com.hellovoid.liquidui.hook.BeforeMethodHookBackend;
import com.hellovoid.liquidui.hook.HookInstallResult;
import com.hellovoid.liquidui.hook.SystemUiHook;
import com.hellovoid.liquidui.reflect.TargetClassResolver;
import com.hellovoid.liquidui.target.SystemUiTargetProfile;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Read-only observer for HyperOS' framework-owned NotificationShade texture consumers.
 *
 * Do not guess which SystemUI child owns the callback. Observe ViewRootImpl's actual
 * addTextureView/clearTextureView registrations and every View#setTextureAvailable callback whose
 * root is the exact NotificationShadeWindowView. No framework consumer is created or mutated here.
 */
public final class FrameworkPassBlurViewConsumerHook implements SystemUiHook {
    public static final String HOOK_ID = "notification.framework-passblur-view-consumer-probe";
    private static final String TARGET_PROFILE = "systemui-001";
    private static final String FRAMEWORK_VIEW = "android.view.View";
    private static final String VIEW_ROOT_IMPL = "android.view.ViewRootImpl";
    private static final String SHADE_WINDOW =
            "com.android.systemui.shade.NotificationShadeWindowView";
    private static final String TAG = "[NotifGlass][FrameworkPB][Consumer]";
    private static final AtomicLong SEQUENCE = new AtomicLong();

    private final BeforeMethodHookBackend backend;

    public FrameworkPassBlurViewConsumerHook(BeforeMethodHookBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    @Override public String id() { return HOOK_ID; }

    @Override
    public HookInstallResult install(ClassLoader classLoader, SystemUiTargetProfile profile) {
        Objects.requireNonNull(classLoader, "classLoader");
        Objects.requireNonNull(profile, "profile");
        if (!TARGET_PROFILE.equals(profile.id())) {
            return HookInstallResult.unsupported(HOOK_ID, "profile=" + profile.id());
        }
        try {
            Class<?> viewClass = TargetClassResolver.require(classLoader, FRAMEWORK_VIEW);
            Class<?> viewRootImplClass = TargetClassResolver.require(classLoader, VIEW_ROOT_IMPL);
            Class<?> shadeWindowClass = TargetClassResolver.require(classLoader, SHADE_WINDOW);

            Method setTextureAvailable = viewClass.getDeclaredMethod(
                    "setTextureAvailable", boolean.class, int.class, float.class);
            Method getPassWindowBlurEnabled =
                    viewClass.getDeclaredMethod("getPassWindowBlurEnabled");
            Method getPassTextureScale = viewClass.getDeclaredMethod("getPassTextureScale");
            Method addTextureView = viewRootImplClass.getDeclaredMethod("addTextureView", View.class);
            Method clearTextureView = viewRootImplClass.getDeclaredMethod("clearTextureView", View.class);
            Method getView = viewRootImplClass.getDeclaredMethod("getView");
            setTextureAvailable.setAccessible(true);
            getPassWindowBlurEnabled.setAccessible(true);
            getPassTextureScale.setAccessible(true);
            addTextureView.setAccessible(true);
            clearTextureView.setAccessible(true);
            getView.setAccessible(true);

            backend.intercept(
                    addTextureView,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> observeRegistration(
                            "consumer-register", thisObject, args, getView, shadeWindowClass,
                            getPassWindowBlurEnabled, getPassTextureScale));
            backend.intercept(
                    clearTextureView,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> observeRegistration(
                            "consumer-clear", thisObject, args, getView, shadeWindowClass,
                            getPassWindowBlurEnabled, getPassTextureScale));
            backend.intercept(
                    setTextureAvailable,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (!(thisObject instanceof View view)
                                || !isShadeRoot(view, shadeWindowClass)
                                || args.length < 3
                                || !(args[0] instanceof Boolean available)
                                || !(args[1] instanceof Number value)
                                || !(args[2] instanceof Number scale)) {
                            return;
                        }
                        long sequence = SEQUENCE.incrementAndGet();
                        log("sequence=" + sequence
                                + " op=texture-callback"
                                + " view=" + describeView(view)
                                + " available=" + available
                                + " value=" + value.intValue()
                                + " scale=" + scale.floatValue()
                                + " passEnabled=" + passEnabled(view, getPassWindowBlurEnabled)
                                + " configuredScale=" + passScale(view, getPassTextureScale)
                                + " attached=" + view.isAttachedToWindow()
                                + " shown=" + view.isShown()
                                + " root=" + rootIdentity(view)
                                + " thread=" + Thread.currentThread().getName());
                    });
            return HookInstallResult.installed(HOOK_ID);
        } catch (ClassNotFoundException | NoSuchMethodException error) {
            return HookInstallResult.unsupported(
                    HOOK_ID, "framework notification consumer contract missing: " + error);
        } catch (Throwable error) {
            return HookInstallResult.failed(
                    HOOK_ID, "framework notification consumer probe registration failed", error);
        }
    }

    private static void observeRegistration(
            String operation,
            Object viewRoot,
            Object[] args,
            Method getView,
            Class<?> shadeWindowClass,
            Method getPassWindowBlurEnabled,
            Method getPassTextureScale) {
        try {
            Object rootObject = getView.invoke(viewRoot);
            if (!(rootObject instanceof View root) || !shadeWindowClass.isInstance(root)) return;
            if (args == null || args.length == 0 || !(args[0] instanceof View consumer)) return;
            long sequence = SEQUENCE.incrementAndGet();
            log("sequence=" + sequence
                    + " op=" + operation
                    + " view=" + describeView(consumer)
                    + " passEnabled=" + passEnabled(consumer, getPassWindowBlurEnabled)
                    + " configuredScale=" + passScale(consumer, getPassTextureScale)
                    + " attached=" + consumer.isAttachedToWindow()
                    + " shown=" + consumer.isShown()
                    + " root=" + describeView(root)
                    + " thread=" + Thread.currentThread().getName());
        } catch (Throwable ignored) {
        }
    }

    private static boolean isShadeRoot(View view, Class<?> shadeWindowClass) {
        try {
            View root = view.getRootView();
            return root != null && shadeWindowClass.isInstance(root);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean passEnabled(View view, Method getter) {
        try {
            Object enabled = getter.invoke(view);
            return enabled instanceof Boolean && (Boolean) enabled;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static float passScale(View view, Method getter) {
        try {
            Object configured = getter.invoke(view);
            return configured instanceof Number ? ((Number) configured).floatValue() : Float.NaN;
        } catch (Throwable ignored) {
            return Float.NaN;
        }
    }

    private static String describeView(View view) {
        return view.getClass().getName() + "@"
                + Integer.toHexString(System.identityHashCode(view));
    }

    private static String rootIdentity(View view) {
        try {
            View root = view.getRootView();
            return root == null ? "null" : describeView(root);
        } catch (Throwable ignored) {
            return "error";
        }
    }

    private static void log(String message) {
        try {
            Api101Bridge.log(LiquidUiLog.format(TAG + " " + message));
        } catch (Throwable ignored) {
            android.util.Log.i("LiquidUI", "[LUI]" + TAG + " " + message);
        }
    }
}
