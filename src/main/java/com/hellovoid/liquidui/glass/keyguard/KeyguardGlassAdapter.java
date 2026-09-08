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

        bindButton(root.findViewById(startButtonId), previous, next, 0);
        bindButton(root.findViewById(endButtonId), previous, next, 1);

        for (View old : Set.copyOf(previous)) {
            if (next.contains(old)) continue;
            hosts.unregister(old, "keyguard-shortcut-replaced");
            material.forget(old);
        }
        sectionHosts.put(section, next);
    }

    /** Explicit section teardown; detach remains a secondary fail-safe in the shared host controller. */
    public void unbindSection(Object section) {
        if (section == null) return;
        Set<View> previous = sectionHosts.remove(section);
        if (previous == null) return;
        for (View host : Set.copyOf(previous)) {
            hosts.unregister(host, "keyguard-shortcuts-removeViews");
            material.forget(host);
        }
    }

    @Override
    public void close() {
        hosts.close();
        material.restoreAll();
        for (Map.Entry<Object, Set<View>> entry : new ArrayList<>(sectionHosts.entrySet())) {
            for (View host : entry.getValue()) material.forget(host);
        }
        sectionHosts.clear();
    }

    private void bindButton(View host, Set<View> previous, Set<View> next, int zOrder) {
        if (host == null) {
            throw new IllegalStateException("systemui-001 Keyguard quick-affordance button missing");
        }
        next.add(host);
        material.observe(host);
        GlassHostGeometry geometry = GlassHostGeometry.rounded(radiusPx(host), 1f, zOrder);
        if (previous.contains(host)) {
            hosts.update(host, geometry);
        } else {
            hosts.register(host, SystemUiMaterialHostKind.KEYGUARD_TILE, geometry, material);
        }
    }

    private static float radiusPx(View host) {
        int id = host.getResources().getIdentifier(
                RADIUS_DIMEN, "dimen", "com.android.systemui");
        if (id == 0) {
            throw new IllegalStateException("missing SystemUI dimen " + RADIUS_DIMEN);
        }
        return Math.max(0f, host.getResources().getDimension(id));
    }
}
