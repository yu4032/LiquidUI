package com.hellovoid.liquidui.glass.statusbar;

import android.view.View;

import com.hellovoid.liquidui.glass.core.SystemUiGlassCore;
import com.hellovoid.liquidui.glass.systemui.GlassHostGeometry;
import com.hellovoid.liquidui.glass.systemui.SystemUiGlassDomain;
import com.hellovoid.liquidui.glass.systemui.SystemUiGlassHostController;
import com.hellovoid.liquidui.glass.systemui.SystemUiMaterialHostKind;

import java.util.ArrayList;
import java.util.WeakHashMap;

/** Publishes only verified ongoing-activity chip background containers as status glass nodes. */
public final class StatusBarGlassAdapter implements AutoCloseable {
    private static final String RADIUS_RESOURCE = "ongoing_activity_chip_corner_radius";
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    private final SystemUiGlassHostController hosts;
    private final StatusBarNativeMaterialController material;
    private final WeakHashMap<View, Slot> slots = new WeakHashMap<>();

    public StatusBarGlassAdapter(
            SystemUiGlassCore core,
            StatusBarNativeMaterialController material) {
        this.hosts = new SystemUiGlassHostController(
                core, SystemUiGlassDomain.STATUS_BAR, "ongoing-activity-chip");
        this.material = material;
    }

    public void bindChip(View chipRoot, View backgroundView) {
        if (chipRoot == null || backgroundView == null) return;
        Slot slot = slots.get(chipRoot);
        if (slot == null) {
            slot = new Slot();
            slots.put(chipRoot, slot);
        }
        if (slot.host != null && slot.host != backgroundView) {
            release(slot, "chip-background-replaced");
        }
        slot.host = backgroundView;
        material.observe(backgroundView);
        ensureAttachListener(slot);
        activate(slot);
    }

    public void unbindChip(View chipRoot) {
        if (chipRoot == null) return;
        Slot slot = slots.remove(chipRoot);
        if (slot != null) release(slot, "chip-inactive");
    }

    @Override
    public void close() {
        for (Slot slot : new ArrayList<>(slots.values())) release(slot, "statusbar-hook-close");
        slots.clear();
        hosts.close();
        material.restoreAll();
    }

    private void activate(Slot slot) {
        View host = slot.host;
        if (host == null || !host.isAttachedToWindow()) return;
        GlassHostGeometry geometry = GlassHostGeometry.rounded(resolveRadius(host), 1f, 0);
        if (!slot.registered) {
            hosts.register(host, SystemUiMaterialHostKind.STATUS_CAPSULE, geometry, material);
            slot.registered = true;
            return;
        }
        if (!material.refresh(host)) {
            hosts.unregister(host, "status-capsule-native-refresh-failed");
            material.forget(host);
            slot.registered = false;
            return;
        }
        hosts.update(host, geometry);
    }

    private void ensureAttachListener(Slot slot) {
        if (slot.listener != null || slot.host == null) return;
        slot.listener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                try {
                    activate(slot);
                } catch (Throwable error) {
                    android.util.Log.e("LiquidUI", "[LUI][StatusGlass] chip attach failed", error);
                }
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                if (!slot.registered) return;
                hosts.unregister(v, "status-capsule-detached");
                slot.registered = false;
            }
        };
        slot.host.addOnAttachStateChangeListener(slot.listener);
    }

    private void release(Slot slot, String reason) {
        View host = slot.host;
        if (host != null && slot.listener != null) {
            try { host.removeOnAttachStateChangeListener(slot.listener); } catch (Throwable ignored) {}
        }
        if (host != null && slot.registered) hosts.unregister(host, reason);
        if (host != null) material.forget(host);
        slot.host = null;
        slot.listener = null;
        slot.registered = false;
    }

    private static float resolveRadius(View host) {
        int id = host.getResources().getIdentifier(
                RADIUS_RESOURCE, "dimen", SYSTEMUI_PACKAGE);
        if (id != 0) {
            try {
                float radius = host.getResources().getDimension(id);
                if (radius > 0f) return radius;
            } catch (RuntimeException ignored) {}
        }
        int min = Math.min(host.getWidth(), host.getHeight());
        return min > 0 ? min * 0.5f : 0f;
    }

    private static final class Slot {
        View host;
        View.OnAttachStateChangeListener listener;
        boolean registered;
    }
}
