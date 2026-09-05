package com.hellovoid.liquidui.hook.systemui.notification;

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

/** Forces notification-row final render backgrounds to opaque red. */
public final class NotificationRedBackgroundHook implements SystemUiHook {
    public static final String HOOK_ID = "notification.red-background";

    private static final String TARGET_PROFILE = "systemui-001";
    private static final String BACKGROUND_VIEW =
            "com.android.systemui.statusbar.notification.row.NotificationBackgroundView";
    private static final String EXPANDABLE_NOTIFICATION_ROW =
            "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow";
    private static final String NOTIFICATION_VIEW_WRAPPER =
            "com.android.systemui.statusbar.notification.row.wrapper.NotificationViewWrapper";
    private static final String MIUI_NOTIFICATION_TEMPLATE_WRAPPER =
            "com.android.systemui.statusbar.notification.row.wrapper.MiuiNotificationTemplateViewWrapper";
    private static final String MIUI_NOTIFICATION_BIG_TEXT_WRAPPER =
            "com.android.systemui.statusbar.notification.row.wrapper.MiuiNotificationBigTextViewWrapper";
    private static final String MIUI_NOTIFICATION_CUSTOM_WRAPPER =
            "com.android.systemui.statusbar.notification.row.wrapper.MiuiNotificationCustomViewWrapper";
    private static final String MI_BLUR_COMPAT = "com.miui.systemui.util.MiBlurCompat";
    private static final String VIEW = "android.view.View";
    private static final String CANVAS = "android.graphics.Canvas";
    private static final String DRAWABLE = "android.graphics.drawable.Drawable";
    private static final String GRADIENT_DRAWABLE = "android.graphics.drawable.GradientDrawable";
    private static final String LAYER_DRAWABLE = "android.graphics.drawable.LayerDrawable";

    private final BeforeMethodHookBackend backend;

