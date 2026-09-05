package com.hellovoid.liquidui.hook.systemui.notification;

import com.hellovoid.liquidui.hook.HookInstallResult;
import com.hellovoid.liquidui.hook.HookInstallStatus;
import com.hellovoid.liquidui.target.SystemUiTargetProfile;
import com.hellovoid.liquidui.target.profiles.SystemUi001Profile;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

import static org.junit.Assert.*;

public class NotificationRedBackgroundHookContractTest {
    private static final String HOOK =
            "com.hellovoid.liquidui.hook.systemui.notification.NotificationRedBackgroundHook";
    private static final String BACKEND =
            "com.hellovoid.liquidui.hook.IntArgumentHookBackend";
    private static final String BOOLEAN_BACKEND =
            "com.hellovoid.liquidui.hook.BooleanArgumentHookBackend";
    private static final String ACTIVATABLE =
            "com.android.systemui.statusbar.notification.row.ActivatableNotificationView";
    private static final String BACKGROUND =
            "com.android.systemui.statusbar.notification.row.NotificationBackgroundView";
    private static final String ROW_INJECTOR =
            "com.android.systemui.statusbar.notification.row.ExpandableNotificationRowInjector";
    private static final String R_DRAWABLE = "com.android.systemui.R$drawable";

    @Test
    public void installsExactMaterialAndTintRewritersThroughTargetClassLoader() throws Exception {
        RecordingBackend backend = new RecordingBackend(-1);
        RecordingBooleanBackend booleanBackend = new RecordingBooleanBackend(-1);
        Object hook = newHook(backend, booleanBackend);
        TargetLoader loader = new TargetLoader(true, true, true, true);

        HookInstallResult result = install(hook, loader, SystemUi001Profile.INSTANCE);

        assertEquals(HookInstallStatus.INSTALLED, result.status());
        assertEquals("notification.red-background", id(hook));
        assertEquals(2L, backend.registrations.size());
        assertEquals(1L, booleanBackend.registrations.size());
        assertTrue(loader.requested.contains(ACTIVATABLE));
        assertTrue(loader.requested.contains(BACKGROUND));
        assertTrue(loader.requested.contains(ROW_INJECTOR));
        assertTrue(loader.requested.contains(R_DRAWABLE));

        BooleanRegistration blur = booleanBackend.byMethod("updateBlurBg");
        assertEquals(2L, blur.argumentIndex);
        assertEquals((long) Integer.MAX_VALUE, blur.priority);
        assertFalse(blur.rewriter.rewrite(true));
        assertFalse(blur.rewriter.rewrite(false));

        Registration material = backend.byMethod("setCustomBackground");
        assertEquals(0L, material.argumentIndex);
        assertEquals((long) Integer.MAX_VALUE, material.priority);
        assertEquals((long) FakeRDrawable.notification_material_bg,
                material.rewriter.applyAsInt(123));

        Registration tint = backend.byMethod("setBackgroundTintColor");
        assertEquals(0L, tint.argumentIndex);
        assertEquals((long) Integer.MAX_VALUE, tint.priority);
        assertEquals((long) 0xFFFF0000, tint.rewriter.applyAsInt(0x44010203));
    }

    @Test
    public void missingExactTargetContractIsUnsupportedWithoutRegistration() throws Exception {
        RecordingBackend backend = new RecordingBackend(-1);
        RecordingBooleanBackend booleanBackend = new RecordingBooleanBackend(-1);
        Object hook = newHook(backend, booleanBackend);
        TargetLoader loader = new TargetLoader(true, false, true, true);

        HookInstallResult result = install(hook, loader, SystemUi001Profile.INSTANCE);

        assertEquals(HookInstallStatus.UNSUPPORTED, result.status());
        assertEquals(0L, backend.registrations.size());
        assertEquals(0L, booleanBackend.registrations.size());
    }

    @Test
    public void laterRegistrationFailureRollsBackAllEarlierRegistrations() throws Exception {
        RecordingBackend backend = new RecordingBackend(1);
        RecordingBooleanBackend booleanBackend = new RecordingBooleanBackend(-1);
        Object hook = newHook(backend, booleanBackend);

        HookInstallResult result = install(
                hook, new TargetLoader(true, true, true, true), SystemUi001Profile.INSTANCE);

        assertEquals(HookInstallStatus.FAILED, result.status());
        assertEquals(1L, booleanBackend.registrations.size());
        assertEquals(1L, backend.registrations.size());
        assertTrue(booleanBackend.registrations.get(0).unhooked);
        assertTrue(backend.registrations.get(0).unhooked);
    }

