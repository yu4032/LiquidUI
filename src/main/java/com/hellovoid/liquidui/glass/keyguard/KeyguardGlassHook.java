package com.hellovoid.liquidui.glass.keyguard;

import android.view.ViewGroup;

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

/** Verified bounded Keyguard quick-affordance hook for systemui-001. */
public final class KeyguardGlassHook implements SystemUiHook {
    public static final String HOOK_ID = "keyguard.quick-affordance-liquid-glass";
    private static final String TARGET_PROFILE = "systemui-001";
    private static final String SHORTCUTS_SECTION =
            "com.android.systemui.keyguard.ui.view.layout.sections.DefaultShortcutsSection";
    private static final String CONSTRAINT_LAYOUT =
            "androidx.constraintlayout.widget.ConstraintLayout";
    private static final String SYSTEMUI_R_ID = "com.android.systemui.R$id";
    private static final String START_BUTTON = "start_button";
    private static final String END_BUTTON = "end_button";

    private final AfterMethodHookBackend afterBackend;
    private final SystemUiGlassCore glassCore;

    public KeyguardGlassHook(
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

        final Method addViews;
        final Method removeViews;
        final KeyguardGlassAdapter adapter;

        try {
            Class<?> sectionClass = TargetClassResolver.require(classLoader, SHORTCUTS_SECTION);
            Class<?> constraintLayoutClass = TargetClassResolver.require(classLoader, CONSTRAINT_LAYOUT);
            Class<?> rIdClass = TargetClassResolver.require(classLoader, SYSTEMUI_R_ID);

            addViews = accessible(sectionClass.getDeclaredMethod("addViews", constraintLayoutClass));
            removeViews = accessible(sectionClass.getDeclaredMethod("removeViews", constraintLayoutClass));
            Field startButton = accessible(rIdClass.getDeclaredField(START_BUTTON));
            Field endButton = accessible(rIdClass.getDeclaredField(END_BUTTON));

            adapter = new KeyguardGlassAdapter(
                    glassCore,
                    new KeyguardNativeMaterialController(),
                    startButton.getInt(null),
                    endButton.getInt(null));
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException
                | IllegalAccessException error) {
            return HookInstallResult.unsupported(HOOK_ID,
                    "Keyguard quick-affordance contract missing: " + error);
        } catch (Throwable error) {
            return HookInstallResult.failed(HOOK_ID,
                    "Keyguard quick-affordance contract resolution failed", error);
        }

        List<Runnable> rollbacks = new ArrayList<>();
        try {
            // addViews creates start_button/end_button and assigns their exact native backgrounds.
            // Registration is deferred by the adapter until each button is attached to the already
            // verified NotificationShade Window renderer.
            rollbacks.add(afterBackend.intercept(
                    addViews,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        try {
                            if (args.length < 1 || !(args[0] instanceof ViewGroup root)) return;
                            adapter.bindSection(thisObject, root);
                        } catch (Throwable error) {
                            android.util.Log.e("LiquidUI",
                                    "[LUI][KeyguardGlass] shortcut bind failed", error);
                        }
                    })::unhook);

            // removeViews disposes the binding handles and removes both exact button Views. The
            // shared host detach path revokes immediately; this callback also clears adapter state.
            rollbacks.add(afterBackend.intercept(
                    removeViews,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        try {
                            adapter.unbindSection(thisObject);
                        } catch (Throwable error) {
                            android.util.Log.e("LiquidUI",
                                    "[LUI][KeyguardGlass] shortcut unbind failed", error);
                        }
                    })::unhook);

            android.util.Log.i("LiquidUI",
                    "[LUI][KeyguardGlass] installed verified quick-affordance adapter");
            return HookInstallResult.installed(HOOK_ID);
        } catch (Throwable error) {
            for (int index = rollbacks.size() - 1; index >= 0; index--) {
                try { rollbacks.get(index).run(); } catch (Throwable rollback) {
                    error.addSuppressed(rollback);
                }
            }
            try { adapter.close(); } catch (Throwable rollback) { error.addSuppressed(rollback); }
            return HookInstallResult.failed(HOOK_ID,
                    "Keyguard quick-affordance hook registration failed", error);
        }
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }
}
