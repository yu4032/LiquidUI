package com.hellovoid.liquidui.glass.plugin;

import android.content.Context;
import android.view.View;

import com.hellovoid.liquidui.glass.core.SystemUiGlassCore;
import com.hellovoid.liquidui.hook.AfterMethodHookBackend;
import com.hellovoid.liquidui.reflect.TargetClassResolver;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** One loaded miui.systemui.plugin ClassLoader session for verified Control Center glass hosts. */
public final class MiuiControlCenterPluginGlassSession implements AutoCloseable {
    private static final String TILE_VIEW =
            "miui.systemui.controlcenter.qs.tileview.QSTileItemView";
    private static final String TILE_ICON_VIEW =
            "miui.systemui.controlcenter.qs.tileview.QSTileItemIconView";
    private static final String CARD_VIEW =
            "miui.systemui.controlcenter.qs.tileview.QSCardItemView";
    private static final String CARD_ICON_VIEW =
            "miui.systemui.controlcenter.qs.tileview.QSCardItemIconView";
    private static final String TILE_BINDING =
            "miui.systemui.controlcenter.databinding.QsTileItemViewBinding";
    private static final String SLIDER_BINDING =
            "miui.systemui.controlcenter.databinding.ToggleSliderItemViewBinding";
    private static final String TOGGLE_SLIDER_HOLDER =
            "miui.systemui.controlcenter.panel.main.recyclerview.ToggleSliderViewHolder";
    private static final String MAIN_PANEL_ITEM_HOLDER =
            "miui.systemui.controlcenter.panel.main.recyclerview.MainPanelItemViewHolder";
    private static final String CONTROL_CENTER_HOLDER =
            "miui.systemui.controlcenter.panel.main.recyclerview.ControlCenterViewHolder";
    private static final String QS_TILE_STATE =
            "com.android.systemui.plugins.qs.QSTile$State";

    private final MiuiControlCenterPluginGlassAdapter adapter;
    private final List<Runnable> unhooks;
    private boolean closed;