    @Test
    public void unsupportedProfileIsRejectedBeforeTargetLookup() throws Exception {
        RecordingBackend backend = new RecordingBackend(-1);
        RecordingBooleanBackend booleanBackend = new RecordingBooleanBackend(-1);
        Object hook = newHook(backend, booleanBackend);
        TargetLoader loader = new TargetLoader(true, true, true, true);
        SystemUiTargetProfile other = new SystemUiTargetProfile() {
            @Override public String id() { return "systemui-other"; }
            @Override public String packageName() { return "com.android.systemui"; }
            @Override public long versionCode() { return 1; }
            @Override public String versionName() { return "other"; }
            @Override public int sdkInt() { return 36; }
            @Override public List<com.hellovoid.liquidui.target.StructuralProbe> structuralProbes() {
                return List.of();
            }
        };

        HookInstallResult result = install(hook, loader, other);

        assertEquals(HookInstallStatus.UNSUPPORTED, result.status());
        assertEquals(0L, loader.requested.size());
        assertEquals(0L, backend.registrations.size());
        assertEquals(0L, booleanBackend.registrations.size());
    }

    private static Object newHook(
            RecordingBackend backend, RecordingBooleanBackend booleanBackend) throws Exception {
        Class<?> backendType = Class.forName(BACKEND);
        Class<?> booleanBackendType = Class.forName(BOOLEAN_BACKEND);
        Object proxy = Proxy.newProxyInstance(
                backendType.getClassLoader(), new Class<?>[]{backendType}, backend);
        Object booleanProxy = Proxy.newProxyInstance(
                booleanBackendType.getClassLoader(), new Class<?>[]{booleanBackendType}, booleanBackend);
        Class<?> hookType = Class.forName(HOOK);
        return hookType.getConstructor(backendType, booleanBackendType)
                .newInstance(proxy, booleanProxy);
    }

    private static HookInstallResult install(
            Object hook, ClassLoader loader, SystemUiTargetProfile profile) throws Exception {
        try {
            return (HookInstallResult) hook.getClass()
                    .getMethod("install", ClassLoader.class, SystemUiTargetProfile.class)
                    .invoke(hook, loader, profile);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw error;
        }
    }

    private static String id(Object hook) throws Exception {
        return (String) hook.getClass().getMethod("id").invoke(hook);
    }

    public static final class FakeActivatableNotificationView {
        public void setBackgroundTintColor(int tint) {}
    }

    public static final class FakeNotificationBackgroundView {
        public void setCustomBackground(int resourceId) {}
    }

    public static final class FakeExpandableNotificationRowInjector {
        public void updateBlurBg(int blurBackground, int solidBackground, boolean enableBlur) {}
    }

    public static final class FakeRDrawable {
        public static final int notification_material_bg = 0x7f080123;
    }

    private static final class TargetLoader extends ClassLoader {
        private final boolean activatable;
        private final boolean background;
        private final boolean rowInjector;
        private final boolean drawable;
        private final List<String> requested = new ArrayList<>();

        TargetLoader(boolean activatable, boolean background, boolean rowInjector, boolean drawable) {
            super(NotificationRedBackgroundHookContractTest.class.getClassLoader());
            this.activatable = activatable;
            this.background = background;
            this.rowInjector = rowInjector;
            this.drawable = drawable;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            requested.add(name);
            if (ACTIVATABLE.equals(name)) {
                if (!activatable) throw new ClassNotFoundException(name);
                return FakeActivatableNotificationView.class;
            }
            if (BACKGROUND.equals(name)) {
                if (!background) throw new ClassNotFoundException(name);
                return FakeNotificationBackgroundView.class;
            }
            if (ROW_INJECTOR.equals(name)) {
                if (!rowInjector) throw new ClassNotFoundException(name);
                return FakeExpandableNotificationRowInjector.class;
            }
            if (R_DRAWABLE.equals(name)) {
                if (!drawable) throw new ClassNotFoundException(name);
                return FakeRDrawable.class;
            }
            return super.loadClass(name, resolve);
        }
    }

    private static final class Registration {
        final Method method;
        final int argumentIndex;
        final int priority;
        final IntUnaryOperator rewriter;
        boolean unhooked;

        Registration(Method method, int argumentIndex, int priority, IntUnaryOperator rewriter) {
            this.method = method;
            this.argumentIndex = argumentIndex;
            this.priority = priority;
            this.rewriter = rewriter;
        }
    }

