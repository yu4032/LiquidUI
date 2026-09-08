package com.hellovoid.liquidui.glass.plugin;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import com.hellovoid.liquidui.glass.core.SystemUiGlassCore;
import com.hellovoid.liquidui.hook.AfterMethodHookBackend;
import com.hellovoid.liquidui.hook.BeforeMethodHookBackend;
import com.hellovoid.liquidui.reflect.TargetClassResolver;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** One loaded plugin session for the actual floating HyperOS Volume panel and columns. */
public final class MiuiVolumePluginGlassSession implements AutoCloseable {
    private static final String VOLUME_CONTROLLER =
            "com.android.systemui.miui.volume.VolumePanelViewController";
    private static final String VOLUME_COLUMN =
            "com.android.systemui.miui.volume.VolumeColumn";
    private static final String BACKDROP_VIEW =
            "com.miui.blur.sdk.backdrop.a";

    private final MiuiVolumePluginGlassAdapter adapter;
    private final List<Runnable> unhooks;
    private boolean closed;

    private MiuiVolumePluginGlassSession(
            MiuiVolumePluginGlassAdapter adapter,
            List<Runnable> unhooks) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.unhooks = List.copyOf(unhooks);
    }

    public static MiuiVolumePluginGlassSession install(
            ClassLoader pluginClassLoader,
            Context pluginContext,
            SystemUiGlassCore glassCore,
            BeforeMethodHookBackend beforeBackend,
            AfterMethodHookBackend afterBackend) throws Throwable {
        Objects.requireNonNull(pluginClassLoader, "pluginClassLoader");
        Objects.requireNonNull(pluginContext, "pluginContext");
        Objects.requireNonNull(glassCore, "glassCore");
        Objects.requireNonNull(beforeBackend, "beforeBackend");
        Objects.requireNonNull(afterBackend, "afterBackend");

        Class<?> controllerClass = TargetClassResolver.require(pluginClassLoader, VOLUME_CONTROLLER);
        Class<?> volumeColumnClass = TargetClassResolver.require(pluginClassLoader, VOLUME_COLUMN);
        Class<?> backdropClass = TargetClassResolver.require(pluginClassLoader, BACKDROP_VIEW);

        Method initPanelView = accessible(controllerClass.getDeclaredMethod("initPanelView"));
        Method reInit = accessible(controllerClass.getDeclaredMethod("reInit"));
        Method destroy = accessible(controllerClass.getDeclaredMethod("destroy"));
        Method updateExpandedH = accessible(controllerClass.getDeclaredMethod(
                "updateExpandedH", boolean.class, boolean.class, boolean.class));
        Method updateColumnH = accessible(
                controllerClass.getDeclaredMethod("updateColumnH", volumeColumnClass));

        Method initColumn = accessible(volumeColumnClass.getDeclaredMethod(
                "initColumn", Context.class, ViewGroup.class, int.class,
                boolean.class, boolean.class, boolean.class));
        Method setSliderBlendColor = accessible(
                volumeColumnClass.getDeclaredMethod("setSliderBlendColor", boolean.class));
        Method setSliderResource = accessible(
                volumeColumnClass.getDeclaredMethod("setSliderResource", boolean.class));
        Method release = accessible(volumeColumnClass.getDeclaredMethod("release"));
        Method getView = accessible(volumeColumnClass.getDeclaredMethod("getView"));
        Method getSlider = accessible(volumeColumnClass.getDeclaredMethod("getSlider"));
        Method getProgressViewBg = accessible(
                volumeColumnClass.getDeclaredMethod("getProgressViewBg"));
        Method getRadius = accessible(volumeColumnClass.getDeclaredMethod("getRadius"));

        Field expandBgView = accessible(controllerClass.getDeclaredField("mExpandBgView"));
        Field volumeContentView = accessible(controllerClass.getDeclaredField("mVolumeContentView"));

        Method isBlurEnabledAndSupported = accessible(
                backdropClass.getMethod("isBlurEnabledAndSupported"));
        Method setBlurEnabled = accessible(backdropClass.getMethod("setBlurEnabled", boolean.class));
        Method setMiViewBlurMode = View.class.getMethod("setMiViewBlurMode", int.class);
        Method clearMiBackgroundBlendColor =
                View.class.getMethod("clearMiBackgroundBlendColor");
        Method getMiBackgroundBlendColor = View.class.getMethod(
                "getMiBackgroundBlendColor", ArrayList.class, ArrayList.class);
        Method setMiBackgroundBlendColors =
                View.class.getMethod("setMiBackgroundBlendColors", ArrayList.class);

        MiuiVolumePanelMaterialController panelMaterial = new MiuiVolumePanelMaterialController(
                isBlurEnabledAndSupported,
                setBlurEnabled,
                setMiViewBlurMode,
                clearMiBackgroundBlendColor,
                getMiBackgroundBlendColor,
                setMiBackgroundBlendColors);
        MiuiVolumeColumnMaterialController columnMaterial = new MiuiVolumeColumnMaterialController(
                isBlurEnabledAndSupported,
                setBlurEnabled,
                setMiViewBlurMode,
                clearMiBackgroundBlendColor,
                getMiBackgroundBlendColor,
                setMiBackgroundBlendColors);
        MiuiVolumePluginGlassAdapter adapter = new MiuiVolumePluginGlassAdapter(
                glassCore,
                panelMaterial,
                columnMaterial,
                expandBgView,
                volumeContentView,
                getView,
                getSlider,
                getProgressViewBg,
                getRadius);

        List<Runnable> rollbacks = new ArrayList<>();
        try {
            // initPanelView() has installed mExpandBgView/mVolumeContentView and all column parents.
            rollbacks.add(afterBackend.intercept(
                    initPanelView,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> safe(
                            "initPanelView", () -> adapter.bindPanel(thisObject)))::unhook);

            // reInit() swaps the entire VolumePanelView; revoke old nodes before vendor rollover.
            rollbacks.add(beforeBackend.intercept(
                    reInit,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> adapter.beforeReInit(thisObject))::unhook);

            // Expanded-state changes rebuild panel backgrounds and per-column resources/blend tuples.
            rollbacks.add(afterBackend.intercept(
                    updateExpandedH,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> safe(
                            "updateExpandedH", () -> adapter.refreshPanel(thisObject)))::unhook);
            rollbacks.add(afterBackend.intercept(
                    updateColumnH,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (args.length > 0 && args[0] != null) {
                            safe("updateColumnH", () -> adapter.refreshColumn(args[0]));
                        }
                    })::unhook);

            // Each initColumn inflates a fresh slider/root/progress host, including reInit reuse.
            rollbacks.add(afterBackend.intercept(
                    initColumn,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> safe(
                            "column initColumn", () -> adapter.bindColumn(thisObject)))::unhook);
            rollbacks.add(afterBackend.intercept(
                    setSliderBlendColor,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> safe(
                            "column setSliderBlendColor",
                            () -> adapter.refreshColumn(thisObject)))::unhook);
            rollbacks.add(afterBackend.intercept(
                    setSliderResource,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> safe(
                            "column setSliderResource",
                            () -> adapter.refreshColumn(thisObject)))::unhook);

            // Release native glass before the column's coroutine/animation teardown loses authority.
            rollbacks.add(beforeBackend.intercept(
                    release,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> adapter.releaseColumn(thisObject))::unhook);
            rollbacks.add(beforeBackend.intercept(
                    destroy,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> adapter.destroyController(thisObject))::unhook);

            android.util.Log.i("LiquidUI",
                    "[LUI][PluginVolume] installed verified floating volume panel/column adapters package="
                            + pluginContext.getPackageName());
            return new MiuiVolumePluginGlassSession(adapter, rollbacks);
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
                android.util.Log.e("LiquidUI", "[LUI][PluginVolume] unhook failed", error);
            }
        }
        try {
            adapter.close();
        } catch (Throwable error) {
            android.util.Log.e("LiquidUI", "[LUI][PluginVolume] native restore failed", error);
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
            android.util.Log.e("LiquidUI", "[LUI][PluginVolume] " + stage + " failed", error);
        }
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }
}