    private MiuiControlCenterPluginGlassSession(
            MiuiControlCenterPluginGlassAdapter adapter,
            List<Runnable> unhooks) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.unhooks = List.copyOf(unhooks);
    }

    public static MiuiControlCenterPluginGlassSession install(
            ClassLoader pluginClassLoader,
            Context pluginContext,
            SystemUiGlassCore glassCore,
            AfterMethodHookBackend afterBackend) throws Throwable {
        Objects.requireNonNull(pluginClassLoader, "pluginClassLoader");
        Objects.requireNonNull(pluginContext, "pluginContext");
        Objects.requireNonNull(glassCore, "glassCore");
        Objects.requireNonNull(afterBackend, "afterBackend");

        Class<?> tileClass = TargetClassResolver.require(pluginClassLoader, TILE_VIEW);
        Class<?> tileIconClass = TargetClassResolver.require(pluginClassLoader, TILE_ICON_VIEW);
        Class<?> cardClass = TargetClassResolver.require(pluginClassLoader, CARD_VIEW);
        Class<?> cardIconClass = TargetClassResolver.require(pluginClassLoader, CARD_ICON_VIEW);
        Class<?> tileBindingClass = TargetClassResolver.require(pluginClassLoader, TILE_BINDING);
        Class<?> sliderBindingClass = TargetClassResolver.require(pluginClassLoader, SLIDER_BINDING);
        Class<?> toggleSliderClass = TargetClassResolver.require(pluginClassLoader, TOGGLE_SLIDER_HOLDER);
        Class<?> mainPanelItemClass = TargetClassResolver.require(pluginClassLoader, MAIN_PANEL_ITEM_HOLDER);
        Class<?> controlCenterHolderClass = TargetClassResolver.require(pluginClassLoader, CONTROL_CENTER_HOLDER);
        Class<?> qsTileStateClass = TargetClassResolver.require(pluginClassLoader, QS_TILE_STATE);

        if (!mainPanelItemClass.isAssignableFrom(toggleSliderClass)
                || !controlCenterHolderClass.isAssignableFrom(mainPanelItemClass)) {
            throw new IllegalStateException("Control Center ViewHolder inheritance contract changed");
        }

        Method tileInit = accessible(tileClass.getDeclaredMethod("init", tileIconClass));
        Method tileRecycle = accessible(tileClass.getDeclaredMethod("recycle"));
        Method tileGetBinding = accessible(tileClass.getDeclaredMethod("getBinding"));
        Method tileGetIcon = accessible(tileClass.getDeclaredMethod("getIcon"));
        Method tileIconGetIcon = accessible(tileIconClass.getDeclaredMethod("getIcon"));
        Method tileIconGetCornerRadius = accessible(
                tileIconClass.getDeclaredMethod("getCornerRadius"));
        Method updateIcon = accessible(tileIconClass.getDeclaredMethod(
                "updateIcon",
                qsTileStateClass,
                boolean.class,
                boolean.class,
                boolean.class,
                boolean.class));

        Method cardInit = accessible(cardClass.getDeclaredMethod("init", cardIconClass));
        Method cardUpdateBackground = accessible(
                cardClass.getDeclaredMethod("updateBackground", boolean.class));
        Method cardRecycle = accessible(cardClass.getDeclaredMethod("recycle"));
        Method cardGetCornerRadius = accessible(cardClass.getDeclaredMethod("getCornerRadius"));

        Method sliderGetBinding = accessible(toggleSliderClass.getDeclaredMethod("getBinding"));
        Method sliderGetOutlineRadius = accessible(
                toggleSliderClass.getDeclaredMethod("getOutlineRadius"));
        Method updateBlendBlur = accessible(toggleSliderClass.getDeclaredMethod("updateBlendBlur"));
        Method onConfigurationChanged = accessible(
                toggleSliderClass.getDeclaredMethod("onConfigurationChanged", int.class));
        Method onViewAttachedToWindow = accessible(
                controlCenterHolderClass.getDeclaredMethod("onViewAttachedToWindow"));
        Method onViewDetachedFromWindow = accessible(
                controlCenterHolderClass.getDeclaredMethod("onViewDetachedFromWindow"));
        Method recycleHolder = accessible(controlCenterHolderClass.getDeclaredMethod("recycle"));

        Field iconFrame = accessible(tileBindingClass.getDeclaredField("iconFrame"));
        Field toggleSliderInner = accessible(sliderBindingClass.getDeclaredField("toggleSliderInner"));
        Field progressBg = accessible(sliderBindingClass.getDeclaredField("progressBg"));

        Method setMiViewBlurMode = View.class.getMethod("setMiViewBlurMode", int.class);
        Method clearMiBackgroundBlendColor =
                View.class.getMethod("clearMiBackgroundBlendColor");
        Method getMiBackgroundBlendColor = View.class.getMethod(
                "getMiBackgroundBlendColor", ArrayList.class, ArrayList.class);
        Method setMiBackgroundBlendColors =
                View.class.getMethod("setMiBackgroundBlendColors", ArrayList.class);

        MiuiControlCenterTileMaterialController tileMaterial =
                new MiuiControlCenterTileMaterialController(
                        setMiViewBlurMode,
                        clearMiBackgroundBlendColor,
                        getMiBackgroundBlendColor,
                        setMiBackgroundBlendColors);
        MiuiControlCenterSurfaceMaterialController surfaceMaterial =
                new MiuiControlCenterSurfaceMaterialController(
                        setMiViewBlurMode,
                        clearMiBackgroundBlendColor,
                        getMiBackgroundBlendColor,
                        setMiBackgroundBlendColors);
        MiuiControlCenterPluginGlassAdapter adapter = new MiuiControlCenterPluginGlassAdapter(
                glassCore,
                tileMaterial,
                surfaceMaterial,
                tileGetBinding,
                tileGetIcon,
                tileIconGetIcon,
                tileIconGetCornerRadius,
                iconFrame,
                cardGetCornerRadius,
                sliderGetBinding,
                sliderGetOutlineRadius,
                toggleSliderInner,
                progressBg);

        List<Runnable> rollbacks = new ArrayList<>();
        try {
            rollbacks.add(afterBackend.intercept(
                    tileInit,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> safe("tile init", () -> adapter.bindTile(thisObject)))::unhook);
            rollbacks.add(afterBackend.intercept(
                    updateIcon,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> safe(
                            "tile updateIcon", () -> adapter.onTileIconUpdated(thisObject)))::unhook);
            rollbacks.add(afterBackend.intercept(
                    tileRecycle,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> adapter.recycleTile(thisObject))::unhook);

            rollbacks.add(afterBackend.intercept(
                    cardInit,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> safe("card init", () -> adapter.bindCard(thisObject)))::unhook);
            rollbacks.add(afterBackend.intercept(
                    cardUpdateBackground,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> safe(
                            "card updateBackground",
                            () -> adapter.onCardBackgroundUpdated(thisObject)))::unhook);
            rollbacks.add(afterBackend.intercept(
                    cardRecycle,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> adapter.recycleCard(thisObject))::unhook);

            rollbacks.add(afterBackend.intercept(
                    onViewAttachedToWindow,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (!toggleSliderClass.isInstance(thisObject)) return;
                        safe("slider attach", () -> adapter.bindSlider(thisObject));
                    })::unhook);
            rollbacks.add(afterBackend.intercept(
                    onViewDetachedFromWindow,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (toggleSliderClass.isInstance(thisObject)) {
                            adapter.detachSlider(thisObject);
                        }
                    })::unhook);
            rollbacks.add(afterBackend.intercept(
                    updateBlendBlur,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> safe(
                            "slider updateBlendBlur",
                            () -> adapter.onSliderMaterialUpdated(thisObject)))::unhook);
            rollbacks.add(afterBackend.intercept(
                    onConfigurationChanged,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> safe(
                            "slider onConfigurationChanged",
                            () -> adapter.onSliderMaterialUpdated(thisObject)))::unhook);
            rollbacks.add(afterBackend.intercept(
                    recycleHolder,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (toggleSliderClass.isInstance(thisObject)) {
                            adapter.recycleSlider(thisObject);
                        }
                    })::unhook);

            android.util.Log.i("LiquidUI",
                    "[LUI][PluginCC] installed verified tile/card/brightness adapters package="
                            + pluginContext.getPackageName());
            return new MiuiControlCenterPluginGlassSession(adapter, rollbacks);
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
                android.util.Log.e("LiquidUI", "[LUI][PluginCC] unhook failed", error);
            }
        }
        try {
            adapter.close();
        } catch (Throwable error) {
            android.util.Log.e("LiquidUI", "[LUI][PluginCC] native restore failed", error);
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
            android.util.Log.e("LiquidUI", "[LUI][PluginCC] " + stage + " failed", error);
        }
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }
}
