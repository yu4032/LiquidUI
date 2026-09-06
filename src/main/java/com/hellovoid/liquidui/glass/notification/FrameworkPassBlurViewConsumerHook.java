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
 * Read-only observer for HyperOS' existing notification PassBlur/background-blur consumer.
 *
 * The exact SystemUI target injects a full-screen ShadeBackgroundView into
 * SharedNotificationContainer. HyperOS already owns the notification container's PassBlur texture
 * lifecycle, so this hook observes both exact participants instead of registering a second
 * consumer or treating the callback integer as a GLES texture before its semantics/context are
 * proven on-device.
 */
public final class FrameworkPassBlurViewConsumerHook implements SystemUiHook {
    public static final String HOOK_ID = "notification.framework-passblur-view-consumer-probe";
    private static final String TARGET_PROFILE = "systemui-001";
    private static final String FRAMEWORK_VIEW = "android.view.View";
    private static final String SHADE_BACKGROUND_VIEW =
            "com.miui.systemui.shade.ShadeBackgroundView";
    private static final String SHARED_NOTIFICATION_CONTAINER =
            "com.android.systemui.statusbar.notification.stack.ui.view.SharedNotificationContainer";
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
            Class<?> shadeBackgroundClass =
                    TargetClassResolver.require(classLoader, SHADE_BACKGROUND_VIEW);
            Class<?> sharedNotificationContainerClass =
                    TargetClassResolver.require(classLoader, SHARED_NOTIFICATION_CONTAINER);
            Method setTextureAvailable = viewClass.getDeclaredMethod(
                    "setTextureAvailable", boolean.class, int.class, float.class);
            Method getPassWindowBlurEnabled =
                    viewClass.getDeclaredMethod("getPassWindowBlurEnabled");
            Method getPassTextureScale = viewClass.getDeclaredMethod("getPassTextureScale");
            setTextureAvailable.setAccessible(true);
            getPassWindowBlurEnabled.setAccessible(true);
            getPassTextureScale.setAccessible(true);

            backend.intercept(
                    setTextureAvailable,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if ((!shadeBackgroundClass.isInstance(thisObject)
                                && !sharedNotificationContainerClass.isInstance(thisObject))
                                || !(thisObject instanceof View view)
                                || args.length < 3
                                || !(args[0] instanceof Boolean available)
                                || !(args[1] instanceof Number value)
                                || !(args[2] instanceof Number scale)) {
                            return;
                        }
                        boolean passEnabled = false;
                        float configuredScale = Float.NaN;
                        try {
                            Object enabled = getPassWindowBlurEnabled.invoke(thisObject);
                            if (enabled instanceof Boolean flag) passEnabled = flag;
                        } catch (Throwable ignored) {}
                        try {
                            Object configured = getPassTextureScale.invoke(thisObject);
                            if (configured instanceof Number number) {
                                configuredScale = number.floatValue();
                            }
                        } catch (Throwable ignored) {}
                        long sequence = SEQUENCE.incrementAndGet();
                        log("sequence=" + sequence
                                + " view=" + view.getClass().getName()
                                + "@" + Integer.toHexString(System.identityHashCode(view))
                                + " available=" + available
                                + " value=" + value.intValue()
                                + " scale=" + scale.floatValue()
                                + " passEnabled=" + passEnabled
                                + " configuredScale=" + configuredScale
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

    private static String rootIdentity(View view) {
        try {
            View root = view.getRootView();
            return root == null ? "null"
                    : root.getClass().getName() + "@"
                    + Integer.toHexString(System.identityHashCode(root));
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
