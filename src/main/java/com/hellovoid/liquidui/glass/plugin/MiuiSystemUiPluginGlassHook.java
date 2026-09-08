package com.hellovoid.liquidui.glass.plugin;

import android.content.Context;
import android.content.pm.PackageInfo;

import com.hellovoid.liquidui.hook.AfterMethodHookBackend;
import com.hellovoid.liquidui.hook.BeforeMethodHookBackend;
import com.hellovoid.liquidui.hook.HookInstallResult;
import com.hellovoid.liquidui.hook.SystemUiHook;
import com.hellovoid.liquidui.reflect.TargetClassResolver;
import com.hellovoid.liquidui.target.SystemUiTargetProfile;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Dynamic ClassLoader bridge for the exact Xiaomi SystemUI plugin build. */
public final class MiuiSystemUiPluginGlassHook implements SystemUiHook {
    public static final String HOOK_ID = "miui-systemui-plugin-glass-bridge";
    private static final String TARGET_PROFILE = "systemui-001";
    private static final String PLUGIN_INSTANCE =
            "com.android.systemui.shared.plugins.PluginInstance";
    private static final String PLUGIN_PACKAGE = "miui.systemui.plugin";
    private static final long PLUGIN_VERSION_CODE = 171047100L;
    private static final String PLUGIN_VERSION_NAME = "17.1.4.71.0";

    @FunctionalInterface
    public interface PluginSessionInstaller {
        AutoCloseable install(ClassLoader pluginClassLoader, Context pluginContext) throws Throwable;
    }

    private static final class SharedPluginSession {
        final AutoCloseable session;
        final IdentityHashMap<Object, Boolean> owners = new IdentityHashMap<>();

        SharedPluginSession(AutoCloseable session) {
            this.session = Objects.requireNonNull(session, "session");
        }
    }

    private final BeforeMethodHookBackend beforeBackend;
    private final AfterMethodHookBackend afterBackend;
    private final PluginSessionInstaller sessionInstaller;

    // Xiaomi may create multiple PluginInstance wrappers for the same loaded plugin ClassLoader
    // (for example, different plugin listeners/interfaces). The glass hooks are ClassLoader-wide,
    // so installing once per wrapper duplicates every component adapter. Keep wrapper ownership
    // separately and share exactly one glass session for each plugin ClassLoader.
    private final IdentityHashMap<Object, ClassLoader> pluginOwners = new IdentityHashMap<>();
    private final IdentityHashMap<ClassLoader, SharedPluginSession> sharedPluginSessions =
            new IdentityHashMap<>();

    public MiuiSystemUiPluginGlassHook(
            BeforeMethodHookBackend beforeBackend,
            AfterMethodHookBackend afterBackend,
            PluginSessionInstaller sessionInstaller) {
        this.beforeBackend = Objects.requireNonNull(beforeBackend, "beforeBackend");
        this.afterBackend = Objects.requireNonNull(afterBackend, "afterBackend");
        this.sessionInstaller = Objects.requireNonNull(sessionInstaller, "sessionInstaller");
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

        final Method loadPlugin;
        final Method unloadPlugin;
        final Method getPackage;
        final Method getPlugin;
        final Method getPluginContext;
        try {
            Class<?> instanceClass = TargetClassResolver.require(classLoader, PLUGIN_INSTANCE);
            loadPlugin = accessible(instanceClass.getDeclaredMethod("loadPlugin"));
            unloadPlugin = accessible(instanceClass.getDeclaredMethod("unloadPlugin"));
            getPackage = accessible(instanceClass.getDeclaredMethod("getPackage"));
            getPlugin = accessible(instanceClass.getDeclaredMethod("getPlugin"));
            getPluginContext = accessible(instanceClass.getDeclaredMethod("getPluginContext"));
        } catch (ClassNotFoundException | NoSuchMethodException error) {
            return HookInstallResult.unsupported(HOOK_ID,
                    "plugin lifecycle contract missing: " + error);
        } catch (Throwable error) {
            return HookInstallResult.failed(HOOK_ID,
                    "plugin lifecycle contract resolution failed", error);
        }

        List<Runnable> rollbacks = new ArrayList<>();
        try {
            rollbacks.add(afterBackend.intercept(
                    loadPlugin,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        try {
                            if (!PLUGIN_PACKAGE.equals(getPackage.invoke(thisObject))) return;
                            Object plugin = getPlugin.invoke(thisObject);
                            Object contextObject = getPluginContext.invoke(thisObject);
                            if (plugin == null || !(contextObject instanceof Context pluginContext)) {
                                closePluginSession(thisObject);
                                return;
                            }
                            if (!isExactPlugin(pluginContext)) {
                                closePluginSession(thisObject);
                                android.util.Log.w("LiquidUI",
                                        "[LUI][PluginGlass] unsupported plugin version for "
                                                + PLUGIN_PACKAGE);
                                return;
                            }

                            ClassLoader pluginClassLoader = plugin.getClass().getClassLoader();
                            if (pluginClassLoader == null) {
                                closePluginSession(thisObject);
                                throw new IllegalStateException("plugin ClassLoader is null");
                            }

                            acquirePluginSession(thisObject, pluginClassLoader, pluginContext);
                        } catch (Throwable error) {
                            closePluginSession(thisObject);
                            android.util.Log.e("LiquidUI",
                                    "[LUI][PluginGlass] plugin session install failed", error);
                        }
                    })::unhook);

            rollbacks.add(beforeBackend.intercept(
                    unloadPlugin,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        try {
                            if (PLUGIN_PACKAGE.equals(getPackage.invoke(thisObject))) {
                                closePluginSession(thisObject);
                            }
                        } catch (Throwable error) {
                            android.util.Log.e("LiquidUI",
                                    "[LUI][PluginGlass] plugin session unload failed", error);
                        }
                    })::unhook);

