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

import static org.junit.Assert.*;

public class NotificationRedBackgroundHookContractTest {
    private static final String HOOK =
            "com.hellovoid.liquidui.hook.systemui.notification.NotificationRedBackgroundHook";
    private static final String BACKEND =
            "com.hellovoid.liquidui.hook.BeforeMethodHookBackend";
    private static final String BACKGROUND =
            "com.android.systemui.statusbar.notification.row.NotificationBackgroundView";
    private static final String ROW =
            "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow";
    private static final String WRAPPER =
            "com.android.systemui.statusbar.notification.row.wrapper.NotificationViewWrapper";
    private static final String MIUI_TEMPLATE_WRAPPER =
            "com.android.systemui.statusbar.notification.row.wrapper.MiuiNotificationTemplateViewWrapper";
    private static final String MIUI_BIG_TEXT_WRAPPER =
            "com.android.systemui.statusbar.notification.row.wrapper.MiuiNotificationBigTextViewWrapper";
    private static final String MIUI_CUSTOM_WRAPPER =
            "com.android.systemui.statusbar.notification.row.wrapper.MiuiNotificationCustomViewWrapper";
    private static final String MI_BLUR = "com.miui.systemui.util.MiBlurCompat";
    private static final String VIEW = "android.view.View";
    private static final String CANVAS = "android.graphics.Canvas";
    private static final String DRAWABLE = "android.graphics.drawable.Drawable";
    private static final String GRADIENT = "android.graphics.drawable.GradientDrawable";
    private static final String LAYER = "android.graphics.drawable.LayerDrawable";

    @Test
    public void installsFinalDrawAndContentRootAuthorities() throws Throwable {
        RecordingBackend backend = new RecordingBackend(-1);
        Object hook = newHook(backend);
        TargetLoader loader = new TargetLoader();

        HookInstallResult result = install(hook, loader, SystemUi001Profile.INSTANCE);

        assertEquals(HookInstallStatus.INSTALLED, result.status());
        assertEquals("notification.red-background", id(hook));
        assertEquals(5L, backend.registrations.size());
        assertTrue(loader.requested.contains(BACKGROUND));
        assertTrue(loader.requested.contains(ROW));
        assertTrue(loader.requested.contains(WRAPPER));
        assertTrue(loader.requested.contains(MIUI_TEMPLATE_WRAPPER));
        assertTrue(loader.requested.contains(MIUI_BIG_TEXT_WRAPPER));
        assertTrue(loader.requested.contains(MIUI_CUSTOM_WRAPPER));
        assertTrue(loader.requested.contains(MI_BLUR));
        assertTrue(loader.requested.contains(VIEW));
        assertTrue(loader.requested.contains(CANVAS));
        assertTrue(loader.requested.contains(DRAWABLE));
        assertTrue(loader.requested.contains(GRADIENT));
        assertTrue(loader.requested.contains(LAYER));

        Registration draw = backend.byMethod("onDraw");
        assertEquals((long) Integer.MAX_VALUE, draw.priority);
        FakeExpandableNotificationRow row = new FakeExpandableNotificationRow();
        FakeGradientDrawable leaf = new FakeGradientDrawable();
        FakeLayerDrawable layer = new FakeLayerDrawable(leaf);
        FakeNotificationBackgroundView background = new FakeNotificationBackgroundView(row, layer);
        draw.before.before(background, new Object[]{new FakeCanvas()});
        assertEquals(0L, FakeMiBlurCompat.lastMode);
        assertTrue(FakeMiBlurCompat.cleared);
        assertEquals(255L, layer.alpha);
        assertEquals(255L, leaf.alpha);
        assertEquals((long) 0xFFFF0000, leaf.color);

        assertEquals(4L, backend.countByMethod("onReinflated"));
        assertTrue(backend.hasRegistration(FakeNotificationViewWrapper.class, "onReinflated"));
        assertTrue(backend.hasRegistration(FakeMiuiNotificationTemplateViewWrapper.class, "onReinflated"));
        assertTrue(backend.hasRegistration(FakeMiuiNotificationBigTextViewWrapper.class, "onReinflated"));
        assertTrue(backend.hasRegistration(FakeMiuiNotificationCustomViewWrapper.class, "onReinflated"));

        FakeView content = new FakeView();
        content.backgroundResource = 123;
        FakeNotificationViewWrapper wrapper = new FakeNotificationViewWrapper(content, row);
        backend.byDeclaringClassAndMethod(FakeNotificationViewWrapper.class, "onReinflated")
                .before.before(wrapper, new Object[0]);
        assertEquals(0L, content.backgroundResource);
    }

