package com.hellovoid.liquidui.glass.notification;

import android.view.View;

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

/** Installs one shared PassBlur/Prismal compositor for every visible notification row in NSSL. */
public final class NotificationLiquidGlassHook implements SystemUiHook {
    public static final String HOOK_ID = "notification.liquid-glass";
    private static final String TARGET_PROFILE = "systemui-001";
    private static final String ROW =
            "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow";
    private static final String ACTIVATABLE =
            "com.android.systemui.statusbar.notification.row.ActivatableNotificationView";
    private static final String BACKGROUND =
            "com.android.systemui.statusbar.notification.row.NotificationBackgroundView";
    private static final String STACK =
            "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout";
    private static final String WRAPPER =
            "com.android.systemui.statusbar.notification.row.wrapper.NotificationViewWrapper";
    private static final String MIUI_TEMPLATE =
            "com.android.systemui.statusbar.notification.row.wrapper.MiuiNotificationTemplateViewWrapper";
    private static final String MIUI_BIG_TEXT =
            "com.android.systemui.statusbar.notification.row.wrapper.MiuiNotificationBigTextViewWrapper";
    private static final String MIUI_CUSTOM =
            "com.android.systemui.statusbar.notification.row.wrapper.MiuiNotificationCustomViewWrapper";
    private static final String MI_BLUR_COMPAT = "com.miui.systemui.util.MiBlurCompat";
    private static final String SHADE_BLUR_PROVIDER =
            "com.miui.systemui.shade.blur.ShadeBlendBlurController$BlurProvider";
    private static final String SHADE_BLEND_BACKGROUND =
            "com.miui.systemui.shade.blur.ShadeBlendBlurController$BlendBackground";
    private static final String SHADE_WINDOW =
            "com.android.systemui.shade.NotificationShadeWindowView";
    private static final String NOTIFICATION_PANEL =
            "com.android.systemui.shade.NotificationPanelView";
    private static final String BLUR_UTILS = "com.android.systemui.statusbar.BlurUtils";
    private static final String VIEW_ROOT_IMPL = "android.view.ViewRootImpl";
    private static final String FRAMEWORK_VIEW = "android.view.View";
    private static final String ROOT_TASK_DISPLAY_AREA =
            "com.android.wm.shell.RootTaskDisplayAreaOrganizer";
    private static final String DISPLAY_AREA_INFO = "android.window.DisplayAreaInfo";

    private final BeforeMethodHookBackend backend;
    private final ArgumentRewriteHookBackend argumentBackend;
    private final boolean enabled;

