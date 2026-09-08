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

/** Verified ongoing-activity chip glass hook for systemui-001. */
public final class StatusBarGlassHook implements SystemUiHook {
    public static final String HOOK_ID = "statusbar.ongoing-activity-chip-liquid-glass";
    private static final String TARGET_PROFILE = "systemui-001";
    private static final String CHIP_BINDER =
            "com.android.systemui.statusbar.chips.ui.binder.OngoingActivityChipBinder";
    private static final String CHIP_MODEL =
            "com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel";
    private static final String CHIP_ACTIVE =
            "com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel$Active";
    private static final String CHIP_BINDING =
            "com.android.systemui.statusbar.chips.ui.binder.OngoingActivityChipViewBinding";
    private static final String ICON_STORE =
            "com.android.systemui.statusbar.notification.icon.ui.viewbinder."
                    + "NotificationIconContainerViewBinder$IconViewStore";
    private static final String STATUS_BAR_ROOT =
            "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView";

    private final AfterMethodHookBackend afterBackend;
    private final SystemUiGlassCore glassCore;

    public StatusBarGlassHook(AfterMethodHookBackend afterBackend, SystemUiGlassCore glassCore) {
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

        final Method chipBind;
        final Method statusBarAttached;
        final Field rootView;
        final Field backgroundView;
        final Class<?> activeClass;
        final Class<?> statusBarRootClass;
        final StatusBarGlassAdapter adapter;

        try {
            Class<?> binderClass = TargetClassResolver.require(classLoader, CHIP_BINDER);
            Class<?> modelClass = TargetClassResolver.require(classLoader, CHIP_MODEL);
            activeClass = TargetClassResolver.require(classLoader, CHIP_ACTIVE);
            Class<?> bindingClass = TargetClassResolver.require(classLoader, CHIP_BINDING);
            Class<?> iconStoreClass = TargetClassResolver.require(classLoader, ICON_STORE);
            statusBarRootClass = TargetClassResolver.require(classLoader, STATUS_BAR_ROOT);

            chipBind = accessible(binderClass.getDeclaredMethod(
                    "bind", modelClass, bindingClass, iconStoreClass));
            statusBarAttached = accessible(statusBarRootClass.getMethod("onAttachedToWindow"));
            rootView = accessible(bindingClass.getDeclaredField("rootView"));
            backgroundView = accessible(bindingClass.getDeclaredField("backgroundView"));

            adapter = new StatusBarGlassAdapter(glassCore, new StatusBarNativeMaterialController());
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException error) {
            return HookInstallResult.unsupported(HOOK_ID,
                    "status-bar glass contract missing: " + error);
        } catch (Throwable error) {
            return HookInstallResult.failed(HOOK_ID,
                    "status-bar glass contract resolution failed", error);
        }

        List<Runnable> rollbacks = new ArrayList<>();
        try {
            // The exact status-bar root establishes its Window host before child chip attach work.
            rollbacks.add(afterBackend.intercept(
                    statusBarAttached,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (!(thisObject instanceof View root)
                                || !statusBarRootClass.isInstance(thisObject)) return;
                        try {
                            StatusBarWindowGlassAuthority.ensure(glassCore, root);
                        } catch (Throwable error) {
                            android.util.Log.e("LiquidUI",
                                    "[LUI][StatusGlass] Window authority attach failed", error);
                        }
                    })::unhook);

            // bind() is final native background authority and carries Active/Inactive membership.
            rollbacks.add(afterBackend.intercept(
                    chipBind,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        try {
                            if (args.length < 2 || args[1] == null) return;
                            Object model = args[0];
                            Object binding = args[1];
                            Object rootValue = rootView.get(binding);
                            Object backgroundValue = backgroundView.get(binding);
                            if (!(rootValue instanceof View chipRoot)
                                    || !(backgroundValue instanceof View background)) return;

                            if (activeClass.isInstance(model)) {
                                View windowRoot = chipRoot.getRootView();
                                StatusBarWindowGlassAuthority.ensure(glassCore, windowRoot);
                                adapter.bindChip(chipRoot, background);
                            } else {
                                adapter.unbindChip(chipRoot);
                            }
                        } catch (Throwable error) {
                            android.util.Log.e("LiquidUI",
                                    "[LUI][StatusGlass] chip bind failed", error);
                        }
                    })::unhook);

            android.util.Log.i("LiquidUI",
                    "[LUI][StatusGlass] installed verified ongoing activity chip adapter");
            return HookInstallResult.installed(HOOK_ID);
        } catch (Throwable error) {
            for (int index = rollbacks.size() - 1; index >= 0; index--) {
                try { rollbacks.get(index).run(); } catch (Throwable rollback) {
                    error.addSuppressed(rollback);
                }
            }
            try { adapter.close(); } catch (Throwable rollback) { error.addSuppressed(rollback); }
            return HookInstallResult.failed(HOOK_ID,
                    "status-bar glass hook registration failed", error);
        }
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }
}
