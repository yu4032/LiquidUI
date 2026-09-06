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
 * Notification material authority derived from the supplied MiuiSystemUI.apk and HyperLight APK.
 *
 * SystemUI chooses the exact notification element View through
 * NotificationUtil#applyElementViewBlend -> MiBlurCompat#setMiBackgroundBlendColors(View,int[],float).
 * HyperLight's non-screen-capture notification route hooks that setter before the original call,
 * adds HWUI element material state, then lets SystemUI keep its own notification blend/round state.
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

            // Supplied MiuiSystemUI.apk, classes2.dex.
            applyElementViewBlend = accessible(notificationUtil.getDeclaredMethod(
                    "applyElementViewBlend", Context.class, View.class,
                    boolean.class, int[].class, boolean.class));
            setMiBackgroundBlendColors = accessible(miBlurCompat.getDeclaredMethod(
                    "setMiBackgroundBlendColors", View.class, int[].class, float.class));
            setChildrenExpanded = accessible(childrenContainer.getDeclaredMethod(
                    "setChildrenExpanded", boolean.class));
            setRoundRect = accessible(notificationUtil.getDeclaredMethod(
                    "setRoundRect", View.class, boolean.class, boolean.class));

            // Supplied SystemUI's miuix MiuiBlurUtils/HyperBloomStrokeUtils resolve exactly these
            // framework View methods for HyperOS material rendering.
            Method setMixEffectEnabled = View.class.getMethod(
                    "setMixEffectEnabled", boolean.class);
            Method setMiViewBlurMode = View.class.getMethod(
                    "setMiViewBlurMode", int.class);
            Method setViewBackgroundBlendColors = View.class.getMethod(
                    "setMiBackgroundBlendColors", ArrayList.class);
            Method setMiBloomStroke = optionalPublicMethod(
                    View.class, "setMiBloomStroke", float[].class);

            targetRegistry = new NotificationMaterialTargetRegistry(rowClass);
            materialController = new NotificationVendorMaterialController(
                    setMixEffectEnabled,
                    setMiViewBlurMode,
                    setViewBackgroundBlendColors,
                    setMiBloomStroke);
            android.util.Log.i("LiquidUI",
                    "[LUI][NotifGlass][Hook] resolved verified material contract "
                            + "MiBlurCompat#setMiBackgroundBlendColors(View,int[],float) "
                            + "bloom=" + (setMiBloomStroke != null));
        } catch (ClassNotFoundException | NoSuchMethodException error) {
            android.util.Log.e("LiquidUI",
                    "[LUI][NotifGlass][Hook] exact notification material contract missing", error);
            return HookInstallResult.unsupported(HOOK_ID,
                    "exact notification material contract missing: " + error);
        } catch (Throwable error) {
            android.util.Log.e("LiquidUI",
                    "[LUI][NotifGlass][Hook] notification material contract resolution failed", error);
            return HookInstallResult.failed(HOOK_ID,
                    "notification material contract resolution failed", error);
        }

        List<Runnable> rollbacks = new ArrayList<>();
        try {
            // HyperLight authority scope: only setter calls originating inside notification blend.
            rollbacks.add(beforeBackend.intercept(
                    applyElementViewBlend,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> enterNotificationBlend())::unhook);
            rollbacks.add(afterBackend.intercept(
                    applyElementViewBlend,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> exitNotificationBlend())::unhook);

            // HyperLight NotificationBlurHook uses a BEFORE hook here. Apply only element material;
            // the original SystemUI setter must still run and remains blend-color authority.
            rollbacks.add(beforeBackend.intercept(
                    setMiBackgroundBlendColors,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (!inNotificationBlend()) return;
                        if (args.length < 3 || !(args[0] instanceof View target)
                                || !(args[1] instanceof int[] colors)
                                || !(args[2] instanceof Number ratio)) return;
                        Object row = targetRegistry.observeMaterialTarget(target);
                        materialController.applyHyperLightElementMaterial(
                                target, row, colors, ratio.floatValue());
                    })::unhook);

            // Keep SystemUI's own round/expanded state as geometry authority. Unlike HyperLight we
            // intentionally do not install a hard-coded 24dp OutlineProvider on this target.
            rollbacks.add(beforeBackend.intercept(
                    setChildrenExpanded,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    targetRegistry::observeChildrenExpanded)::unhook);
            rollbacks.add(afterBackend.intercept(
                    setRoundRect,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> targetRegistry.observeRoundRect(args))::unhook);

            android.util.Log.i("LiquidUI", "[LUI][NotifGlass][Hook] installed");
            return HookInstallResult.installed(HOOK_ID);
        } catch (Throwable error) {
            for (int index = rollbacks.size() - 1; index >= 0; index--) {
                try { rollbacks.get(index).run(); } catch (Throwable rollback) {
                    error.addSuppressed(rollback);
                }
            }
            android.util.Log.e("LiquidUI",
                    "[LUI][NotifGlass][Hook] registration failed", error);
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

    private static Method optionalPublicMethod(
            Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }
}
