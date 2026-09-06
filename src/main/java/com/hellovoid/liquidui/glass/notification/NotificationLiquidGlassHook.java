package com.hellovoid.liquidui.glass.notification;

import android.content.Context;
import android.view.View;

import com.hellovoid.liquidui.hook.AfterMethodHookBackend;
import com.hellovoid.liquidui.hook.BeforeMethodHookBackend;
import com.hellovoid.liquidui.hook.HookInstallResult;
import com.hellovoid.liquidui.hook.SystemUiHook;
import com.hellovoid.liquidui.reflect.TargetClassResolver;
import com.hellovoid.liquidui.target.SystemUiTargetProfile;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Notification material authority derived from the target MiuiSystemUI.apk.
 *
 * Ordinary notification rows are element consumers: ExpandableNotificationRowInjector#updateBlurBg
 * sends mBackgroundNormal through NotificationUtil#applyElementViewBlend, which then calls
 * MiBlurCompat#setMiBackgroundBlendColors(View, int[], float). LiquidUI observes that exact target
 * without inventing a caller-owned shade Surface endpoint or changing background PassBlur
 * ownership. Prismal/OES classes remain dormant until a framework pass-texture handoff is proven.
 */
public final class NotificationLiquidGlassHook implements SystemUiHook {
    public static final String HOOK_ID = "notification.liquid-glass";
    private static final String TARGET_PROFILE = "systemui-001";
    private static final String ROW =
            "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow";
    private static final String NOTIFICATION_UTIL =
            "com.android.systemui.statusbar.notification.utils.NotificationUtil";
    private static final String CHILDREN_CONTAINER =
            "com.android.systemui.statusbar.notification.stack.NotificationChildrenContainer";
    private static final String MI_BLUR_COMPAT = "com.miui.systemui.util.MiBlurCompat";

    private final BeforeMethodHookBackend beforeBackend;
    private final AfterMethodHookBackend afterBackend;
    private final boolean enabled;
    private final ThreadLocal<Integer> notificationBlendDepth = ThreadLocal.withInitial(() -> 0);

    public NotificationLiquidGlassHook(
            BeforeMethodHookBackend beforeBackend,
            AfterMethodHookBackend afterBackend,
            boolean enabled) {
        this.beforeBackend = Objects.requireNonNull(beforeBackend, "beforeBackend");
        this.afterBackend = Objects.requireNonNull(afterBackend, "afterBackend");
        this.enabled = enabled;
    }

    @Override public String id() { return HOOK_ID; }

    @Override
    public HookInstallResult install(ClassLoader classLoader, SystemUiTargetProfile profile) {
        Objects.requireNonNull(classLoader, "classLoader");
        Objects.requireNonNull(profile, "profile");
        if (!enabled) return HookInstallResult.disabled(HOOK_ID);
        if (!TARGET_PROFILE.equals(profile.id())) {
            return HookInstallResult.unsupported(HOOK_ID, "profile=" + profile.id());
        }

        final Method applyElementViewBlend;
        final Method setMiBackgroundBlendColors;
        final Method setChildrenExpanded;
        final Method setRoundRect;
        final NotificationMaterialTargetRegistry targetRegistry;
        final NotificationVendorMaterialController materialController;
        try {
            Class<?> rowClass = TargetClassResolver.require(classLoader, ROW);
            Class<?> notificationUtil = TargetClassResolver.require(classLoader, NOTIFICATION_UTIL);
            Class<?> childrenContainer = TargetClassResolver.require(classLoader, CHILDREN_CONTAINER);
            Class<?> miBlurCompat = TargetClassResolver.require(classLoader, MI_BLUR_COMPAT);

            // Verified from the file-library MiuiSystemUI.apk (classes2.dex).
            applyElementViewBlend = accessible(notificationUtil.getDeclaredMethod(
                    "applyElementViewBlend", Context.class, View.class,
                    boolean.class, int[].class, boolean.class));
            setMiBackgroundBlendColors = accessible(miBlurCompat.getDeclaredMethod(
                    "setMiBackgroundBlendColors", View.class, int[].class, float.class));
            setChildrenExpanded = accessible(childrenContainer.getDeclaredMethod(
                    "setChildrenExpanded", boolean.class));
            setRoundRect = accessible(notificationUtil.getDeclaredMethod(
                    "setRoundRect", View.class, boolean.class, boolean.class));

            targetRegistry = new NotificationMaterialTargetRegistry(rowClass);
            materialController = new NotificationVendorMaterialController();
        } catch (ClassNotFoundException | NoSuchMethodException error) {
            return HookInstallResult.unsupported(HOOK_ID,
                    "exact notification material contract missing: " + error);
        } catch (Throwable error) {
            return HookInstallResult.failed(HOOK_ID,
                    "notification material contract resolution failed", error);
        }

        List<Runnable> rollbacks = new ArrayList<>();
        try {
            // Scope only MiBlurCompat calls made by SystemUI notification material dispatch.
            rollbacks.add(beforeBackend.intercept(
                    applyElementViewBlend,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> enterNotificationBlend())::unhook);
            rollbacks.add(afterBackend.intercept(
                    applyElementViewBlend,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> exitNotificationBlend())::unhook);

            // SystemUI has already established the native element consumer state when this after
            // callback runs. Observe the exact View and preserve that state unchanged.
            rollbacks.add(afterBackend.intercept(
                    setMiBackgroundBlendColors,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (!inNotificationBlend()) return;
                        if (args.length < 3 || !(args[0] instanceof View target)
                                || !(args[1] instanceof int[] colors)
                                || !(args[2] instanceof Number ratio)) return;
                        Object row = targetRegistry.observeMaterialTarget(target);
                        if (row == null) return;
                        materialController.observeSystemMaterial(
                                target, row, colors, ratio.floatValue());
                    })::unhook);

            // Final round-state authority is SystemUI's own setRoundRect dispatch. Keep the group
            // expansion state alongside it for later Prismal geometry handoff.
            rollbacks.add(beforeBackend.intercept(
                    setChildrenExpanded,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    targetRegistry::observeChildrenExpanded)::unhook);
            rollbacks.add(afterBackend.intercept(
                    setRoundRect,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> targetRegistry.observeRoundRect(args))::unhook);

            return HookInstallResult.installed(HOOK_ID);
        } catch (Throwable error) {
            for (int index = rollbacks.size() - 1; index >= 0; index--) {
                try { rollbacks.get(index).run(); } catch (Throwable rollback) {
                    error.addSuppressed(rollback);
                }
            }
            return HookInstallResult.failed(HOOK_ID,
                    "notification material hook registration failed", error);
        }
    }

    private void enterNotificationBlend() {
        notificationBlendDepth.set(notificationBlendDepth.get() + 1);
    }

    private void exitNotificationBlend() {
        int depth = notificationBlendDepth.get() - 1;
        if (depth <= 0) notificationBlendDepth.remove();
        else notificationBlendDepth.set(depth);
    }

    private boolean inNotificationBlend() {
        return notificationBlendDepth.get() > 0;
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }
}