    @Test
    public void backgroundDrawOutsideNotificationRowIsUntouched() throws Throwable {
        RecordingBackend backend = new RecordingBackend(-1);
        Object hook = newHook(backend);
        install(hook, new TargetLoader(), SystemUi001Profile.INSTANCE);

        FakeGradientDrawable drawable = new FakeGradientDrawable();
        drawable.color = 0x11223344;
        FakeNotificationBackgroundView background =
                new FakeNotificationBackgroundView(new Object(), drawable);
        backend.byMethod("onDraw").before.before(background, new Object[]{new FakeCanvas()});

        assertEquals((long) 0x11223344, drawable.color);
    }

    @Test
    public void laterRegistrationFailureRollsBackEarlierRegistration() throws Exception {
        RecordingBackend backend = new RecordingBackend(1);
        Object hook = newHook(backend);

        HookInstallResult result = install(hook, new TargetLoader(), SystemUi001Profile.INSTANCE);

        assertEquals(HookInstallStatus.FAILED, result.status());
        assertEquals(1L, backend.registrations.size());
        assertTrue(backend.registrations.get(0).unhooked);
    }

    @Test
    public void unsupportedProfileIsRejectedBeforeLookup() throws Exception {
        RecordingBackend backend = new RecordingBackend(-1);
        Object hook = newHook(backend);
        TargetLoader loader = new TargetLoader();
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
    }

    private static Object newHook(RecordingBackend backend) throws Exception {
        Class<?> backendType = Class.forName(BACKEND);
        Object proxy = Proxy.newProxyInstance(
                backendType.getClassLoader(), new Class<?>[]{backendType}, backend);
        Class<?> hookType = Class.forName(HOOK);
        return hookType.getConstructor(backendType).newInstance(proxy);
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

    public static class FakeView {
        Object parent;
        int backgroundResource = -1;
        public Object getParent() { return parent; }
        public void setBackgroundResource(int value) { backgroundResource = value; }
    }

    public static final class FakeCanvas {}

    public static class FakeDrawable {
        int alpha;
        int tint;
        public void setAlpha(int value) { alpha = value; }
        public void setTint(int value) { tint = value; }
    }

    public static final class FakeGradientDrawable extends FakeDrawable {
        int color;
        public void setColor(int value) { color = value; }
    }

    public static final class FakeLayerDrawable extends FakeDrawable {
        final FakeDrawable[] layers;
        FakeLayerDrawable(FakeDrawable... layers) { this.layers = layers; }
        public int getNumberOfLayers() { return layers.length; }
        public FakeDrawable getDrawable(int index) { return layers[index]; }
    }

    public static final class FakeExpandableNotificationRow extends FakeView {}

    public static final class FakeNotificationBackgroundView extends FakeView {
        public FakeDrawable mBackground;
        FakeNotificationBackgroundView(Object parent, FakeDrawable background) {
            this.parent = parent;
            this.mBackground = background;
        }
        protected void onDraw(FakeCanvas canvas) {}
    }

    public static class FakeNotificationViewWrapper {
        public final FakeView mView;
        public final FakeExpandableNotificationRow mRow;
        FakeNotificationViewWrapper(FakeView view, FakeExpandableNotificationRow row) {
            this.mView = view;
            this.mRow = row;
        }
        public void onReinflated() {}
    }

    public static final class FakeMiuiNotificationTemplateViewWrapper extends FakeNotificationViewWrapper {
        FakeMiuiNotificationTemplateViewWrapper(FakeView view, FakeExpandableNotificationRow row) { super(view, row); }
        @Override public void onReinflated() {}
    }

    public static final class FakeMiuiNotificationBigTextViewWrapper extends FakeNotificationViewWrapper {
        FakeMiuiNotificationBigTextViewWrapper(FakeView view, FakeExpandableNotificationRow row) { super(view, row); }
        @Override public void onReinflated() {}
    }

    public static final class FakeMiuiNotificationCustomViewWrapper extends FakeNotificationViewWrapper {
        FakeMiuiNotificationCustomViewWrapper(FakeView view, FakeExpandableNotificationRow row) { super(view, row); }
        @Override public void onReinflated() {}
    }

    public static final class FakeMiBlurCompat {
        static int lastMode = -1;
        static boolean cleared;
        public static void setMiViewBlurModeCompat(int mode, FakeView view) { lastMode = mode; }
        public static void clearMiBackgroundBlendColorCompat(FakeView view) { cleared = true; }
    }

    private static final class TargetLoader extends ClassLoader {
        final List<String> requested = new ArrayList<>();
        TargetLoader() { super(NotificationRedBackgroundHookContractTest.class.getClassLoader()); }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            requested.add(name);
            if (BACKGROUND.equals(name)) return FakeNotificationBackgroundView.class;
            if (ROW.equals(name)) return FakeExpandableNotificationRow.class;
            if (WRAPPER.equals(name)) return FakeNotificationViewWrapper.class;
            if (MIUI_TEMPLATE_WRAPPER.equals(name)) return FakeMiuiNotificationTemplateViewWrapper.class;
            if (MIUI_BIG_TEXT_WRAPPER.equals(name)) return FakeMiuiNotificationBigTextViewWrapper.class;
            if (MIUI_CUSTOM_WRAPPER.equals(name)) return FakeMiuiNotificationCustomViewWrapper.class;
            if (MI_BLUR.equals(name)) return FakeMiBlurCompat.class;
            if (VIEW.equals(name)) return FakeView.class;
            if (CANVAS.equals(name)) return FakeCanvas.class;
            if (DRAWABLE.equals(name)) return FakeDrawable.class;
            if (GRADIENT.equals(name)) return FakeGradientDrawable.class;
            if (LAYER.equals(name)) return FakeLayerDrawable.class;
            return super.loadClass(name, resolve);
        }
    }

