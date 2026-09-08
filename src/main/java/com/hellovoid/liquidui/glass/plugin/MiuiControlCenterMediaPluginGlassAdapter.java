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

/** Shared-glass adapter for the bounded main Control Center MediaPlayerPanel card. */
public final class MiuiControlCenterMediaPluginGlassAdapter implements AutoCloseable {
    private final SystemUiGlassHostController controller;
    private final MiuiControlCenterSurfaceMaterialController material;
    private final Method getContentView;
    private final Method getCornerRadius;
    private final WeakHashMap<Object, MediaState> holders = new WeakHashMap<>();
    private boolean closed;

    public MiuiControlCenterMediaPluginGlassAdapter(
            SystemUiGlassCore glassCore,
            MiuiControlCenterSurfaceMaterialController material,
            Method getContentView,
            Method getCornerRadius) {
        controller = new SystemUiGlassHostController(
                Objects.requireNonNull(glassCore, "glassCore"),
                SystemUiGlassDomain.MEDIA,
                "plugin-control-center-media");
        this.material = Objects.requireNonNull(material, "material");
        this.getContentView = accessible(Objects.requireNonNull(getContentView, "getContentView"));
        this.getCornerRadius = accessible(Objects.requireNonNull(getCornerRadius, "getCornerRadius"));
    }

    public void bindHolder(Object holder) throws Throwable {
        if (closed || holder == null) return;
        Object hostObject = getContentView.invoke(holder);
        if (!(hostObject instanceof View host)) return;

        MediaState state = holders.get(holder);
        if (state == null) {
            state = new MediaState(holder);
            holders.put(holder, state);
        }
        if (state.host != null && state.host != host) {
            releaseState(state, "media-host-replaced");
        }
        if (state.host == null) {
            state.host = host;
            final MediaState listenerState = state;
            state.attachListener = new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    try {
                        registerIfReady(listenerState);
                    } catch (Throwable error) {
                        android.util.Log.e("LiquidUI",
                                "[LUI][PluginMedia] attach register failed", error);
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
        refreshHolder(holder);
    }

    public void refreshHolder(Object holder) throws Throwable {
        if (closed || holder == null) return;
        MediaState state = holders.get(holder);
        if (state == null || state.host == null) return;
        View host = state.host;
        if (!host.isAttachedToWindow()) return;
        if (!state.registered) registerIfReady(state);
        if (!state.registered) return;
        material.refresh(host);
        controller.update(host, geometry(holder));
    }

    public void detachHolder(Object holder, String reason) {
        if (holder == null) return;
        MediaState state = holders.remove(holder);
        if (state == null) return;
        releaseState(state, reason == null ? "media-detach" : reason);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (Map.Entry<Object, MediaState> entry : new ArrayList<>(holders.entrySet())) {
            releaseState(entry.getValue(), "media-session-close");
        }
        holders.clear();
        try { material.restoreAll(); } catch (Throwable ignored) {}
        controller.close();
    }

    private void registerIfReady(MediaState state) throws Throwable {
        View host = state.host;
        if (closed || state.registered || host == null || !host.isAttachedToWindow()) return;
        material.observe(host, host);
        controller.register(
                host,
                SystemUiMaterialHostKind.MEDIA_CARD,
                geometry(state.holder),
                material);
        state.registered = true;
    }

    private GlassHostGeometry geometry(Object holder) throws Throwable {
        Object radiusValue = getCornerRadius.invoke(holder);
        float radius = radiusValue instanceof Number number ? number.floatValue() : 0f;
        return GlassHostGeometry.rounded(radius, 1f, 20);
    }

    private void releaseState(MediaState state, String reason) {
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

    private static final class MediaState {
        final Object holder;
        View host;
        View.OnAttachStateChangeListener attachListener;
        boolean registered;

        MediaState(Object holder) {
            this.holder = holder;
        }
    }
}