            android.util.Log.i("LiquidUI",
                    "[LUI][PluginGlass] installed verified dynamic plugin lifecycle bridge");
            return HookInstallResult.installed(HOOK_ID);
        } catch (Throwable error) {
            for (int index = rollbacks.size() - 1; index >= 0; index--) {
                try { rollbacks.get(index).run(); } catch (Throwable rollback) {
                    error.addSuppressed(rollback);
                }
            }
            closeAllPluginSessions();
            return HookInstallResult.failed(HOOK_ID,
                    "plugin lifecycle hook registration failed", error);
        }
    }

    private synchronized void acquirePluginSession(
            Object pluginInstance,
            ClassLoader pluginClassLoader,
            Context pluginContext) throws Throwable {
        ClassLoader currentLoader = pluginOwners.get(pluginInstance);
        if (currentLoader == pluginClassLoader) {
            SharedPluginSession current = sharedPluginSessions.get(pluginClassLoader);
            if (current != null && current.owners.containsKey(pluginInstance)) {
                android.util.Log.i("LiquidUI",
                        "[LUI][PluginGlass] shared plugin session already owned owner="
                                + identity(pluginInstance)
                                + " loader=" + identity(pluginClassLoader)
                                + " owners=" + current.owners.size());
                return;
            }
        }

        releasePluginOwner(pluginInstance);

        SharedPluginSession shared = sharedPluginSessions.get(pluginClassLoader);
        boolean installed = false;
        if (shared == null) {
            AutoCloseable session = sessionInstaller.install(pluginClassLoader, pluginContext);
            if (session == null) {
                throw new IllegalStateException("plugin session installer returned null");
            }
            shared = new SharedPluginSession(session);
            sharedPluginSessions.put(pluginClassLoader, shared);
            installed = true;
        }

        shared.owners.put(pluginInstance, Boolean.TRUE);
        pluginOwners.put(pluginInstance, pluginClassLoader);
        android.util.Log.i("LiquidUI",
                "[LUI][PluginGlass] shared plugin session "
                        + (installed ? "installed" : "reused")
                        + " version=" + PLUGIN_VERSION_NAME
                        + " owner=" + identity(pluginInstance)
                        + " loader=" + identity(pluginClassLoader)
                        + " owners=" + shared.owners.size());
    }

    private synchronized void closePluginSession(Object pluginInstance) {
        releasePluginOwner(pluginInstance);
    }

    private void releasePluginOwner(Object pluginInstance) {
        ClassLoader pluginClassLoader = pluginOwners.remove(pluginInstance);
        if (pluginClassLoader == null) return;

        SharedPluginSession shared = sharedPluginSessions.get(pluginClassLoader);
        if (shared == null) return;
        shared.owners.remove(pluginInstance);
        if (!shared.owners.isEmpty()) {
            android.util.Log.i("LiquidUI",
                    "[LUI][PluginGlass] shared plugin owner released owner="
                            + identity(pluginInstance)
                            + " loader=" + identity(pluginClassLoader)
                            + " owners=" + shared.owners.size());
            return;
        }

        sharedPluginSessions.remove(pluginClassLoader);
        try {
            shared.session.close();
        } catch (Throwable error) {
            android.util.Log.e("LiquidUI", "[LUI][PluginGlass] session close failed", error);
        }
        android.util.Log.i("LiquidUI",
                "[LUI][PluginGlass] shared plugin session closed loader="
                        + identity(pluginClassLoader));
    }

    private synchronized void closeAllPluginSessions() {
        pluginOwners.clear();
        for (Map.Entry<ClassLoader, SharedPluginSession> entry :
                new ArrayList<>(sharedPluginSessions.entrySet())) {
            try {
                entry.getValue().session.close();
            } catch (Throwable error) {
                android.util.Log.e("LiquidUI", "[LUI][PluginGlass] session close failed", error);
            }
        }
        sharedPluginSessions.clear();
    }

    private static boolean isExactPlugin(Context pluginContext) throws Exception {
        if (!PLUGIN_PACKAGE.equals(pluginContext.getPackageName())) return false;
        PackageInfo packageInfo = pluginContext.getPackageManager()
                .getPackageInfo(PLUGIN_PACKAGE, 0);
        return packageInfo.getLongVersionCode() == PLUGIN_VERSION_CODE
                && PLUGIN_VERSION_NAME.equals(packageInfo.versionName);
    }

    private static String identity(Object value) {
        return "0x" + Integer.toHexString(System.identityHashCode(value));
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }
}
