package com.hellovoid.liquidui.glass.plugin;

import android.view.View;

import com.hellovoid.liquidui.glass.core.SystemUiGlassCore;
import com.hellovoid.liquidui.glass.systemui.GlassHostGeometry;
import com.hellovoid.liquidui.glass.systemui.SystemUiGlassDomain;
import com.hellovoid.liquidui.glass.systemui.SystemUiGlassHostController;
import com.hellovoid.liquidui.glass.systemui.SystemUiMaterialHostKind;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Bounded adapter for the shared SecondaryPanelControllerBase contentBg material. */
public final class MiuiSecondaryPanelPluginGlassAdapter implements AutoCloseable {
    private final SystemUiGlassHostController controller;
    private final MiuiControlCenterSurfaceMaterialController material;
    private final Method getContentBg;
    private final Method getContentBgRadius;
    private final WeakHashMap<Object, PanelState> panels = new WeakHashMap<>();
    private boolean closed;

    public MiuiSecondaryPanelPluginGlassAdapter(
            SystemUiGlassCore glassCore,
            MiuiControlCenterSurfaceMaterialController material,
            Method getContentBg,
            Method getContentBgRadius) {
        controller = new SystemUiGlassHostController(
                Objects.requireNonNull(glassCore, "glassCore"),
                SystemUiGlassDomain.CONTROL_CENTER,
                "plugin-secondary-panels");
        this.material = Objects.requireNonNull(material, "material");
        this.getContentBg = accessible(Objects.requireNonNull(getContentBg, "getContentBg"));
        this.getContentBgRadius = accessible(Objects.requireNonNull(
                getContentBgRadius, "getContentBgRadius"));
    }

    public void bindPanel(Object panelController) throws Throwable {
        if (closed || panelController == null) return;
        Object hostObject = getContentBg.invoke(panelController);
        if (!(hostObject instanceof View host)) return;

        PanelState state = panels.get(panelController);
        if (state == null) {
            state = new PanelState(panelController);
            panels.put(panelController, state);
        }
        if (state.host != null && state.host != host) {
            releaseState(state, "secondary-host-replaced");
        }
        if (state.host == null) {
            state.host = host;
            final PanelState listenerState = state;
            state.attachListener = new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    try {
                        registerIfReady(listenerState);
                    } catch (Throwable error) {
                        android.util.Log.e("LiquidUI",
                                "[LUI][PluginSecondary] attach register failed", error);
                    }
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    listenerState.registered = false;
                }
            };
            host.addOnAttachStateChangeListener(state.attachListener);
        }

        material.observe(host, host);
        registerIfReady(state);
        refreshPanel(panelController);
    }

    public void refreshPanel(Object panelController) throws Throwable {
        if (closed || panelController == null) return;
        PanelState state = panels.get(panelController);
        if (state == null || state.host == null) return;
        View host = state.host;
        if (!host.isAttachedToWindow()) return;
        if (!state.registered) registerIfReady(state);
        if (!state.registered) return;
        material.refresh(host);
        controller.update(host, geometry(panelController));
    }

    public void detachPanel(Object panelController, String reason) {
        if (panelController == null) return;
        PanelState state = panels.remove(panelController);
        if (state == null) return;
        releaseState(state, reason == null ? "secondary-detach" : reason);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (Map.Entry<Object, PanelState> entry : new ArrayList<>(panels.entrySet())) {
            releaseState(entry.getValue(), "secondary-session-close");
        }
        panels.clear();
        try { material.restoreAll(); } catch (Throwable ignored) {}
        controller.close();
    }

    private void registerIfReady(PanelState state) throws Throwable {
        View host = state.host;
        if (closed || state.registered || host == null || !host.isAttachedToWindow()) return;
        material.observe(host, host);
        controller.register(
                host,
                SystemUiMaterialHostKind.CONTROL_CENTER_PANEL,
                geometry(state.panelController),
                material);
        state.registered = true;
    }

    private GlassHostGeometry geometry(Object panelController) throws Throwable {
        Object radiusValue = getContentBgRadius.invoke(panelController);
        float radius = radiusValue instanceof Number number ? number.floatValue() : 0f;
        return GlassHostGeometry.rounded(radius, 1f, 30);
    }

    private void releaseState(PanelState state, String reason) {
        View host = state.host;
        if (host == null) return;
        if (state.attachListener != null) {
            try { host.removeOnAttachStateChangeListener(state.attachListener); } catch (Throwable ignored) {}
        }
        try { controller.unregister(host, reason); } catch (Throwable ignored) {}
        try { material.forget(host); } catch (Throwable ignored) {}
        state.registered = false;
        state.attachListener = null;
        state.host = null;
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }

    private static final class PanelState {
        final Object panelController;
        View host;
        View.OnAttachStateChangeListener attachListener;
        boolean registered;

        PanelState(Object panelController) {
            this.panelController = panelController;
        }
    }
}
