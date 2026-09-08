package com.hellovoid.liquidui.glass.statusbar;

import android.view.View;

import com.hellovoid.liquidui.glass.core.SystemUiGlassCore;
import com.hellovoid.liquidui.hook.AfterMethodHookBackend;
import com.hellovoid.liquidui.hook.HookInstallResult;
import com.hellovoid.liquidui.hook.SystemUiHook;
import com.hellovoid.liquidui.reflect.TargetClassResolver;
import com.hellovoid.liquidui.target.SystemUiTargetProfile;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Exact systemui-001 binder hook for the status-bar ongoing-activity chip. */
public final class OngoingActivityChipGlassHook implements SystemUiHook {
    public static final String HOOK_ID = "statusbar.ongoing-activity-chip-liquid-glass";
    private static final String TARGET_PROFILE = "systemui-001";
    private static final String BINDER =
            "com.android.systemui.statusbar.chips.ui.binder.OngoingActivityChipBinder";
    private static final String VIEW_BINDING =
            "com.android.systemui.statusbar.chips.ui.binder.OngoingActivityChipViewBinding";
    private static final String CHIP_MODEL =
            "com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel";
    private static final String ACTIVE_MODEL =
            "com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel$Active";
    private static final String ICON_STORE =
            "com.android.systemui.statusbar.notification.icon.ui.viewbinder."
                    + "NotificationIconContainerViewBinder$IconViewStore";

    private final AfterMethodHookBackend afterBackend;
    private final SystemUiGlassCore glassCore;

    public OngoingActivityChipGlassHook(
            AfterMethodHookBackend afterBackend,
            SystemUiGlassCore glassCore) {
        this.afterBackend = Objects.requireNonNull(afterBackend, "afterBackend");
        this.glassCore = Objects.requireNonNull(glassCore, "glassCore");
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
            return HookInstallResult.unsupported(HOOK_ID, "profile=" + profile.id());
        }

        final Method bind;
        final Field backgroundView;
        final Class<?> activeClass;
        final OngoingActivityChipGlassAdapter adapter;
        try {
            Class<?> binderClass = TargetClassResolver.require(classLoader, BINDER);
            Class<?> bindingClass = TargetClassResolver.require(classLoader, VIEW_BINDING);
            Class<?> modelClass = TargetClassResolver.require(classLoader, CHIP_MODEL);
            activeClass = TargetClassResolver.require(classLoader, ACTIVE_MODEL);
            Class<?> iconStoreClass = TargetClassResolver.require(classLoader, ICON_STORE);
            bind = accessible(binderClass.getDeclaredMethod(
                    "bind", modelClass, bindingClass, iconStoreClass));
            backgroundView = accessible(bindingClass.getDeclaredField("backgroundView"));
            adapter = new OngoingActivityChipGlassAdapter(
                    glassCore, new OngoingActivityChipNativeMaterialController());
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException error) {
            return HookInstallResult.unsupported(HOOK_ID,
                    "ongoing activity chip contract missing: " + error);
        } catch (Throwable error) {
            return HookInstallResult.failed(HOOK_ID,
                    "ongoing activity chip contract resolution failed", error);
        }

        List<Runnable> rollbacks = new ArrayList<>();
        try {
            // Binder.bind() finishes all active-state color/stroke/icon mutations before this
            // callback. Only then may LiquidUI suppress backgroundView after presentation auth.
            rollbacks.add(afterBackend.intercept(
                    bind,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (args.length < 2 || args[1] == null) return;
                        Object binding = args[1];
                        Object model = args[0];
                        try {
                            Object background = backgroundView.get(binding);
                            if (!(background instanceof View view)) return;
                            if (activeClass.isInstance(model)) {
                                adapter.bindActive(binding, view);
                            } else {
                                adapter.unbind(binding, "chip-inactive");
                            }
                        } catch (Throwable error) {
                            adapter.unbind(binding, "chip-bind-error");
                            android.util.Log.e("LiquidUI",
                                    "[LUI][OngoingChip] binder callback failed", error);
                        }
                    })::unhook);

            android.util.Log.i("LiquidUI",
                    "[LUI][OngoingChip] installed exact ongoing-activity binder adapter");
            return HookInstallResult.installed(HOOK_ID);
        } catch (Throwable error) {
            for (int index = rollbacks.size() - 1; index >= 0; index--) {
                try { rollbacks.get(index).run(); } catch (Throwable rollback) {
                    error.addSuppressed(rollback);
                }
            }
            try { adapter.close(); } catch (Throwable rollback) { error.addSuppressed(rollback); }
            return HookInstallResult.failed(HOOK_ID,
                    "ongoing activity chip hook registration failed", error);
        }
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }
}