    public NotificationLiquidGlassHook(
            BeforeMethodHookBackend backend,
            ArgumentRewriteHookBackend argumentBackend,
            boolean enabled) {
        this.backend = Objects.requireNonNull(backend, "backend");
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

        final NotificationGlassRuntime runtime;
        final NotificationGlassActivityState activityState = new NotificationGlassActivityState();
        final NotificationPassBlurAuthorityState authorityState =
                new NotificationPassBlurAuthorityState();
        final NotificationPassBlurSourceState sourceState =
                new NotificationPassBlurSourceState();
        final Method rowAttached;
        final Method rowDetached;
        final List<Method> wrapperReinflated;
        final Method blurProviderSetRatio;
        final Method blendBackgroundSetEnabled;
        final Method setPassWindowBlurEnabled;
        final Method setMiBackgroundBlurMode;
        final Method blurUtilsApplyBlur;
        final Method viewRootGetView;
        final Method displayAreaAppeared;
        final Method displayAreaVanished;
        final Field displayAreaDisplayId;
        final Field blurProviderView;
        final Field blurProviderPassBlur;
        final Field blendBackgroundView;
        final Class<?> shadeWindowClass;
        final Class<?> notificationPanelClass;
        try {
            Class<?> rowClass = TargetClassResolver.require(classLoader, ROW);
            Class<?> activatableClass = TargetClassResolver.require(classLoader, ACTIVATABLE);
            Class<?> backgroundClass = TargetClassResolver.require(classLoader, BACKGROUND);
            Class<?> stackClass = TargetClassResolver.require(classLoader, STACK);
            Class<?> wrapperClass = TargetClassResolver.require(classLoader, WRAPPER);
            Class<?> miuiTemplate = TargetClassResolver.require(classLoader, MIUI_TEMPLATE);
            Class<?> miuiBigText = TargetClassResolver.require(classLoader, MIUI_BIG_TEXT);
            Class<?> miuiCustom = TargetClassResolver.require(classLoader, MIUI_CUSTOM);
            Class<?> miBlurCompat = TargetClassResolver.require(classLoader, MI_BLUR_COMPAT);
            Class<?> blurProviderClass = TargetClassResolver.require(classLoader, SHADE_BLUR_PROVIDER);
            Class<?> blendBackgroundClass = TargetClassResolver.require(classLoader, SHADE_BLEND_BACKGROUND);
            shadeWindowClass = TargetClassResolver.require(classLoader, SHADE_WINDOW);
            notificationPanelClass = TargetClassResolver.require(classLoader, NOTIFICATION_PANEL);
            Class<?> blurUtilsClass = TargetClassResolver.require(classLoader, BLUR_UTILS);
            Class<?> viewRootImplClass = TargetClassResolver.require(classLoader, VIEW_ROOT_IMPL);
            Class<?> frameworkViewClass = TargetClassResolver.require(classLoader, FRAMEWORK_VIEW);
            Class<?> rootTaskDisplayAreaClass = TargetClassResolver.require(classLoader, ROOT_TASK_DISPLAY_AREA);
            Class<?> displayAreaInfoClass = TargetClassResolver.require(classLoader, DISPLAY_AREA_INFO);

            rowAttached = accessible(rowClass.getDeclaredMethod("onAttachedToWindow"));
            rowDetached = accessible(rowClass.getDeclaredMethod("onDetachedFromWindow"));
            wrapperReinflated = List.of(
                    accessible(wrapperClass.getDeclaredMethod("onReinflated")),
                    accessible(miuiTemplate.getDeclaredMethod("onReinflated")),
                    accessible(miuiBigText.getDeclaredMethod("onReinflated")),
                    accessible(miuiCustom.getDeclaredMethod("onReinflated")));

            Field backgroundNormal = accessible(activatableClass.getDeclaredField("mBackgroundNormal"));
            Field actualWidth = accessible(backgroundClass.getDeclaredField("mActualWidth"));
            Field actualHeight = accessible(backgroundClass.getDeclaredField("mActualHeight"));
            Field clipTop = accessible(backgroundClass.getDeclaredField("mClipTopAmount"));
            Field clipBottom = accessible(backgroundClass.getDeclaredField("mClipBottomAmount"));
            Method topCornerRadius = accessible(rowClass.getMethod("getTopCornerRadius"));
            Method bottomCornerRadius = accessible(rowClass.getMethod("getBottomCornerRadius"));
            Field expandRunning = accessible(backgroundClass.getDeclaredField("mExpandAnimationRunning"));
            Field expandWidth = accessible(backgroundClass.getDeclaredField("mExpandAnimationWidth"));
            Field expandHeight = accessible(backgroundClass.getDeclaredField("mExpandAnimationHeight"));
            Field wrapperView = accessible(wrapperClass.getDeclaredField("mView"));
            Field wrapperRow = accessible(wrapperClass.getDeclaredField("mRow"));

            Method disableBlur = accessible(miBlurCompat.getDeclaredMethod(
                    "setMiViewBlurModeCompat", int.class, View.class));
            Method clearBlend = accessible(miBlurCompat.getDeclaredMethod(
                    "clearMiBackgroundBlendColorCompat", View.class));

            blurProviderSetRatio = accessible(blurProviderClass.getDeclaredMethod(
                    "setBlurRatio", float.class));
            blurProviderView = accessible(blurProviderClass.getDeclaredField("view"));
            blurProviderPassBlur = accessible(blurProviderClass.getDeclaredField("passBlur"));
            blendBackgroundSetEnabled = accessible(blendBackgroundClass.getDeclaredMethod(
                    "setEnabled", boolean.class));
            blendBackgroundView = accessible(blendBackgroundClass.getDeclaredField("view"));
            setPassWindowBlurEnabled = accessible(frameworkViewClass.getDeclaredMethod(
                    "setPassWindowBlurEnabled", boolean.class));
            setMiBackgroundBlurMode = accessible(frameworkViewClass.getDeclaredMethod(
                    "setMiBackgroundBlurMode", int.class));
            blurUtilsApplyBlur = accessible(blurUtilsClass.getDeclaredMethod(
                    "applyBlur", viewRootImplClass, int.class, boolean.class));
            viewRootGetView = accessible(viewRootImplClass.getDeclaredMethod("getView"));
            displayAreaAppeared = accessible(rootTaskDisplayAreaClass.getDeclaredMethod(
                    "onDisplayAreaAppeared", displayAreaInfoClass, android.view.SurfaceControl.class));
            displayAreaVanished = accessible(rootTaskDisplayAreaClass.getDeclaredMethod(
                    "onDisplayAreaVanished", displayAreaInfoClass));
            displayAreaDisplayId = accessible(displayAreaInfoClass.getDeclaredField("displayId"));

            NotificationGlassNodeCollector collector = new NotificationGlassNodeCollector(
                    rowClass,
                    backgroundNormal,
                    actualWidth,
                    actualHeight,
                    clipTop,
                    clipBottom,
                    topCornerRadius,
                    bottomCornerRadius,
                    expandRunning,
                    expandWidth,
                    expandHeight);
            NotificationVendorMaterialController materialController =
                    new NotificationVendorMaterialController(
                            collector,
                            rowClass,
                            wrapperView,
                            wrapperRow,
                            disableBlur,
                            clearBlend);
            runtime = new NotificationGlassRuntime(
                    stackClass, collector, materialController, activityState, authorityState, sourceState);
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException error) {
            return HookInstallResult.unsupported(HOOK_ID, "exact notification glass contract missing: " + error);
        } catch (Throwable error) {
            return HookInstallResult.failed(HOOK_ID, "notification glass contract resolution failed", error);
        }

        List<Runnable> rollbacks = new ArrayList<>();
        try {
            rollbacks.add(backend.intercept(
                    rowAttached,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (thisObject instanceof View row) {
                            row.post(() -> runtime.onRowAttached(thisObject));
                        }
                    })::unhook);
            rollbacks.add(backend.intercept(
                    rowDetached,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> runtime.onRowDetached(thisObject))::unhook);
            for (Method method : wrapperReinflated) {
                rollbacks.add(backend.intercept(
                        method,
                        BeforeMethodHookBackend.PRIORITY_HIGHEST,
                        (thisObject, args) -> runtime.onWrapperObserved(thisObject))::unhook);
            }

            rollbacks.add(backend.intercept(
                    displayAreaAppeared,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (args.length < 2 || !(args[1] instanceof android.view.SurfaceControl source)) return;
                        int displayId = displayAreaDisplayId.getInt(args[0]);
                        sourceState.observe(displayId, source);
                    })::unhook);
            rollbacks.add(backend.intercept(
                    displayAreaVanished,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (args.length == 0 || args[0] == null) return;
                        sourceState.remove(displayAreaDisplayId.getInt(args[0]), null);
                    })::unhook);

            rollbacks.add(argumentBackend.intercept(
                    blurProviderSetRatio,
                    ArgumentRewriteHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        Object target = blurProviderView.get(thisObject);
                        if (notificationPanelClass.isInstance(target)) {
                            authorityState.observe(blurProviderPassBlur.getBoolean(thisObject));
                        }
                        if (!activityState.isActive() || args.length == 0 || !(args[0] instanceof Float ratio)) {
                            return;
                        }
                        if (!isShadeBlurTarget(target, shadeWindowClass, notificationPanelClass)) return;
                        args[0] = NotificationShadeBlurPolicy.blurRatio(true, ratio);
                        setMiBackgroundBlurMode.invoke(target, 0);
                    })::unhook);
            rollbacks.add(argumentBackend.intercept(
                    blendBackgroundSetEnabled,
                    ArgumentRewriteHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (!activityState.isActive() || args.length == 0 || !(args[0] instanceof Boolean requested)) {
                            return;
                        }
                        Object target = blendBackgroundView.get(thisObject);
                        if (!isShadeBlendTarget(target, shadeWindowClass, notificationPanelClass)) return;
                        args[0] = NotificationShadeBlurPolicy.enabled(true, requested);
                    })::unhook);
            rollbacks.add(argumentBackend.intercept(
                    setPassWindowBlurEnabled,
                    ArgumentRewriteHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (args.length == 0 || !(args[0] instanceof Boolean requested)) return;
                        if (!notificationPanelClass.isInstance(thisObject)) return;
                        authorityState.observe(requested);
                    })::unhook);
            rollbacks.add(argumentBackend.intercept(
                    blurUtilsApplyBlur,
                    ArgumentRewriteHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (!activityState.isActive() || args.length < 2 || !(args[1] instanceof Integer radius)) {
                            return;
                        }
                        Object rootView = args[0] == null ? null : viewRootGetView.invoke(args[0]);
                        if (!shadeWindowClass.isInstance(rootView)) return;
                        args[1] = NotificationShadeBlurPolicy.blurRadius(true, radius);
                    })::unhook);
            return HookInstallResult.installed(HOOK_ID);
        } catch (Throwable error) {
            for (int index = rollbacks.size() - 1; index >= 0; index--) {
                try { rollbacks.get(index).run(); } catch (Throwable rollback) {
                    error.addSuppressed(rollback);
                }
            }
            return HookInstallResult.failed(HOOK_ID, "notification glass hook registration failed", error);
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

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }
}
