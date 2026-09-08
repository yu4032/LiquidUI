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

/** Verified plugin-ClassLoader session for the main Control Center media card. */
public final class MiuiControlCenterMediaPluginGlassSession implements AutoCloseable {
    private static final String MEDIA_HOLDER =
            "miui.systemui.controlcenter.panel.main.media.MediaPlayerController$MediaPlayerViewHolder";
    private static final String MEDIA_CONTROLLER =
            "miui.systemui.controlcenter.panel.main.media.MediaPlayerController";
    private static final String CONTROL_CENTER_HOLDER =
            "miui.systemui.controlcenter.panel.main.recyclerview.ControlCenterViewHolder";

    private final MiuiControlCenterMediaPluginGlassAdapter adapter;
    private final List<Runnable> unhooks;
    private boolean closed;

    private MiuiControlCenterMediaPluginGlassSession(
            MiuiControlCenterMediaPluginGlassAdapter adapter,
            List<Runnable> unhooks) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.unhooks = List.copyOf(unhooks);
    }

    public static MiuiControlCenterMediaPluginGlassSession install(
            ClassLoader pluginClassLoader,
            Context pluginContext,
            SystemUiGlassCore glassCore,
            AfterMethodHookBackend afterBackend) throws Throwable {
        Objects.requireNonNull(pluginClassLoader, "pluginClassLoader");
        Objects.requireNonNull(pluginContext, "pluginContext");
        Objects.requireNonNull(glassCore, "glassCore");
        Objects.requireNonNull(afterBackend, "afterBackend");

        Class<?> mediaHolderClass = TargetClassResolver.require(pluginClassLoader, MEDIA_HOLDER);
        Class<?> mediaControllerClass = TargetClassResolver.require(pluginClassLoader, MEDIA_CONTROLLER);
        Class<?> controlCenterHolderClass = TargetClassResolver.require(
                pluginClassLoader, CONTROL_CENTER_HOLDER);
        if (!controlCenterHolderClass.isAssignableFrom(mediaHolderClass)) {
            throw new IllegalStateException("Control Center media holder inheritance contract changed");
        }

        Method getContentView = accessible(mediaHolderClass.getDeclaredMethod("getContentView"));
        Method getCornerRadius = accessible(mediaHolderClass.getDeclaredMethod("getCornerRadius"));
        Method updateBlendBlur = accessible(mediaHolderClass.getDeclaredMethod("updateBlendBlur"));
        Method onConfigurationChanged = accessible(
                mediaHolderClass.getDeclaredMethod("onConfigurationChanged", int.class));
        Method onViewAttachedToWindow = accessible(
                controlCenterHolderClass.getDeclaredMethod("onViewAttachedToWindow"));
        Method onViewDetachedFromWindow = accessible(
                controlCenterHolderClass.getDeclaredMethod("onViewDetachedFromWindow"));
        Method recycle = accessible(controlCenterHolderClass.getDeclaredMethod("recycle"));
        Method onUnbindViewHolder = accessible(
                mediaControllerClass.getDeclaredMethod("onUnbindViewHolder"));
        Method getViewHolder = accessible(mediaControllerClass.getDeclaredMethod("getViewHolder"));

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
        MiuiControlCenterMediaPluginGlassAdapter adapter =
                new MiuiControlCenterMediaPluginGlassAdapter(
                        glassCore, material, getContentView, getCornerRadius);

        List<Runnable> rollbacks = new ArrayList<>();
        try {
            rollbacks.add(afterBackend.intercept(
                    onViewAttachedToWindow,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (mediaHolderClass.isInstance(thisObject)) {
                            safe("media attach", () -> adapter.bindHolder(thisObject));
                        }
                    })::unhook);
            rollbacks.add(afterBackend.intercept(
                    onViewDetachedFromWindow,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (mediaHolderClass.isInstance(thisObject)) {
                            adapter.detachHolder(thisObject, "media-detached");
                        }
                    })::unhook);
            rollbacks.add(afterBackend.intercept(
                    recycle,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (mediaHolderClass.isInstance(thisObject)) {
                            adapter.detachHolder(thisObject, "media-recycle");
                        }
                    })::unhook);
            rollbacks.add(afterBackend.intercept(
                    updateBlendBlur,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> safe(
                            "media updateBlendBlur", () -> adapter.refreshHolder(thisObject)))::unhook);
            rollbacks.add(afterBackend.intercept(
                    onConfigurationChanged,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> safe(
                            "media onConfigurationChanged", () -> adapter.refreshHolder(thisObject)))::unhook);
            rollbacks.add(afterBackend.intercept(
                    onUnbindViewHolder,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> safe("media onUnbindViewHolder", () -> {
                        Object holder = getViewHolder.invoke(thisObject);
                        if (holder != null) adapter.detachHolder(holder, "media-unbind");
                    }))::unhook);

            android.util.Log.i("LiquidUI",
                    "[LUI][PluginMedia] installed verified main media-card adapter package="
                            + pluginContext.getPackageName());
            return new MiuiControlCenterMediaPluginGlassSession(adapter, rollbacks);
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
                android.util.Log.e("LiquidUI", "[LUI][PluginMedia] unhook failed", error);
            }
        }
        try { adapter.close(); } catch (Throwable error) {
            android.util.Log.e("LiquidUI", "[LUI][PluginMedia] restore failed", error);
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
            android.util.Log.e("LiquidUI", "[LUI][PluginMedia] " + stage + " failed", error);
        }
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }
}
