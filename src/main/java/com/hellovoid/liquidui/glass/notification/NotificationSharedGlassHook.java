package com.hellovoid.liquidui.glass.notification;

import android.view.View;

import com.hellovoid.liquidui.hook.AfterMethodHookBackend;
import com.hellovoid.liquidui.hook.ArgumentRewriteHookBackend;
import com.hellovoid.liquidui.hook.BeforeMethodHookBackend;
import com.hellovoid.liquidui.hook.HookInstallResult;
import com.hellovoid.liquidui.hook.SystemUiHook;
import com.hellovoid.liquidui.glass.core.SystemUiGlassCore;
import com.hellovoid.liquidui.reflect.TargetClassResolver;
import com.hellovoid.liquidui.target.SystemUiTargetProfile;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Production candidate: one shared SurfaceFlinger PassBlur/OES/Prismal renderer per Shade Window.
 *
 * The exact updateBackground$1 authority still installs the verified 2dp card material. That
 * material remains alive underneath the shared renderer and is only hidden after the shared EGL
 * scene has successfully swapped. No standalone PassBlur stream probe is created by this hook.
 */
public final class NotificationSharedGlassHook implements SystemUiHook {
    public static final String HOOK_ID = "notification.shared-liquid-glass";
    private static final String TARGET_PROFILE = "systemui-001";

    private static final String ROW =
            "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow";
    private static final String ROW_INJECTOR =
            "com.android.systemui.statusbar.notification.row.ExpandableNotificationRowInjector";
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
    private static final String SHARED_NOTIFICATION_CONTAINER =
            "com.android.systemui.statusbar.notification.stack.ui.view.SharedNotificationContainer";
    private static final String CONTROL_CENTER_CONTAINER =
            "com.miui.systemui.controlcenter.container.ControlCenterContainer";
    private static final String BLUR_UTILS = "com.android.systemui.statusbar.BlurUtils";
    private static final String VIEW_ROOT_IMPL = "android.view.ViewRootImpl";

    private final BeforeMethodHookBackend beforeBackend;
    private final AfterMethodHookBackend afterBackend;
    private final ArgumentRewriteHookBackend argumentBackend;
    private final SystemUiGlassCore glassCore;
    private final boolean enabled;

