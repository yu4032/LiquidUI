package com.hellovoid.liquidui.glass.controlcenter;

import android.view.View;

import com.hellovoid.liquidui.glass.core.SystemUiGlassCore;
import com.hellovoid.liquidui.glass.systemui.GlassHostGeometry;
import com.hellovoid.liquidui.glass.systemui.SystemUiGlassDomain;
import com.hellovoid.liquidui.glass.systemui.SystemUiGlassHostController;
import com.hellovoid.liquidui.glass.systemui.SystemUiMaterialHostKind;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/** Exact built-in Quick Settings adapter; vendor discovery stays in the hook. */
public final class ControlCenterGlassAdapter implements AutoCloseable {
    private static final float CIRCLE_RADIUS_SENTINEL = 100_000f;

    private final SystemUiGlassHostController hosts;
    private final ControlCenterNativeMaterialController material;
    private final Field recordsField;
    private final Field tileViewField;
    private final Field iconFrameField;
    private final Field iconWrapperField;
    private final WeakHashMap<Object, Set<View>> panelHosts = new WeakHashMap<>();

    public ControlCenterGlassAdapter(
            SystemUiGlassCore core,
            ControlCenterNativeMaterialController material,
            Field recordsField,
            Field tileViewField,
            Field iconFrameField,
            Field iconWrapperField) {
        this.hosts = new SystemUiGlassHostController(
                Objects.requireNonNull(core, "core"),
                SystemUiGlassDomain.CONTROL_CENTER,
                "control-center.classic-qs");
        this.material = Objects.requireNonNull(material, "material");
        this.recordsField = accessible(Objects.requireNonNull(recordsField, "recordsField"));
        this.tileViewField = accessible(Objects.requireNonNull(tileViewField, "tileViewField"));
        this.iconFrameField = accessible(Objects.requireNonNull(iconFrameField, "iconFrameField"));
        this.iconWrapperField = accessible(Objects.requireNonNull(iconWrapperField, "iconWrapperField"));
    }

    /** Reconcile one exact MiuiQSPanel after its authoritative setTiles rebuild completed. */
    public void reconcilePanel(Object panel) throws IllegalAccessException {
        if (panel == null) return;
        Object recordsValue = recordsField.get(panel);
        if (!(recordsValue instanceof Collection<?> records)) return;

        Set<View> previous = panelHosts.get(panel);
        if (previous == null) previous = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<View> next = Collections.newSetFromMap(new IdentityHashMap<>());
        int zOrder = 0;

        for (Object record : new ArrayList<>(records)) {
            if (record == null) continue;
            Object tileObject = tileViewField.get(record);
            if (!(tileObject instanceof View tileView)) continue;
            Object frameObject = iconFrameField.get(tileObject);
            Object wrapperObject = iconWrapperField.get(tileObject);
            if (!(frameObject instanceof View frame) || !(wrapperObject instanceof View)) continue;

            next.add(frame);
            material.observe(frame, wrapperObject);
            GlassHostGeometry geometry = GlassHostGeometry.rounded(
                    CIRCLE_RADIUS_SENTINEL, 1f, zOrder++);
            if (previous.contains(frame)) {
                hosts.update(frame, geometry);
            } else {
                hosts.register(frame, SystemUiMaterialHostKind.QS_TILE, geometry, material);
            }
        }

        for (View old : Set.copyOf(previous)) {
            if (next.contains(old)) continue;
            hosts.unregister(old, "qs-setTiles-reconcile");
            material.forget(old);
        }
        panelHosts.put(panel, next);
    }

    public void onIconUpdated(Object iconWrapper, boolean active) {
        if (iconWrapper == null) return;
        material.onIconUpdated(iconWrapper, active);
    }

    @Override
    public void close() {
        hosts.close();
        material.restoreAll();
        for (Map.Entry<Object, Set<View>> entry : new ArrayList<>(panelHosts.entrySet())) {
            for (View host : entry.getValue()) material.forget(host);
        }
        panelHosts.clear();
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }
}