    private static final class Registration {
        final Method method;
        final int priority;
        final Before before;
        boolean unhooked;
        Registration(Method method, int priority, Before before) {
            this.method = method;
            this.priority = priority;
            this.before = before;
        }
    }

    @FunctionalInterface
    private interface Before {
        void before(Object thisObject, Object[] args) throws Throwable;
    }

    private static final class RecordingBackend implements java.lang.reflect.InvocationHandler {
        final List<Registration> registrations = new ArrayList<>();
        final int failAtRegistrationIndex;
        RecordingBackend(int failAtRegistrationIndex) { this.failAtRegistrationIndex = failAtRegistrationIndex; }

        Registration byMethod(String methodName) {
            for (Registration registration : registrations) {
                if (registration.method.getName().equals(methodName)) return registration;
            }
            throw new AssertionError("missing registration for " + methodName);
        }

        Registration byDeclaringClassAndMethod(Class<?> declaringClass, String methodName) {
            for (Registration registration : registrations) {
                if (registration.method.getDeclaringClass() == declaringClass
                        && registration.method.getName().equals(methodName)) {
                    return registration;
                }
            }
            throw new AssertionError("missing registration for "
                    + declaringClass.getName() + "#" + methodName);
        }

        long countByMethod(String methodName) {
            return registrations.stream()
                    .filter(registration -> registration.method.getName().equals(methodName))
                    .count();
        }

        boolean hasRegistration(Class<?> declaringClass, String methodName) {
            return registrations.stream().anyMatch(registration ->
                    registration.method.getDeclaringClass() == declaringClass
                            && registration.method.getName().equals(methodName));
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                if ("toString".equals(method.getName())) return "RecordingBackend";
                if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                if ("equals".equals(method.getName())) return proxy == args[0];
                return null;
            }
            if (!"intercept".equals(method.getName())) {
                throw new AssertionError("unexpected backend method " + method);
            }
            if (registrations.size() == failAtRegistrationIndex) {
                throw new IllegalStateException("synthetic registration failure");
            }
            Object callback = args[2];
            Method beforeMethod = callback.getClass().getInterfaces()[0]
                    .getMethod("before", Object.class, Object[].class);
            beforeMethod.setAccessible(true);
            Before before = (thisObject, callArgs) -> {
                try {
                    beforeMethod.invoke(callback, thisObject, callArgs);
                } catch (InvocationTargetException error) {
                    throw error.getCause();
                }
            };
            Registration registration = new Registration((Method) args[0], (Integer) args[1], before);
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
                        return null;
                    });
        }
    }
}
