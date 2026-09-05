package com.hellovoid.liquidui.hook.systemui.notification;

import com.hellovoid.liquidui.hook.BooleanArgumentHookBackend;
import com.hellovoid.liquidui.hook.HookInstallResult;
import com.hellovoid.liquidui.hook.IntArgumentHookBackend;
import com.hellovoid.liquidui.hook.SystemUiHook;
import com.hellovoid.liquidui.reflect.TargetClassResolver;
import com.hellovoid.liquidui.target.SystemUiTargetProfile;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Forces standard SystemUI notification row backgrounds to opaque red. */
public final class NotificationRedBackgroundHook implements SystemUiHook {
    public static final String HOOK_ID = "notification.red-background";

    private static final String TARGET_PROFILE = "systemui-001";
    private static final String ACTIVATABLE_NOTIFICATION_VIEW =
            "com.android.systemui.statusbar.notification.row.ActivatableNotificationView";
    private static final String NOTIFICATION_BACKGROUND_VIEW =
            "com.android.systemui.statusbar.notification.row.NotificationBackgroundView";
    private static final String EXPANDABLE_NOTIFICATION_ROW_INJECTOR =
            "com.android.systemui.statusbar.notification.row.ExpandableNotificationRowInjector";
    private static final String R_DRAWABLE = "com.android.systemui.R$drawable";
    private static final String MATERIAL_BACKGROUND = "notification_material_bg";

    private final IntArgumentHookBackend intBackend;
    private final BooleanArgumentHookBackend booleanBackend;

    public NotificationRedBackgroundHook(
            IntArgumentHookBackend intBackend,
            BooleanArgumentHookBackend booleanBackend) {
        this.intBackend = Objects.requireNonNull(intBackend, "intBackend");
        this.booleanBackend = Objects.requireNonNull(booleanBackend, "booleanBackend");
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

        final Method updateBlurBg;
        final Method setCustomBackground;
        final Method setBackgroundTintColor;
        final int materialBackgroundId;
        try {
            Class<?> rowInjector = TargetClassResolver.require(
                    classLoader, EXPANDABLE_NOTIFICATION_ROW_INJECTOR);
            Class<?> backgroundView = TargetClassResolver.require(
                    classLoader, NOTIFICATION_BACKGROUND_VIEW);
            Class<?> activatableView = TargetClassResolver.require(
                    classLoader, ACTIVATABLE_NOTIFICATION_VIEW);
            Class<?> rDrawable = TargetClassResolver.require(classLoader, R_DRAWABLE);

            updateBlurBg = rowInjector.getDeclaredMethod(
                    "updateBlurBg", int.class, int.class, boolean.class);
            updateBlurBg.setAccessible(true);
            setCustomBackground = backgroundView.getDeclaredMethod(
                    "setCustomBackground", int.class);
            setCustomBackground.setAccessible(true);
            setBackgroundTintColor = activatableView.getDeclaredMethod(
                    "setBackgroundTintColor", int.class);
            setBackgroundTintColor.setAccessible(true);

            Field materialBackground = rDrawable.getDeclaredField(MATERIAL_BACKGROUND);
            materialBackground.setAccessible(true);
            if (materialBackground.getType() != int.class
                    || !Modifier.isStatic(materialBackground.getModifiers())) {
                return HookInstallResult.unsupported(
                        HOOK_ID, R_DRAWABLE + "#" + MATERIAL_BACKGROUND + " is not static int");
            }
            materialBackgroundId = materialBackground.getInt(null);
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException error) {
            return HookInstallResult.unsupported(HOOK_ID, "exact notification contract missing: " + error);
        } catch (Throwable error) {
            return HookInstallResult.failed(HOOK_ID, "notification contract resolution failed", error);
        }

        List<Runnable> rollbacks = new ArrayList<>(3);
        try {
            BooleanArgumentHookBackend.Registration blurRegistration = booleanBackend.intercept(
                    updateBlurBg,
                    2,
                    BooleanArgumentHookBackend.PRIORITY_HIGHEST,
                    ignored -> false);
            rollbacks.add(blurRegistration::unhook);

            IntArgumentHookBackend.Registration materialRegistration = intBackend.intercept(
                    setCustomBackground,
                    0,
                    IntArgumentHookBackend.PRIORITY_HIGHEST,
                    ignored -> materialBackgroundId);
            rollbacks.add(materialRegistration::unhook);

            IntArgumentHookBackend.Registration tintRegistration = intBackend.intercept(
                    setBackgroundTintColor,
                    0,
                    IntArgumentHookBackend.PRIORITY_HIGHEST,
                    NotificationRedBackgroundPolicy::rewriteTint);
            rollbacks.add(tintRegistration::unhook);
            return HookInstallResult.installed(HOOK_ID);
        } catch (Throwable error) {
            rollbackAll(rollbacks, error);
            return HookInstallResult.failed(HOOK_ID, "notification hook registration failed", error);
        }
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
}
