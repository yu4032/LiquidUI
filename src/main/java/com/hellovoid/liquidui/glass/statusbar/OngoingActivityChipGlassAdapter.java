package com.hellovoid.liquidui.glass.statusbar;

import android.content.res.Resources;
import android.view.View;

import com.hellovoid.liquidui.glass.core.SystemUiGlassCore;
import com.hellovoid.liquidui.glass.systemui.GlassHostGeometry;
import com.hellovoid.liquidui.glass.systemui.SystemUiGlassDomain;
import com.hellovoid.liquidui.glass.systemui.SystemUiGlassHostController;
import com.hellovoid.liquidui.glass.systemui.SystemUiMaterialHostKind;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Bounded shared-glass adapter for active status-bar ongoing-activity chips. */
public final class OngoingActivityChipGlassAdapter implements AutoCloseable {
    private static final String CORNER_RADIUS = "ongoing_activity_chip_corner_radius";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";

    private final SystemUiGlassHostController controller;
    private final OngoingActivityChipNativeMaterialController material;
    private final WeakHashMap<Object, ChipState> bindings = new WeakHashMap<>();
    private boolean closed;

    public OngoingActivityChipGlassAdapter(
            SystemUiGlassCore glassCore,
            OngoingActivityChipNativeMaterialController material) {
        controller = new SystemUiGlassHostController(
                Objects.requireNonNull(glassCore, "glassCore"),
                SystemUiGlassDomain.STATUS_BAR,
                "ongoing-activity-chip");
        this.material = Objects.requireNonNull(material, "material");
    }

    public void bindActive(Object binding, View backgroundView) {
        if (closed || binding == null || backgroundView == null) return;
        ChipState state = bindings.get(binding);
        if (state == null) {
            state = new ChipState(binding);
            bindings.put(binding, state);
        }
        if (state.host != null && state.host != backgroundView) {
            releaseState(state, "chip-background-replaced");
        }
        if (state.host == null) {
            state.host = backgroundView;
            final ChipState listenerState = state;
            state.attachListener = new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    try {
                        registerIfReady(listenerState);
                    } catch (Throwable error) {
                        android.util.Log.e("LiquidUI",
                                "[LUI][OngoingChip] attach register failed", error);
                    }
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    listenerState.registered = false;
                }
            };
            backgroundView.addOnAttachStateChangeListener(state.attachListener);
        }

        material.observe(backgroundView);
        try {
            registerIfReady(state);
            if (state.registered) {
                material.refresh(backgroundView);
                controller.update(backgroundView, geometry(backgroundView));
            }
        } catch (Throwable error) {
            android.util.Log.e("LiquidUI", "[LUI][OngoingChip] bind failed", error);
            unbind(binding, "chip-bind-failed");
        }
    }

    public void unbind(Object binding, String reason) {
        if (binding == null) return;
        ChipState state = bindings.remove(binding);
        if (state == null) return;
        releaseState(state, reason == null ? "chip-inactive" : reason);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (Map.Entry<Object, ChipState> entry : new ArrayList<>(bindings.entrySet())) {
            releaseState(entry.getValue(), "chip-adapter-close");
        }
        bindings.clear();
        try { material.restoreAll(); } catch (Throwable ignored) {}
        controller.close();
    }

    private void registerIfReady(ChipState state) {
        View host = state.host;
        if (closed || state.registered || host == null || !host.isAttachedToWindow()) return;
        material.observe(host);
        controller.register(
                host,
                SystemUiMaterialHostKind.STATUS_CAPSULE,
                geometry(host),
                material);
        state.registered = true;
    }

    private static GlassHostGeometry geometry(View host) {
        Resources resources = host.getResources();
        int radiusId = resources.getIdentifier(
                CORNER_RADIUS, "dimen", SYSTEM_UI_PACKAGE);
        float radius = radiusId != 0 ? resources.getDimension(radiusId) : 0f;
        return GlassHostGeometry.rounded(radius, 1f, 100);
    }

    private void releaseState(ChipState state, String reason) {
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

    private static final class ChipState {
        final Object binding;
        View host;
        View.OnAttachStateChangeListener attachListener;
        boolean registered;

        ChipState(Object binding) {
            this.binding = binding;
        }
    }
}
