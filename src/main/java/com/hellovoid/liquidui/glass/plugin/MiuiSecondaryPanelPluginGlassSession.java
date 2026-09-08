package com.hellovoid.liquidui.glass.plugin;

import android.content.Context;
import android.view.View;

import com.hellovoid.liquidui.glass.core.SystemUiGlassCore;
import com.hellovoid.liquidui.hook.AfterMethodHookBackend;
import com.hellovoid.liquidui.reflect.TargetClassResolver;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** One verified plugin-ClassLoader session for bounded secondary Control Center panels. */
public final class MiuiSecondaryPanelPluginGlassSession implements AutoCloseable {
    private static final String SECONDARY_PANEL_BASE =
            "miui.systemui.controlcenter.panel.secondary.SecondaryPanelControllerBase";
    private static final String SECONDARY_PARAMS =
            "miui.systemui.controlcenter.panel.secondary.SecondaryParams";
    private static final String SECONDARY_PANEL_INTERFACE =
            "miui.systemui.controlcenter.panel.SecondaryPanelRouter$SecondaryPanel";

    private final MiuiSecondaryPanelPluginGlassAdapter adapter;
    private final List<Runnable> unhooks;
    private boolean closed;

    private MiuiSecondaryPanelPluginGlassSession(
            MiuiSecondaryPanelPluginGlassAdapter adapter,
            List<Runnable> unhooks) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.unhooks = List.copyOf(unhooks);
    }

    public static MiuiSecondaryPanelPluginGlassSession install(
            ClassLoader pluginClassLoader,
            Context pluginContext,
            SystemUiGlassCore glassCore,
            AfterMethodHookBackend afterBackend) throws Throwable {
        Objects.requireNonNull(pluginClassLoader, "pluginClassLoader");
        Objects.requireNonNull(pluginContext, "pluginContext");
        Objects.requireNonNull(glassCore, "glassCore");
        Objects.requireNonNull(afterBackend, "afterBackend");

        Class<?> panelBase = TargetClassResolver.require(pluginClassLoader, SECONDARY_PANEL_BASE);
        Class<?> secondaryParams = TargetClassResolver.require(pluginClassLoader, SECONDARY_PARAMS);
        Class<?> secondaryPanel = TargetClassResolver.require(
                pluginClassLoader, SECONDARY_PANEL_INTERFACE);

        Method prepareShow = accessible(panelBase.getDeclaredMethod(
                "prepareShow", secondaryParams));
        Method onShown = accessible(panelBase.getDeclaredMethod("onShown"));
        Method onHidden = accessible(panelBase.getDeclaredMethod("onHidden", secondaryPanel));
        Method onStop = accessible(panelBase.getDeclaredMethod("onStop"));
        Method onDestroy = accessible(panelBase.getDeclaredMethod("onDestroy"));
        Method onConfigurationChanged = accessible(panelBase.getDeclaredMethod(
                "onConfigurationChanged", int.class));
        Method setContentBgColor = accessible(panelBase.getDeclaredMethod(
                "setContentBgColor", float.class));
        Method setContentBgRadius = accessible(panelBase.getDeclaredMethod(
                "setContentBgRadius", float.class));
        Method getContentBg = accessible(panelBase.getDeclaredMethod("getContentBg"));
        Method getContentBgRadius = accessible(panelBase.getDeclaredMethod("getContentBgRadius"));

        Method setMiViewBlurMode = View.class.getMethod("setMiViewBlurMode", int.class);
        Method clearMiBackgroundBlendColor =
                View.class.getMethod("clearMiBackgroundBlendColor");
        Method getMiBackgroundBlendColor = View.class.getMethod(
                "getMiBackgroundBlendColor", ArrayList.class, ArrayList.class);
        Method setMiBackgroundBlendColors =
                View.class.getMethod("setMiBackgroundBlendColors", ArrayList.class);

        MiuiControlCenterSurfaceMaterialController material =
                new MiuiControlCenterSurfaceMaterialController(
                        setMiViewBlurMode,
                        clearMiBackgroundBlendColor,
                        getMiBackgroundBlendColor,
                        setMiBackgroundBlendColors);
        MiuiSecondaryPanelPluginGlassAdapter adapter =
                new MiuiSecondaryPanelPluginGlassAdapter(
                        glassCore, material, getContentBg, getContentBgRadius);

        List<Runnable> rollbacks = new ArrayList<>();
        try {
            rollbacks.add(afterBackend.intercept(
                    prepareShow,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> safe(
                            "prepareShow", () -> adapter.bindPanel(thisObject)))::unhook);
            rollbacks.add(afterBackend.intercept(
                    onShown,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> safe(
                            "onShown", () -> adapter.bindPanel(thisObject)))::unhook);

            rollbacks.add(afterBackend.intercept(
                    onConfigurationChanged,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> safe(
                            "onConfigurationChanged", () -> adapter.refreshPanel(thisObject)))::unhook);
            rollbacks.add(afterBackend.intercept(
                    setContentBgColor,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> safe(
                            "setContentBgColor", () -> adapter.refreshPanel(thisObject)))::unhook);
            rollbacks.add(afterBackend.intercept(
                    setContentBgRadius,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> safe(
                            "setContentBgRadius", () -> adapter.refreshPanel(thisObject)))::unhook);

            rollbacks.add(afterBackend.intercept(
                    onHidden,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> adapter.detachPanel(thisObject, "secondary-hidden"))::unhook);
            rollbacks.add(afterBackend.intercept(
                    onStop,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> adapter.detachPanel(thisObject, "secondary-stop"))::unhook);
            rollbacks.add(afterBackend.intercept(
                    onDestroy,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> adapter.detachPanel(thisObject, "secondary-destroy"))::unhook);

            android.util.Log.i("LiquidUI",
                    "[LUI][PluginSecondary] installed bounded secondary-panel adapter package="
                            + pluginContext.getPackageName());
            return new MiuiSecondaryPanelPluginGlassSession(adapter, rollbacks);
        } catch (Throwable error) {
            for (int index = rollbacks.size() - 1; index >= 0; index--) {
                try { rollbacks.get(index).run(); } catch (Throwable rollback) {
                    error.addSuppressed(rollback);
                }
            }
            try { adapter.close(); } catch (Throwable rollback) { error.addSuppressed(rollback); }
            throw error;
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (int index = unhooks.size() - 1; index >= 0; index--) {
            try { unhooks.get(index).run(); } catch (Throwable error) {
                android.util.Log.e("LiquidUI", "[LUI][PluginSecondary] unhook failed", error);
            }
        }
        try { adapter.close(); } catch (Throwable error) {
            android.util.Log.e("LiquidUI", "[LUI][PluginSecondary] restore failed", error);
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Throwable;
    }

    private static void safe(String stage, ThrowingAction action) {
        try {
            action.run();
        } catch (Throwable error) {
            android.util.Log.e("LiquidUI",
                    "[LUI][PluginSecondary] " + stage + " failed", error);
        }
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }
}
