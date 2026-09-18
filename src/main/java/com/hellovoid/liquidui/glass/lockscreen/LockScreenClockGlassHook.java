package com.hellovoid.liquidui.glass.lockscreen;

import android.view.View;

import com.hellovoid.liquidui.glass.core.SystemUiGlassCore;
import com.hellovoid.liquidui.glass.systemui.GlassHostGeometry;
import com.hellovoid.liquidui.glass.systemui.NativeMaterialController;
import com.hellovoid.liquidui.glass.systemui.SystemUiGlassDomain;
import com.hellovoid.liquidui.glass.systemui.SystemUiGlassHostController;
import com.hellovoid.liquidui.glass.systemui.SystemUiMaterialHostKind;
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
 * OS3 compatibility bridge for HyperOS lockscreen clocks.
 *
 * <p>OS3 does not provide the vendor glass renderer. Effect 5 is therefore treated only as a
 * semantic intent marker: LiquidUI owns the actual bounded glass presentation in the current
 * SystemUI Window and never delegates rendering to setMiGlass/setMiGlassBlurRadius or other
 * OS4 vendor material APIs. This first-stage bridge intentionally keeps native clock text visible
 * until glyph-mask authority is implemented.</p>
 */
public final class LockScreenClockGlassHook implements SystemUiHook {
    private static final String HOOK_ID = "lockscreen-clock-glass";
    private static final String CLOCK_CONTROLLER = "com.miui.clock.MiuiClockController";
    private static final String CLOCK_BEAN = "com.miui.clock.module.ClockBean";
    private static final int GLASS_EFFECT = 5;

    private final BeforeMethodHookBackend beforeBackend;
    private final AfterMethodHookBackend afterBackend;
    private final SystemUiGlassCore glassCore;

    public LockScreenClockGlassHook(
            BeforeMethodHookBackend beforeBackend,
            AfterMethodHookBackend afterBackend,
            SystemUiGlassCore glassCore) {
        this.beforeBackend = Objects.requireNonNull(beforeBackend, "beforeBackend");
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
        final Method setClockEffect;
        final Field clockViewField;

        try {
            controllerClass = TargetClassResolver.require(classLoader, CLOCK_CONTROLLER);
            beanClass = TargetClassResolver.require(classLoader, CLOCK_BEAN);
            addClockView = accessible(controllerClass.getDeclaredMethod(
                    "addClockView", beanClass, boolean.class));
            setClockEffect = accessible(beanClass.getDeclaredMethod("setClockEffect", int.class));
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
        NativeMaterialController<View> keepNativeText = new KeepNativeClockMaterial();
        List<Runnable> rollbacks = new ArrayList<>();

        try {
            rollbacks.add(beforeBackend.intercept(
                    addClockView,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (args.length == 0 || args[0] == null || !beanClass.isInstance(args[0])) {
                            return;
                        }
                        try {
                            setClockEffect.invoke(args[0], GLASS_EFFECT);
                        } catch (Throwable error) {
                            android.util.Log.e("LiquidUI",
                                    "[LUI][ClockGlass] failed to request vendor glass effect", error);
                        }
                    })::unhook);

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
                                            keepNativeText);
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
                    "[LUI][ClockGlass] installed OS3 semantic-effect compatibility bridge");
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

    private static final class KeepNativeClockMaterial implements NativeMaterialController<View> {
        @Override public void suppress(View host, long lifecycleGeneration) {
            // Stage 1: never hide clock glyphs. LiquidUI glass must prove stable presentation first.
        }

        @Override public void restore(View host, long lifecycleGeneration) {
            // Native clock was never suppressed.
        }

        @Override public void restoreAll() {
            // Native clock was never suppressed.
        }
    }
}
