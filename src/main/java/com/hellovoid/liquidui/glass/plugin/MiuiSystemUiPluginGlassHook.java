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

/**
 * Main-SystemUI bridge for dynamically loaded Xiaomi SystemUI plugin glass adapters.
 *
 * <p>Plugin implementation classes are deliberately absent here. The bridge waits until the
 * verified plugin object exists, obtains that object's own ClassLoader, and delegates component
 * hook installation to a session installer. unloadPlugin() closes the session before vendor
 * onDestroy(), preventing stale Views or ClassLoader references across plugin reloads.</p>
 */
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

    private final BeforeMethodHookBackend beforeBackend;
    private final AfterMethodHookBackend afterBackend;
    private final PluginSessionInstaller sessionInstaller;
    private final IdentityHashMap<Object, AutoCloseable> pluginSessions = new IdentityHashMap<>();

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
            // loadPlugin() returns only after plugin creation, version check, onCreate and listener
            // notification. At this point getPlugin/getPluginContext are authoritative.
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

                            // Repeated vendor load callbacks are idempotent: retire any previous
                            // session before installing against the current plugin object/loader.
                            closePluginSession(thisObject);
                            AutoCloseable session = sessionInstaller.install(
                                    pluginClassLoader, pluginContext);
                            if (session == null) {
                                throw new IllegalStateException("plugin session installer returned null");
                            }
                            pluginSessions.put(thisObject, session);
                            android.util.Log.i("LiquidUI",
                                    "[LUI][PluginGlass] verified plugin session installed version="
                                            + PLUGIN_VERSION_NAME);
                        } catch (Throwable error) {
                            closePluginSession(thisObject);
                            android.util.Log.e("LiquidUI",
                                    "[LUI][PluginGlass] plugin session install failed", error);
                        }
                    })::unhook);

            // Must run before vendor unloadPlugin() clears mPlugin/mPluginContext and invokes
            // plugin onDestroy(), so component adapters can restore native materials first.
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

    private static boolean isExactPlugin(Context pluginContext) throws Exception {
        if (!PLUGIN_PACKAGE.equals(pluginContext.getPackageName())) return false;
        PackageInfo packageInfo = pluginContext.getPackageManager()
                .getPackageInfo(PLUGIN_PACKAGE, 0);
        return packageInfo.getLongVersionCode() == PLUGIN_VERSION_CODE
                && PLUGIN_VERSION_NAME.equals(packageInfo.versionName);
    }

    private void closePluginSession(Object pluginInstance) {
        AutoCloseable session = pluginSessions.remove(pluginInstance);
        if (session == null) return;
        try {
            session.close();
        } catch (Throwable error) {
            android.util.Log.e("LiquidUI", "[LUI][PluginGlass] session close failed", error);
        }
    }

    private void closeAllPluginSessions() {
        for (Map.Entry<Object, AutoCloseable> entry :
                new ArrayList<>(pluginSessions.entrySet())) {
            closePluginSession(entry.getKey());
        }
        pluginSessions.clear();
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }
}
