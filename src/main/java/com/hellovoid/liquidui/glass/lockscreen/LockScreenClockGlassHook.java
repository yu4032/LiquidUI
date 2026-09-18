package com.hellovoid.liquidui.glass.lockscreen;

import android.view.View;

import com.hellovoid.liquidui.glass.core.SystemUiGlassCore;
import com.hellovoid.liquidui.glass.systemui.GlassHostGeometry;
import com.hellovoid.liquidui.glass.systemui.NativeMaterialController;
import com.hellovoid.liquidui.glass.systemui.SystemUiGlassDomain;
import com.hellovoid.liquidui.glass.systemui.SystemUiGlassHostController;
import com.hellovoid.liquidui.glass.systemui.SystemUiMaterialHostKind;
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

/**
 * Forced LiquidUI replacement for HyperOS OS3 lockscreen clocks.
 *
 * <p>No vendor glass capability, effect value, or OS4 material API participates in this path.
 * Once MiuiClockController creates a clock View, LiquidUI registers it unconditionally and owns
 * presentation. Native content is suppressed only after LiquidUI receives presentation authority,
 * and is restored immediately when authority is revoked.</p>
 */
public final class LockScreenClockGlassHook implements SystemUiHook {
    private static final String HOOK_ID = "lockscreen-clock-glass";
    private static final String CLOCK_CONTROLLER = "com.miui.clock.MiuiClockController";
    private static final String CLOCK_BEAN = "com.miui.clock.module.ClockBean";

    private final AfterMethodHookBackend afterBackend;
    private final SystemUiGlassCore glassCore;

    public LockScreenClockGlassHook(
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
        final Class<?> controllerClass;
        final Class<?> beanClass;
        final Method addClockView;
        final Field clockViewField;

        try {
            controllerClass = TargetClassResolver.require(classLoader, CLOCK_CONTROLLER);
            beanClass = TargetClassResolver.require(classLoader, CLOCK_BEAN);
            addClockView = accessible(controllerClass.getDeclaredMethod(
                    "addClockView", beanClass, boolean.class));
            clockViewField = findField(controllerClass, "mClockView");
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException error) {
            return HookInstallResult.unsupported(HOOK_ID,
                    "lockscreen clock glass contract missing: " + error);
        } catch (Throwable error) {
            return HookInstallResult.failed(HOOK_ID,
                    "lockscreen clock glass contract resolution failed", error);
        }

        SystemUiGlassHostController hosts = new SystemUiGlassHostController(
                glassCore, SystemUiGlassDomain.KEYGUARD, "clock");
        NativeMaterialController<View> replacementMaterial = new ClockReplacementMaterial();
        List<Runnable> rollbacks = new ArrayList<>();

        try {
            rollbacks.add(afterBackend.intercept(
                    addClockView,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        try {
                            Object value = clockViewField.get(thisObject);
                            if (!(value instanceof View clockView)) return;
                            Runnable register = () -> {
                                try {
                                    if (!clockView.isAttachedToWindow()) return;
                                    hosts.register(
                                            clockView,
                                            SystemUiMaterialHostKind.KEYGUARD_PANEL,
                                            GlassHostGeometry.rounded(0f, 1f, 40),
                                            replacementMaterial);
                                } catch (Throwable error) {
                                    android.util.Log.e("LiquidUI",
                                            "[LUI][ClockGlass] host registration failed", error);
                                }
                            };
                            if (clockView.isAttachedToWindow()) {
                                register.run();
                            } else {
                                clockView.post(register);
                            }
                        } catch (Throwable error) {
                            android.util.Log.e("LiquidUI",
                                    "[LUI][ClockGlass] clock View observation failed", error);
                        }
                    })::unhook);

            android.util.Log.i("LiquidUI",
                    "[LUI][ClockGlass] installed unconditional OS3 clock replacement");
            return HookInstallResult.installed(HOOK_ID);
        } catch (Throwable error) {
            hosts.close();
            for (int index = rollbacks.size() - 1; index >= 0; index--) {
                try { rollbacks.get(index).run(); } catch (Throwable rollback) {
                    error.addSuppressed(rollback);
                }
            }
            return HookInstallResult.failed(HOOK_ID,
                    "lockscreen clock glass hook registration failed", error);
        }
    }

    private static Field findField(Class<?> owner, String name) throws NoSuchFieldException {
        Class<?> current = owner;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                return accessible(field);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(owner.getName() + "#" + name);
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }

    private static final class ClockReplacementMaterial implements NativeMaterialController<View> {
        private final java.util.WeakHashMap<View, Float> originalAlpha = new java.util.WeakHashMap<>();

        @Override public void suppress(View host, long lifecycleGeneration) {
            if (!originalAlpha.containsKey(host)) originalAlpha.put(host, host.getAlpha());
            host.setAlpha(0f);
        }

        @Override public void restore(View host, long lifecycleGeneration) {
            Float alpha = originalAlpha.remove(host);
            if (alpha != null) host.setAlpha(alpha);
        }

        @Override public void restoreAll() {
            for (java.util.Map.Entry<View, Float> entry :
                    new java.util.ArrayList<>(originalAlpha.entrySet())) {
                View host = entry.getKey();
                if (host != null) host.setAlpha(entry.getValue());
            }
            originalAlpha.clear();
        }
    }
}
