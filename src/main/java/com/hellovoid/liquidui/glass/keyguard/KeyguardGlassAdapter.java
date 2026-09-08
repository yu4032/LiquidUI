package com.hellovoid.liquidui.glass.keyguard;

import android.view.View;
import android.view.ViewGroup;

import com.hellovoid.liquidui.glass.core.SystemUiGlassCore;
import com.hellovoid.liquidui.glass.systemui.GlassHostGeometry;
import com.hellovoid.liquidui.glass.systemui.SystemUiGlassDomain;
import com.hellovoid.liquidui.glass.systemui.SystemUiGlassHostController;
import com.hellovoid.liquidui.glass.systemui.SystemUiMaterialHostKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/** Exact systemui-001 adapter for the two bounded Keyguard quick-affordance buttons. */
public final class KeyguardGlassAdapter implements AutoCloseable {
    private static final String RADIUS_DIMEN = "keyguard_affordance_fixed_radius";

    private final SystemUiGlassHostController hosts;
    private final KeyguardNativeMaterialController material;
    private final int startButtonId;
    private final int endButtonId;
    private final WeakHashMap<Object, Set<View>> sectionHosts = new WeakHashMap<>();
    private final WeakHashMap<View, ButtonSlot> buttonSlots = new WeakHashMap<>();

    public KeyguardGlassAdapter(
            SystemUiGlassCore core,
            KeyguardNativeMaterialController material,
            int startButtonId,
            int endButtonId) {
        this.hosts = new SystemUiGlassHostController(
                Objects.requireNonNull(core, "core"),
                SystemUiGlassDomain.KEYGUARD,
                "keyguard.quick-affordance");
        this.material = Objects.requireNonNull(material, "material");
        this.startButtonId = startButtonId;
        this.endButtonId = endButtonId;
    }

    /** Called after DefaultShortcutsSection.addViews() created both exact bounded buttons. */
    public void bindSection(Object section, ViewGroup root) {
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(root, "root");

        Set<View> previous = sectionHosts.get(section);
        if (previous == null) previous = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<View> next = Collections.newSetFromMap(new IdentityHashMap<>());

        bindButton(root.findViewById(startButtonId), next, 0);
        bindButton(root.findViewById(endButtonId), next, 1);

        for (View old : Set.copyOf(previous)) {
            if (next.contains(old)) continue;
            releaseButton(old, "keyguard-shortcut-replaced");
        }
        sectionHosts.put(section, next);
    }

    /** Explicit section teardown; detach remains a secondary fail-safe. */
    public void unbindSection(Object section) {
        if (section == null) return;
        Set<View> previous = sectionHosts.remove(section);
        if (previous == null) return;
        for (View host : Set.copyOf(previous)) {
            releaseButton(host, "keyguard-shortcuts-removeViews");
        }
    }

    @Override
    public void close() {
        for (View host : new ArrayList<>(buttonSlots.keySet())) {
            releaseButton(host, "keyguard-adapter-close");
        }
        hosts.close();
        material.restoreAll();
        sectionHosts.clear();
        buttonSlots.clear();
    }

    private void bindButton(View host, Set<View> next, int zOrder) {
        if (host == null) {
            throw new IllegalStateException("systemui-001 Keyguard quick-affordance button missing");
        }
        next.add(host);
        material.observe(host);

        ButtonSlot slot = buttonSlots.get(host);
        if (slot == null) {
            slot = new ButtonSlot(host);
            buttonSlots.put(host, slot);
            host.addOnAttachStateChangeListener(slot.listener);
        }
        slot.zOrder = zOrder;
        slot.radius = radiusPx(host);

        if (host.isAttachedToWindow()) {
            activate(slot);
        }
    }

    private void activate(ButtonSlot slot) {
        View host = slot.host;
        GlassHostGeometry geometry = GlassHostGeometry.rounded(slot.radius, 1f, slot.zOrder);
        if (slot.registered) {
            hosts.update(host, geometry);
            return;
        }
        hosts.register(host, SystemUiMaterialHostKind.KEYGUARD_TILE, geometry, material);
        slot.registered = true;
    }

    private void releaseButton(View host, String reason) {
        ButtonSlot slot = buttonSlots.remove(host);
        if (slot != null) {
            try { host.removeOnAttachStateChangeListener(slot.listener); } catch (Throwable ignored) {}
            if (slot.registered) {
                hosts.unregister(host, reason);
                slot.registered = false;
            }
        }
        material.forget(host);
    }

    private static float radiusPx(View host) {
        int id = host.getResources().getIdentifier(
                RADIUS_DIMEN, "dimen", "com.android.systemui");
        if (id == 0) {
            throw new IllegalStateException("missing SystemUI dimen " + RADIUS_DIMEN);
        }
        return Math.max(0f, host.getResources().getDimension(id));
    }

    private final class ButtonSlot {
        final View host;
        final View.OnAttachStateChangeListener listener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                try {
                    activate(ButtonSlot.this);
                } catch (Throwable error) {
                    android.util.Log.e("LiquidUI",
                            "[LUI][KeyguardGlass] affordance attach failed", error);
                }
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                if (!registered) return;
                hosts.unregister(v, "keyguard-affordance-detached");
                registered = false;
            }
        };

        int zOrder;
        float radius;
        boolean registered;

        ButtonSlot(View host) {
            this.host = Objects.requireNonNull(host, "host");
        }
    }
}