    private static final class RecordingBackend implements java.lang.reflect.InvocationHandler {
        final List<Registration> registrations = new ArrayList<>();
        final int failAtRegistrationIndex;

        RecordingBackend(int failAtRegistrationIndex) {
            this.failAtRegistrationIndex = failAtRegistrationIndex;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                switch (method.getName()) {
                    case "toString": return "RecordingBackend";
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    default: return null;
                }
            }
            if (!"intercept".equals(method.getName())) {
                throw new AssertionError("unexpected backend method " + method);
            }
            if (registrations.size() == failAtRegistrationIndex) {
                throw new IllegalStateException("synthetic registration failure");
            }
            Registration registration = new Registration(
                    (Method) args[0], (Integer) args[1], (Integer) args[2],
                    (IntUnaryOperator) args[3]);
            registrations.add(registration);
            Class<?> registrationType = method.getReturnType();
            return Proxy.newProxyInstance(
                    registrationType.getClassLoader(), new Class<?>[]{registrationType},
                    (handle, handleMethod, handleArgs) -> {
                        if ("unhook".equals(handleMethod.getName())) {
                            registration.unhooked = true;
                            return null;
                        }
                        if (handleMethod.getDeclaringClass() == Object.class) {
                            if ("toString".equals(handleMethod.getName())) return "Registration";
                            if ("hashCode".equals(handleMethod.getName())) return System.identityHashCode(handle);
                            if ("equals".equals(handleMethod.getName())) return handle == handleArgs[0];
                        }
                        throw new AssertionError("unexpected registration method " + handleMethod);
                    });
        }

        Registration byMethod(String methodName) {
            for (Registration registration : registrations) {
                if (methodName.equals(registration.method.getName())) return registration;
            }
            throw new AssertionError("missing method registration " + methodName);
        }
    }

    @FunctionalInterface
    private interface BooleanRewriterView {
        boolean rewrite(boolean value);
    }

    private static final class BooleanRegistration {
        final Method method;
        final int argumentIndex;
        final int priority;
        final BooleanRewriterView rewriter;
        boolean unhooked;

        BooleanRegistration(Method method, int argumentIndex, int priority, BooleanRewriterView rewriter) {
            this.method = method;
            this.argumentIndex = argumentIndex;
            this.priority = priority;
            this.rewriter = rewriter;
        }
    }

    private static final class RecordingBooleanBackend implements java.lang.reflect.InvocationHandler {
        final List<BooleanRegistration> registrations = new ArrayList<>();
        final int failAtRegistrationIndex;

        RecordingBooleanBackend(int failAtRegistrationIndex) {
            this.failAtRegistrationIndex = failAtRegistrationIndex;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                switch (method.getName()) {
                    case "toString": return "RecordingBooleanBackend";
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    default: return null;
                }
            }
            if (!"intercept".equals(method.getName())) {
                throw new AssertionError("unexpected boolean backend method " + method);
            }
            if (registrations.size() == failAtRegistrationIndex) {
                throw new IllegalStateException("synthetic boolean registration failure");
            }
            Object rewriteProxy = args[3];
            Method rewriteMethod = rewriteProxy.getClass().getInterfaces()[0]
                    .getMethod("applyAsBoolean", boolean.class);
            BooleanRegistration registration = new BooleanRegistration(
                    (Method) args[0], (Integer) args[1], (Integer) args[2],
                    value -> {
                        try {
                            return (Boolean) rewriteMethod.invoke(rewriteProxy, value);
                        } catch (ReflectiveOperationException error) {
                            throw new RuntimeException(error);
                        }
                    });
            registrations.add(registration);
            Class<?> registrationType = method.getReturnType();
            return Proxy.newProxyInstance(
                    registrationType.getClassLoader(), new Class<?>[]{registrationType},
                    (handle, handleMethod, handleArgs) -> {
                        if ("unhook".equals(handleMethod.getName())) {
                            registration.unhooked = true;
                            return null;
                        }
                        if (handleMethod.getDeclaringClass() == Object.class) {
                            if ("toString".equals(handleMethod.getName())) return "BooleanRegistration";
                            if ("hashCode".equals(handleMethod.getName())) return System.identityHashCode(handle);
                            if ("equals".equals(handleMethod.getName())) return handle == handleArgs[0];
                        }
                        throw new AssertionError("unexpected registration method " + handleMethod);
                    });
        }

        BooleanRegistration byMethod(String methodName) {
            for (BooleanRegistration registration : registrations) {
                if (methodName.equals(registration.method.getName())) return registration;
            }
            throw new AssertionError("missing boolean method registration " + methodName);
        }
    }

}