    public NotificationRedBackgroundHook(BeforeMethodHookBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    @Override
    public String id() {
        return HOOK_ID;
    }

    @Override
    public HookInstallResult install(ClassLoader classLoader, SystemUiTargetProfile profile) {
        Objects.requireNonNull(classLoader, "classLoader");
        Objects.requireNonNull(profile, "profile");
        if (!TARGET_PROFILE.equals(profile.id())) {
            return HookInstallResult.unsupported(
                    HOOK_ID, "profile is not " + TARGET_PROFILE + ": " + profile.id());
        }

        final RenderAuthority authority;
        final Method onDraw;
        final List<Method> onReinflatedMethods;
        try {
            Class<?> backgroundView = TargetClassResolver.require(classLoader, BACKGROUND_VIEW);
            Class<?> row = TargetClassResolver.require(classLoader, EXPANDABLE_NOTIFICATION_ROW);
            Class<?> wrapper = TargetClassResolver.require(classLoader, NOTIFICATION_VIEW_WRAPPER);
            Class<?> miuiTemplateWrapper = TargetClassResolver.require(
                    classLoader, MIUI_NOTIFICATION_TEMPLATE_WRAPPER);
            Class<?> miuiBigTextWrapper = TargetClassResolver.require(
                    classLoader, MIUI_NOTIFICATION_BIG_TEXT_WRAPPER);
            Class<?> miuiCustomWrapper = TargetClassResolver.require(
                    classLoader, MIUI_NOTIFICATION_CUSTOM_WRAPPER);
            Class<?> miBlurCompat = TargetClassResolver.require(classLoader, MI_BLUR_COMPAT);
            Class<?> view = TargetClassResolver.require(classLoader, VIEW);
            Class<?> canvas = TargetClassResolver.require(classLoader, CANVAS);
            Class<?> drawable = TargetClassResolver.require(classLoader, DRAWABLE);
            Class<?> gradientDrawable = TargetClassResolver.require(classLoader, GRADIENT_DRAWABLE);
            Class<?> layerDrawable = TargetClassResolver.require(classLoader, LAYER_DRAWABLE);

            onDraw = backgroundView.getDeclaredMethod("onDraw", canvas);
            onDraw.setAccessible(true);
            onReinflatedMethods = List.of(
                    declaredAccessibleMethod(wrapper, "onReinflated"),
                    declaredAccessibleMethod(miuiTemplateWrapper, "onReinflated"),
                    declaredAccessibleMethod(miuiBigTextWrapper, "onReinflated"),
                    declaredAccessibleMethod(miuiCustomWrapper, "onReinflated"));

            Field backgroundField = backgroundView.getDeclaredField("mBackground");
            backgroundField.setAccessible(true);
            Field wrapperViewField = wrapper.getDeclaredField("mView");
            wrapperViewField.setAccessible(true);
            Field wrapperRowField = wrapper.getDeclaredField("mRow");
            wrapperRowField.setAccessible(true);

            Method getParent = view.getMethod("getParent");
            Method setBackgroundResource = view.getMethod("setBackgroundResource", int.class);
            Method setAlpha = drawable.getMethod("setAlpha", int.class);
            Method setTint = drawable.getMethod("setTint", int.class);
            Method setColor = gradientDrawable.getMethod("setColor", int.class);
            Method getNumberOfLayers = layerDrawable.getMethod("getNumberOfLayers");
            Method getDrawable = layerDrawable.getMethod("getDrawable", int.class);
            Method disableBlur = miBlurCompat.getDeclaredMethod(
                    "setMiViewBlurModeCompat", int.class, view);
            disableBlur.setAccessible(true);
            Method clearBlend = miBlurCompat.getDeclaredMethod(
                    "clearMiBackgroundBlendColorCompat", view);
            clearBlend.setAccessible(true);

            authority = new RenderAuthority(
                    row,
                    gradientDrawable,
                    layerDrawable,
                    backgroundField,
                    wrapperViewField,
                    wrapperRowField,
                    getParent,
                    setBackgroundResource,
                    setAlpha,
                    setTint,
                    setColor,
                    getNumberOfLayers,
                    getDrawable,
                    disableBlur,
                    clearBlend);
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException error) {
            return HookInstallResult.unsupported(HOOK_ID, "exact final-render contract missing: " + error);
        } catch (Throwable error) {
            return HookInstallResult.failed(HOOK_ID, "final-render contract resolution failed", error);
        }

        List<Runnable> rollbacks = new ArrayList<>(1 + onReinflatedMethods.size());
        try {
            BeforeMethodHookBackend.Registration drawRegistration = backend.intercept(
                    onDraw,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> authority.beforeBackgroundDraw(thisObject));
            rollbacks.add(drawRegistration::unhook);

            for (Method onReinflated : onReinflatedMethods) {
                BeforeMethodHookBackend.Registration reinflateRegistration = backend.intercept(
                        onReinflated,
                        BeforeMethodHookBackend.PRIORITY_HIGHEST,
                        (thisObject, args) -> authority.beforeWrapperReinflated(thisObject));
                rollbacks.add(reinflateRegistration::unhook);
            }
            return HookInstallResult.installed(HOOK_ID);
        } catch (Throwable error) {
            rollbackAll(rollbacks, error);
            return HookInstallResult.failed(HOOK_ID, "final-render hook registration failed", error);
        }
    }


    private static Method declaredAccessibleMethod(Class<?> type, String name)
            throws NoSuchMethodException {
        Method method = type.getDeclaredMethod(name);
        method.setAccessible(true);
        return method;
    }

    private static void rollbackAll(List<Runnable> rollbacks, Throwable original) {
        for (int index = rollbacks.size() - 1; index >= 0; index--) {
            try {
                rollbacks.get(index).run();
            } catch (Throwable rollbackError) {
                original.addSuppressed(rollbackError);
            }
        }
    }

    private static final class RenderAuthority {
        private final Class<?> rowClass;
        private final Class<?> gradientDrawableClass;
        private final Class<?> layerDrawableClass;
        private final Field backgroundField;
        private final Field wrapperViewField;
        private final Field wrapperRowField;
        private final Method getParent;
        private final Method setBackgroundResource;
        private final Method setAlpha;
        private final Method setTint;
        private final Method setColor;
        private final Method getNumberOfLayers;
        private final Method getDrawable;
        private final Method disableBlur;
        private final Method clearBlend;

        RenderAuthority(
                Class<?> rowClass,
                Class<?> gradientDrawableClass,
                Class<?> layerDrawableClass,
                Field backgroundField,
                Field wrapperViewField,
                Field wrapperRowField,
                Method getParent,
                Method setBackgroundResource,
                Method setAlpha,
                Method setTint,
                Method setColor,
                Method getNumberOfLayers,
                Method getDrawable,
                Method disableBlur,
                Method clearBlend) {
            this.rowClass = rowClass;
            this.gradientDrawableClass = gradientDrawableClass;
            this.layerDrawableClass = layerDrawableClass;
            this.backgroundField = backgroundField;
            this.wrapperViewField = wrapperViewField;
            this.wrapperRowField = wrapperRowField;
            this.getParent = getParent;
            this.setBackgroundResource = setBackgroundResource;
            this.setAlpha = setAlpha;
            this.setTint = setTint;
            this.setColor = setColor;
            this.getNumberOfLayers = getNumberOfLayers;
            this.getDrawable = getDrawable;
            this.disableBlur = disableBlur;
            this.clearBlend = clearBlend;
        }

        void beforeBackgroundDraw(Object backgroundView) throws Throwable {
            Object parent = getParent.invoke(backgroundView);
            if (!rowClass.isInstance(parent)) {
                return;
            }
            disableBlur.invoke(null, 0, backgroundView);
            clearBlend.invoke(null, backgroundView);
            forceOpaqueRed(backgroundField.get(backgroundView));
        }

        void beforeWrapperReinflated(Object wrapper) throws Throwable {
            Object row = wrapperRowField.get(wrapper);
            if (!rowClass.isInstance(row)) {
                return;
            }
            Object contentRoot = wrapperViewField.get(wrapper);
            if (contentRoot != null) {
                setBackgroundResource.invoke(contentRoot, 0);
            }
        }

        private void forceOpaqueRed(Object drawable) throws Throwable {
            if (drawable == null) {
                return;
            }
            setAlpha.invoke(drawable, 255);
            if (gradientDrawableClass.isInstance(drawable)) {
                setColor.invoke(drawable, NotificationRedBackgroundPolicy.OPAQUE_RED);
                return;
            }
            if (layerDrawableClass.isInstance(drawable)) {
                int layerCount = (Integer) getNumberOfLayers.invoke(drawable);
                for (int index = 0; index < layerCount; index++) {
                    forceOpaqueRed(getDrawable.invoke(drawable, index));
                }
                return;
            }
            setTint.invoke(drawable, NotificationRedBackgroundPolicy.OPAQUE_RED);
        }
    }
}
