package com.hellovoid.liquidui.glass.notification;

import android.view.View;

import com.hellovoid.liquidui.hook.AfterMethodHookBackend;
import com.hellovoid.liquidui.hook.ArgumentRewriteHookBackend;
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
 * Notification glass hook derived from the supplied MiuiSystemUI.apk and HyperLight APK.
 *
 * The target build calls ExpandableNotificationRowInjector#updateBackground$1() from multiple
 * classes. LiquidUI hooks this cross-class outer boundary AFTER SystemUI finishes, resolves the
 * exact inherited row instance and mBackgroundNormal, clears SystemUI's element material, then
 * applies native card PassBlur plus LiquidUI mix/blend/bloom. The large SystemUI shade backdrop
 * blur is kept off so blur ownership stays on each notification card.
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
    private static final String SHADE_BLUR_PROVIDER =
            "com.miui.systemui.shade.blur.ShadeBlendBlurController$BlurProvider";
    private static final String SHADE_BLEND_BACKGROUND =
            "com.miui.systemui.shade.blur.ShadeBlendBlurController$BlendBackground";
    private static final String SHADE_WINDOW =
            "com.android.systemui.shade.NotificationShadeWindowView";
    private static final String NOTIFICATION_PANEL =
            "com.android.systemui.shade.NotificationPanelView";
    private static final String BLUR_UTILS =
            "com.android.systemui.statusbar.BlurUtils";
    private static final String VIEW_ROOT_IMPL = "android.view.ViewRootImpl";

    private final BeforeMethodHookBackend beforeBackend;
    private final AfterMethodHookBackend afterBackend;
    private final ArgumentRewriteHookBackend argumentBackend;
    private final boolean enabled;

    public NotificationLiquidGlassHook(
            BeforeMethodHookBackend beforeBackend,
            AfterMethodHookBackend afterBackend,
            ArgumentRewriteHookBackend argumentBackend,
            boolean enabled) {
        this.beforeBackend = Objects.requireNonNull(beforeBackend, "beforeBackend");
        this.afterBackend = Objects.requireNonNull(afterBackend, "afterBackend");
        this.argumentBackend = Objects.requireNonNull(argumentBackend, "argumentBackend");
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

        final Method updateBackground;
        final Method setChildrenExpanded;
        final Method setRoundRect;
        final Method blurProviderSetRatio;
        final Method blendBackgroundSetEnabled;
        final Method blurUtilsApplyBlur;
        final Method viewRootGetView;
        final Method setMiBackgroundBlurMode;
        final Field injectorViewField;
        final Field backgroundNormalField;
        final Field blurProviderView;
        final Field blendBackgroundView;
        final Class<?> shadeWindowClass;
        final Class<?> notificationPanelClass;
        final NotificationMaterialTargetRegistry targetRegistry;
        final NotificationVendorMaterialController materialController;
        try {
            Class<?> rowClass = TargetClassResolver.require(classLoader, ROW);
            Class<?> injectorClass = TargetClassResolver.require(classLoader, ROW_INJECTOR);
            Class<?> notificationUtil = TargetClassResolver.require(classLoader, NOTIFICATION_UTIL);
            Class<?> childrenContainer = TargetClassResolver.require(classLoader, CHILDREN_CONTAINER);
            Class<?> blurProviderClass = TargetClassResolver.require(classLoader, SHADE_BLUR_PROVIDER);
            Class<?> blendBackgroundClass = TargetClassResolver.require(classLoader, SHADE_BLEND_BACKGROUND);
            shadeWindowClass = TargetClassResolver.require(classLoader, SHADE_WINDOW);
            notificationPanelClass = TargetClassResolver.require(classLoader, NOTIFICATION_PANEL);
            Class<?> blurUtilsClass = TargetClassResolver.require(classLoader, BLUR_UTILS);
            Class<?> viewRootImplClass = TargetClassResolver.require(classLoader, VIEW_ROOT_IMPL);

            updateBackground = accessible(injectorClass.getDeclaredMethod("updateBackground$1"));

            // Verified from supplied MiuiSystemUI.apk:
            // ExpandableNotificationRowInjector inherits public final ExpandableViewInjector#view.
            // ExpandableNotificationRow inherits public ActivatableNotificationView#mBackgroundNormal.
            injectorViewField = accessible(injectorClass.getField("view"));
            backgroundNormalField = accessible(rowClass.getField("mBackgroundNormal"));
            setChildrenExpanded = accessible(childrenContainer.getDeclaredMethod(
                    "setChildrenExpanded", boolean.class));
            setRoundRect = accessible(notificationUtil.getDeclaredMethod(
                    "setRoundRect", View.class, boolean.class, boolean.class));

            Method setMixEffectEnabled = View.class.getMethod(
                    "setMixEffectEnabled", boolean.class);
            Method setMiViewBlurMode = View.class.getMethod(
                    "setMiViewBlurMode", int.class);
            Method clearMiBackgroundBlendColor = View.class.getMethod(
                    "clearMiBackgroundBlendColor");
            Method setViewBackgroundBlendColors = View.class.getMethod(
                    "setMiBackgroundBlendColors", ArrayList.class);
            Method setMiBloomStroke = optionalPublicMethod(
                    View.class, "setMiBloomStroke", float[].class);

            // These hooks affect only the notification shade backdrop. Card-level PassBlur is
            // independently owned by NotificationBackgroundView in the material controller.
            blurProviderSetRatio = accessible(blurProviderClass.getDeclaredMethod(
                    "setBlurRatio", float.class));
            blendBackgroundSetEnabled = accessible(blendBackgroundClass.getDeclaredMethod(
                    "setEnabled", boolean.class));
            blurUtilsApplyBlur = accessible(blurUtilsClass.getDeclaredMethod(
                    "applyBlur", viewRootImplClass, int.class, boolean.class));
            viewRootGetView = accessible(viewRootImplClass.getDeclaredMethod("getView"));
            blurProviderView = findField(blurProviderClass, "view");
            blendBackgroundView = findField(blendBackgroundClass, "view");
            setMiBackgroundBlurMode = View.class.getMethod(
                    "setMiBackgroundBlurMode", int.class);

            targetRegistry = new NotificationMaterialTargetRegistry(rowClass);
            materialController = new NotificationVendorMaterialController(
                    setMixEffectEnabled,
                    setMiViewBlurMode,
                    clearMiBackgroundBlendColor,
                    setViewBackgroundBlendColors,
                    setMiBloomStroke);

            android.util.Log.i("LiquidUI",
                    "[LUI][NotifGlass][Hook] resolved notification glass authority "
                            + "viewOwner=ExpandableViewInjector backgroundOwner=ActivatableNotificationView "
                            + "bloom=" + (setMiBloomStroke != null));
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException error) {
            android.util.Log.e("LiquidUI",
                    "[LUI][NotifGlass][Hook] notification glass contract missing", error);
            return HookInstallResult.unsupported(HOOK_ID,
                    "notification glass contract missing: " + error);
        } catch (Throwable error) {
            android.util.Log.e("LiquidUI",
                    "[LUI][NotifGlass][Hook] glass contract resolution failed", error);
            return HookInstallResult.failed(HOOK_ID,
                    "notification glass contract resolution failed", error);
        }

        List<Runnable> rollbacks = new ArrayList<>();
        try {
            // Apply AFTER SystemUI has completed its background update. Decompiled
            // NotificationUtil#applyElementViewBlend leaves mBackgroundNormal with view-blur mode=1
            // plus SystemUI blend colors. Remove exactly those defaults before LiquidUI owns it.
            rollbacks.add(afterBackend.intercept(
                    updateBackground,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        try {
                            if (thisObject == null) return;
                            Object row = injectorViewField.get(thisObject);
                            if (row == null) return;
                            Object background = backgroundNormalField.get(row);
                            if (!(background instanceof View target)) return;

                            Object registeredRow = targetRegistry.observeMaterialTarget(target);
                            if (registeredRow == null) return;
                            materialController.suppressSystemUiElementMaterial(target);
                            materialController.applyHyperLightElementMaterial(target, registeredRow);
                        } catch (Throwable error) {
                            android.util.Log.e("LiquidUI",
                                    "[LUI][NotifGlass][Hook] updateBackground material apply failed",
                                    error);
                        }
                    })::unhook);

            rollbacks.add(beforeBackend.intercept(
                    setChildrenExpanded,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    targetRegistry::observeChildrenExpanded)::unhook);
            rollbacks.add(afterBackend.intercept(
                    setRoundRect,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> targetRegistry.observeRoundRect(args))::unhook);

            // Keep the large SystemUI shade blur disabled so blur ownership remains card-local.
            rollbacks.add(argumentBackend.intercept(
                    blurProviderSetRatio,
                    ArgumentRewriteHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (args.length == 0 || !(args[0] instanceof Float requested)) return;
                        Object target = blurProviderView.get(thisObject);
                        if (!isShadeBlurTarget(target, shadeWindowClass, notificationPanelClass)) return;
                        args[0] = NotificationShadeBlurPolicy.blurRatio(true, requested);
                        if (target instanceof View view) {
                            setMiBackgroundBlurMode.invoke(view, 0);
                        }
                    })::unhook);

            rollbacks.add(argumentBackend.intercept(
                    blendBackgroundSetEnabled,
                    ArgumentRewriteHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (args.length == 0 || !(args[0] instanceof Boolean requested)) return;
                        Object target = blendBackgroundView.get(thisObject);
                        if (!isShadeBlendTarget(target, shadeWindowClass, notificationPanelClass)) return;
                        args[0] = NotificationShadeBlurPolicy.enabled(true, requested);
                    })::unhook);

            rollbacks.add(argumentBackend.intercept(
                    blurUtilsApplyBlur,
                    ArgumentRewriteHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (args.length < 2 || !(args[1] instanceof Integer requested)) return;
                        Object rootView = args[0] == null ? null : viewRootGetView.invoke(args[0]);
                        if (!shadeWindowClass.isInstance(rootView)) return;
                        args[1] = NotificationShadeBlurPolicy.blurRadius(true, requested);
                    })::unhook);

            android.util.Log.i("LiquidUI",
                    "[LUI][NotifGlass][Hook] installed notification glass hooks");
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
                    "notification glass hook registration failed", error);
        }
    }

    private static boolean isShadeBlurTarget(
            Object value, Class<?> shadeWindowClass, Class<?> notificationPanelClass) {
        return shadeWindowClass.isInstance(value) || notificationPanelClass.isInstance(value);
    }

    private static boolean isShadeBlendTarget(
            Object value, Class<?> shadeWindowClass, Class<?> notificationPanelClass) {
        if (!(value instanceof View view)) return false;
        Object parent = view.getParent();
        return shadeWindowClass.isInstance(parent) || notificationPanelClass.isInstance(parent);
    }

    private static Field findField(Class<?> owner, String name) throws NoSuchFieldException {
        Class<?> current = owner;
        while (current != null) {
            try {
                return accessible(current.getDeclaredField(name));
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(owner.getName() + "#" + name);
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