    public NotificationSharedGlassHook(
            BeforeMethodHookBackend beforeBackend,
            AfterMethodHookBackend afterBackend,
            ArgumentRewriteHookBackend argumentBackend,
            SystemUiGlassCore glassCore,
            boolean enabled) {
        this.beforeBackend = Objects.requireNonNull(beforeBackend, "beforeBackend");
        this.afterBackend = Objects.requireNonNull(afterBackend, "afterBackend");
        this.argumentBackend = Objects.requireNonNull(argumentBackend, "argumentBackend");
        this.glassCore = Objects.requireNonNull(glassCore, "glassCore");
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
        final Method rowDetached;
        final List<Method> wrapperReinflated;
        final Method setChildrenExpanded;
        final Method setRoundRect;
        final Method panelPassBlur;
        final Method shadeWindowAttached;
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
        final Class<?> sharedNotificationContainerClass;
        final Class<?> controlCenterContainerClass;
        final NotificationMaterialTargetRegistry targetRegistry;
        final NotificationVendorMaterialController fallbackController;
        final NotificationGlassRuntime runtime;
        final NotificationPassBlurAuthorityState authorityState =
                new NotificationPassBlurAuthorityState();
        final NotificationGlassActivityState activityState = new NotificationGlassActivityState();

        try {
            Class<?> rowClass = TargetClassResolver.require(classLoader, ROW);
            Class<?> injectorClass = TargetClassResolver.require(classLoader, ROW_INJECTOR);
            Class<?> backgroundClass = TargetClassResolver.require(classLoader, BACKGROUND);
            Class<?> stackClass = TargetClassResolver.require(classLoader, STACK);
            Class<?> wrapperClass = TargetClassResolver.require(classLoader, WRAPPER);
            Class<?> miuiTemplate = TargetClassResolver.require(classLoader, MIUI_TEMPLATE);
            Class<?> miuiBigText = TargetClassResolver.require(classLoader, MIUI_BIG_TEXT);
            Class<?> miuiCustom = TargetClassResolver.require(classLoader, MIUI_CUSTOM);
            Class<?> miBlurCompat = TargetClassResolver.require(classLoader, MI_BLUR_COMPAT);
            Class<?> notificationUtil = TargetClassResolver.require(classLoader, NOTIFICATION_UTIL);
            Class<?> childrenContainer = TargetClassResolver.require(classLoader, CHILDREN_CONTAINER);
            Class<?> blurProviderClass = TargetClassResolver.require(classLoader, SHADE_BLUR_PROVIDER);
            Class<?> blendBackgroundClass = TargetClassResolver.require(classLoader, SHADE_BLEND_BACKGROUND);
            shadeWindowClass = TargetClassResolver.require(classLoader, SHADE_WINDOW);
            notificationPanelClass = TargetClassResolver.require(classLoader, NOTIFICATION_PANEL);
            sharedNotificationContainerClass =
                    TargetClassResolver.require(classLoader, SHARED_NOTIFICATION_CONTAINER);
            controlCenterContainerClass = TargetClassResolver.require(classLoader, CONTROL_CENTER_CONTAINER);
            Class<?> blurUtilsClass = TargetClassResolver.require(classLoader, BLUR_UTILS);
            Class<?> viewRootImplClass = TargetClassResolver.require(classLoader, VIEW_ROOT_IMPL);

            updateBackground = accessible(injectorClass.getDeclaredMethod("updateBackground$1"));
            rowDetached = accessible(rowClass.getDeclaredMethod("onDetachedFromWindow"));
            shadeWindowAttached = accessible(shadeWindowClass.getDeclaredMethod("onAttachedToWindow"));
            injectorViewField = accessible(injectorClass.getField("view"));
            backgroundNormalField = accessible(rowClass.getField("mBackgroundNormal"));

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

            wrapperReinflated = List.of(
                    accessible(wrapperClass.getDeclaredMethod("onReinflated")),
                    accessible(miuiTemplate.getDeclaredMethod("onReinflated")),
                    accessible(miuiBigText.getDeclaredMethod("onReinflated")),
                    accessible(miuiCustom.getDeclaredMethod("onReinflated")));
            setChildrenExpanded = accessible(childrenContainer.getDeclaredMethod(
                    "setChildrenExpanded", boolean.class));
            setRoundRect = accessible(notificationUtil.getDeclaredMethod(
                    "setRoundRect", View.class, boolean.class, boolean.class));

            Method setMixEffectEnabled = View.class.getMethod("setMixEffectEnabled", boolean.class);
            Method setMiViewBlurMode = View.class.getMethod("setMiViewBlurMode", int.class);
            Method clearMiBackgroundBlendColor = View.class.getMethod("clearMiBackgroundBlendColor");
            Method setViewBackgroundBlendColors = View.class.getMethod(
                    "setMiBackgroundBlendColors", ArrayList.class);
            Method setMiBloomStroke = optionalPublicMethod(View.class, "setMiBloomStroke", float[].class);
            fallbackController = new NotificationVendorMaterialController(
                    setMixEffectEnabled,
                    setMiViewBlurMode,
                    clearMiBackgroundBlendColor,
                    setViewBackgroundBlendColors,
                    setMiBloomStroke);

            Method disableBlur = accessible(miBlurCompat.getDeclaredMethod(
                    "setMiViewBlurModeCompat", int.class, View.class));
            Method clearBlend = accessible(miBlurCompat.getDeclaredMethod(
                    "clearMiBackgroundBlendColorCompat", View.class));

            targetRegistry = new NotificationMaterialTargetRegistry(rowClass);
            NotificationGlassNodeCollector collector = new NotificationGlassNodeCollector(
                    rowClass,
                    backgroundNormalField,
                    actualWidth,
                    actualHeight,
                    clipTop,
                    clipBottom,
                    topCornerRadius,
                    bottomCornerRadius,
                    expandRunning,
                    expandWidth,
                    expandHeight,
                    targetRegistry);
            LegacyNotificationVendorMaterialController presentationController =
                    new LegacyNotificationVendorMaterialController(
                            collector, rowClass, wrapperView, wrapperRow, disableBlur, clearBlend);
            runtime = new NotificationGlassRuntime(
                    glassCore, stackClass, collector, presentationController,
                    activityState, authorityState);

            panelPassBlur = View.class.getMethod("setPassWindowBlurEnabled", boolean.class);
            blurProviderSetRatio = accessible(blurProviderClass.getDeclaredMethod("setBlurRatio", float.class));
            blendBackgroundSetEnabled = accessible(blendBackgroundClass.getDeclaredMethod("setEnabled", boolean.class));
            blurUtilsApplyBlur = accessible(blurUtilsClass.getDeclaredMethod(
                    "applyBlur", viewRootImplClass, int.class, boolean.class));
            viewRootGetView = accessible(viewRootImplClass.getDeclaredMethod("getView"));
            blurProviderView = findField(blurProviderClass, "view");
            blendBackgroundView = findField(blendBackgroundClass, "view");
            setMiBackgroundBlurMode = View.class.getMethod("setMiBackgroundBlurMode", int.class);
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException error) {
            return HookInstallResult.unsupported(HOOK_ID,
                    "shared notification glass contract missing: " + error);
        } catch (Throwable error) {
            return HookInstallResult.failed(HOOK_ID,
                    "shared notification glass contract resolution failed", error);
        }

        List<Runnable> rollbacks = new ArrayList<>();
        try {
            // Renderer ownership follows the stable NotificationShadeWindowView, not either page.
            rollbacks.add(afterBackend.intercept(
                    shadeWindowAttached,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (!(thisObject instanceof View shadeWindow)) return;
                        try {
                            ShadeWindowGlassAuthority.ensure(glassCore, shadeWindow, authorityState);
                        } catch (Throwable error) {
                            android.util.Log.e("LiquidUI",
                                    "[LUI][ShadeWindowGlass] authority attach failed", error);
                        }
                    })::unhook);

            // Exact final material authority. Keep native 2dp fallback alive, then register the row
            // with the Window-shared adapter. No standalone GpuStream probe is instantiated.
            rollbacks.add(afterBackend.intercept(
                    updateBackground,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        try {
                            if (thisObject == null) return;
                            Object row = injectorViewField.get(thisObject);
                            if (!(row instanceof View rowView)) return;
                            Object background = backgroundNormalField.get(row);
                            if (!(background instanceof View target)) return;
                            Object registeredRow = targetRegistry.observeMaterialTarget(target);
                            if (registeredRow == null) registeredRow = row;

                            fallbackController.suppressSystemUiElementMaterial(target);
                            fallbackController.applySharedGlassFallbackMaterial(target);

                            Object finalRow = registeredRow;
                            if (rowView.isAttachedToWindow()) {
                                runtime.onRowAttached(finalRow);
                            } else {
                                rowView.post(() -> runtime.onRowAttached(finalRow));
                            }
                        } catch (Throwable error) {
                            android.util.Log.e("LiquidUI",
                                    "[LUI][NotifGlass][SharedHook] material/runtime update failed", error);
                        }
                    })::unhook);

