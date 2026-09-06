package com.hellovoid.liquidui.glass.notification;

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
 * The final notification element material call in this SystemUI build is
 * MiBlurCompat#setMiBackgroundBlendColors(View,int[],float). Do not depend on a hook of
 * NotificationUtil#applyElementViewBlend as a scope boundary: updateBlurBg commonly enters an
 * overloaded helper and ART may not route the helper-to-helper call through an external hook.
 * Instead accept the final target only when it is a NotificationBackgroundView or belongs to an
 * ExpandableNotificationRow.
 */
public final class NotificationLiquidGlassHook implements SystemUiHook {
    public static final String HOOK_ID = "notification.liquid-glass";
    private static final String TARGET_PROFILE = "systemui-001";
    private static final String ROW =
            "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow";
    private static final String NOTIFICATION_BACKGROUND =
            "com.android.systemui.statusbar.notification.row.NotificationBackgroundView";
    private static final String NOTIFICATION_UTIL =
            "com.android.systemui.statusbar.notification.utils.NotificationUtil";
    private static final String CHILDREN_CONTAINER =
            "com.android.systemui.statusbar.notification.stack.NotificationChildrenContainer";
    private static final String MI_BLUR_COMPAT = "com.miui.systemui.util.MiBlurCompat";

    private final BeforeMethodHookBackend beforeBackend;
    private final AfterMethodHookBackend afterBackend;
    private final boolean enabled;

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

        final Method setMiBackgroundBlendColors;
        final Method setChildrenExpanded;
        final Method setRoundRect;
        final Class<?> notificationBackgroundClass;
        final NotificationMaterialTargetRegistry targetRegistry;
        final NotificationVendorMaterialController materialController;
        try {
            Class<?> rowClass = TargetClassResolver.require(classLoader, ROW);
            notificationBackgroundClass = TargetClassResolver.require(classLoader, NOTIFICATION_BACKGROUND);
            Class<?> notificationUtil = TargetClassResolver.require(classLoader, NOTIFICATION_UTIL);
            Class<?> childrenContainer = TargetClassResolver.require(classLoader, CHILDREN_CONTAINER);
            Class<?> miBlurCompat = TargetClassResolver.require(classLoader, MI_BLUR_COMPAT);

            // Supplied MiuiSystemUI.apk, classes2.dex: final element-material setter.
            setMiBackgroundBlendColors = accessible(miBlurCompat.getDeclaredMethod(
                    "setMiBackgroundBlendColors", View.class, int[].class, float.class));
            setChildrenExpanded = accessible(childrenContainer.getDeclaredMethod(
                    "setChildrenExpanded", boolean.class));
            setRoundRect = accessible(notificationUtil.getDeclaredMethod(
                    "setRoundRect", View.class, boolean.class, boolean.class));

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
                    "[LUI][NotifGlass][Hook] resolved direct final-material authority "
                            + "MiBlurCompat#setMiBackgroundBlendColors(View,int[],float) "
                            + "target=" + notificationBackgroundClass.getName()
                            + " bloom=" + (setMiBloomStroke != null));
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
            // Hook the FINAL SystemUI material setter directly. A target is accepted only if it is
            // the exact NotificationBackgroundView class or can be walked back to a notification row.
            rollbacks.add(beforeBackend.intercept(
                    setMiBackgroundBlendColors,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (args.length < 3 || !(args[0] instanceof View target)
                                || !(args[1] instanceof int[] colors)
                                || !(args[2] instanceof Number ratio)) return;
                        Object row = targetRegistry.observeMaterialTarget(target);
                        if (row == null && !notificationBackgroundClass.isInstance(target)) return;
                        materialController.applyHyperLightElementMaterial(
                                target, row, colors, ratio.floatValue());
                    })::unhook);

            // Preserve the target SystemUI's own final geometry/round authority.
            rollbacks.add(beforeBackend.intercept(
                    setChildrenExpanded,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    targetRegistry::observeChildrenExpanded)::unhook);
            rollbacks.add(afterBackend.intercept(
                    setRoundRect,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> targetRegistry.observeRoundRect(args))::unhook);

            android.util.Log.i("LiquidUI", "[LUI][NotifGlass][Hook] installed direct material hook");
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
