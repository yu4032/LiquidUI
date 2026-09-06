package com.hellovoid.liquidui.glass.notification;

import android.view.View;

import com.hellovoid.liquidui.hook.AfterMethodHookBackend;
import com.hellovoid.liquidui.hook.BeforeMethodHookBackend;
import com.hellovoid.liquidui.hook.HookInstallResult;
import com.hellovoid.liquidui.hook.SystemUiHook;
import com.hellovoid.liquidui.reflect.TargetClassResolver;
import com.hellovoid.liquidui.target.SystemUiTargetProfile;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Notification material hook derived from the supplied MiuiSystemUI.apk and HyperLight APK.
 *
 * On the target SystemUI build, updateBlurBg(int,int,boolean) is the stable outer notification
 * material boundary. Its internal static calls to NotificationUtil/MiBlurCompat can bypass
 * libxposed interception, so LiquidUI applies the HyperLight element material AFTER updateBlurBg
 * finishes, directly to the exact mBackgroundNormal View chosen by SystemUI.
 */
public final class NotificationLiquidGlassHook implements SystemUiHook {
    public static final String HOOK_ID = "notification.liquid-glass";
    private static final String TARGET_PROFILE = "systemui-001";
    private static final String ROW =
            "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow";
    private static final String ROW_INJECTOR =
            "com.android.systemui.statusbar.notification.row.ExpandableNotificationRowInjector";
    private static final String NOTIFICATION_UTIL =
            "com.android.systemui.statusbar.notification.utils.NotificationUtil";
    private static final String CHILDREN_CONTAINER =
            "com.android.systemui.statusbar.notification.stack.NotificationChildrenContainer";

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

        final Method updateBlurBg;
        final Method setChildrenExpanded;
        final Method setRoundRect;
        final Field injectorViewField;
        final Field backgroundNormalField;
        final NotificationMaterialTargetRegistry targetRegistry;
        final NotificationVendorMaterialController materialController;
        try {
            Class<?> rowClass = TargetClassResolver.require(classLoader, ROW);
            Class<?> injectorClass = TargetClassResolver.require(classLoader, ROW_INJECTOR);
            Class<?> notificationUtil = TargetClassResolver.require(classLoader, NOTIFICATION_UTIL);
            Class<?> childrenContainer = TargetClassResolver.require(classLoader, CHILDREN_CONTAINER);

            // Supplied MiuiSystemUI.apk, ExpandableNotificationRowInjector.java:
            // public final void updateBlurBg(int, int, boolean)
            updateBlurBg = accessible(injectorClass.getDeclaredMethod(
                    "updateBlurBg", int.class, int.class, boolean.class));
            injectorViewField = accessible(injectorClass.getDeclaredField("view"));
            backgroundNormalField = accessible(rowClass.getDeclaredField("mBackgroundNormal"));
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
                    "[LUI][NotifGlass][Hook] resolved outer material authority "
                            + "ExpandableNotificationRowInjector#updateBlurBg(int,int,boolean) "
                            + "bloom=" + (setMiBloomStroke != null));
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException error) {
            android.util.Log.e("LiquidUI",
                    "[LUI][NotifGlass][Hook] notification material contract missing", error);
            return HookInstallResult.unsupported(HOOK_ID,
                    "notification material contract missing: " + error);
        } catch (Throwable error) {
            android.util.Log.e("LiquidUI",
                    "[LUI][NotifGlass][Hook] material contract resolution failed", error);
            return HookInstallResult.failed(HOOK_ID,
                    "notification material contract resolution failed", error);
        }

        List<Runnable> rollbacks = new ArrayList<>();
        try {
            // Apply AFTER SystemUI has finished setCustomBackground / setRoundRect / view blur /
            // clear+add blend calls so its internal static implementation cannot overwrite us.
            rollbacks.add(afterBackend.intercept(
                    updateBlurBg,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        try {
                            if (thisObject == null || args.length < 3) return;
                            Object row = injectorViewField.get(thisObject);
                            if (row == null) return;
                            Object background = backgroundNormalField.get(row);
                            if (!(background instanceof View target)) return;

                            Object registeredRow = targetRegistry.observeMaterialTarget(target);
                            if (registeredRow == null) return;

                            boolean requestedBlur = args[2] instanceof Boolean && (Boolean) args[2];
                            materialController.applyHyperLightElementMaterial(
                                    target, registeredRow, requestedBlur);
                        } catch (Throwable error) {
                            android.util.Log.e("LiquidUI",
                                    "[LUI][NotifGlass][Hook] updateBlurBg material apply failed", error);
                        }
                    })::unhook);

            // Keep SystemUI round/expanded dispatch for future Prismal geometry handoff.
            rollbacks.add(beforeBackend.intercept(
                    setChildrenExpanded,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    targetRegistry::observeChildrenExpanded)::unhook);
            rollbacks.add(afterBackend.intercept(
                    setRoundRect,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> targetRegistry.observeRoundRect(args))::unhook);

            android.util.Log.i("LiquidUI",
                    "[LUI][NotifGlass][Hook] installed updateBlurBg material hook");
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