            rollbacks.add(beforeBackend.intercept(
                    rowDetached,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> runtime.onRowDetached(thisObject))::unhook);
            for (Method method : wrapperReinflated) {
                rollbacks.add(beforeBackend.intercept(
                        method,
                        BeforeMethodHookBackend.PRIORITY_HIGHEST,
                        (thisObject, args) -> runtime.onWrapperObserved(thisObject))::unhook);
            }
            rollbacks.add(beforeBackend.intercept(
                    setChildrenExpanded,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    targetRegistry::observeChildrenExpanded)::unhook);
            rollbacks.add(afterBackend.intercept(
                    setRoundRect,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> targetRegistry.observeRoundRect(args))::unhook);

            // HyperOS has two independent page authorities in this one ViewRoot: notifPassBlur on
            // NotificationPanelView and ctrlPassBlur on the root ControlCenterContainer.
            rollbacks.add(argumentBackend.intercept(
                    panelPassBlur,
                    ArgumentRewriteHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (args.length == 0 || !(args[0] instanceof Boolean enabledValue)) return;
                        if (notificationPanelClass.isInstance(thisObject)) {
                            authorityState.observeNotification(enabledValue);
                            return;
                        }
                        if (isRootControlCenterContainer(
                                thisObject, controlCenterContainerClass, shadeWindowClass)) {
                            authorityState.observeControlCenter(enabledValue);
                        }
                    })::unhook);

            // Preserve HyperOS's Window-level combined backdrop below the shared renderer. Only
            // notification/control-center page-local backdrops above the renderer stay transparent.
            rollbacks.add(argumentBackend.intercept(
                    blurProviderSetRatio,
                    ArgumentRewriteHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (args.length == 0 || !(args[0] instanceof Float requested)) return;
                        Object target = blurProviderView.get(thisObject);
                        if (shadeWindowClass.isInstance(target)) {
                            args[0] = NotificationShadeBlurPolicy.rootBlurRatio(requested);
                            return;
                        }
                        if (!isPageShadeBlurTarget(
                                target, notificationPanelClass,
                                controlCenterContainerClass, shadeWindowClass)) return;
                        args[0] = NotificationShadeBlurPolicy.pageBlurRatio(requested);
                        if (target instanceof View view) setMiBackgroundBlurMode.invoke(view, 0);
                    })::unhook);
            rollbacks.add(argumentBackend.intercept(
                    blendBackgroundSetEnabled,
                    ArgumentRewriteHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (args.length == 0 || !(args[0] instanceof Boolean requested)) return;
                        Object target = blendBackgroundView.get(thisObject);
                        if (isRootShadeBlendTarget(target, shadeWindowClass)) {
                            args[0] = NotificationShadeBlurPolicy.rootBlendEnabled(requested);
                            return;
                        }
                        if (!isPageShadeBlendTarget(
                                target, notificationPanelClass, sharedNotificationContainerClass,
                                controlCenterContainerClass, shadeWindowClass)) return;
                        args[0] = NotificationShadeBlurPolicy.pageBlendEnabled(requested);
                    })::unhook);
            rollbacks.add(argumentBackend.intercept(
                    blurUtilsApplyBlur,
                    ArgumentRewriteHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (args.length < 2 || !(args[1] instanceof Integer requested)) return;
                        Object rootView = args[0] == null ? null : viewRootGetView.invoke(args[0]);
                        if (!shadeWindowClass.isInstance(rootView)) return;
                        args[1] = NotificationShadeBlurPolicy.rootWindowBlurRadius(requested);
                    })::unhook);

            android.util.Log.i("LiquidUI",
                    "[LUI][NotifGlass][SharedHook] installed Shade Window shared GPU glass hook");
            return HookInstallResult.installed(HOOK_ID);
        } catch (Throwable error) {
            for (int index = rollbacks.size() - 1; index >= 0; index--) {
                try { rollbacks.get(index).run(); } catch (Throwable rollback) {
                    error.addSuppressed(rollback);
                }
            }
            return HookInstallResult.failed(HOOK_ID,
                    "shared notification glass hook registration failed", error);
        }
    }

    private static boolean isRootControlCenterContainer(
            Object value, Class<?> controlCenterContainerClass, Class<?> shadeWindowClass) {
        if (!(value instanceof View view) || !controlCenterContainerClass.isInstance(value)) return false;
        return shadeWindowClass.isInstance(view.getParent());
    }

    private static boolean isPageShadeBlurTarget(
            Object value,
            Class<?> notificationPanelClass,
            Class<?> controlCenterContainerClass,
            Class<?> shadeWindowClass) {
        return notificationPanelClass.isInstance(value)
                || isRootControlCenterContainer(value, controlCenterContainerClass, shadeWindowClass);
    }

    private static boolean isRootShadeBlendTarget(Object value, Class<?> shadeWindowClass) {
        if (!(value instanceof View view)) return false;
        return shadeWindowClass.isInstance(view.getParent());
    }

    private static boolean isPageShadeBlendTarget(
            Object value,
            Class<?> notificationPanelClass,
            Class<?> sharedNotificationContainerClass,
            Class<?> controlCenterContainerClass,
            Class<?> shadeWindowClass) {
        if (!(value instanceof View view)) return false;
        Object parent = view.getParent();
        return notificationPanelClass.isInstance(parent)
                || sharedNotificationContainerClass.isInstance(parent)
                || (controlCenterContainerClass.isInstance(parent)
                    && parent instanceof View parentView
                    && shadeWindowClass.isInstance(parentView.getParent()));
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
