package com.hellovoid.liquidui.glass.plugin;

import android.view.View;

import com.hellovoid.liquidui.glass.core.SystemUiGlassCore;
import com.hellovoid.liquidui.glass.systemui.GlassHostGeometry;
import com.hellovoid.liquidui.glass.systemui.SystemUiGlassDomain;
import com.hellovoid.liquidui.glass.systemui.SystemUiGlassHostController;
import com.hellovoid.liquidui.glass.systemui.SystemUiMaterialHostKind;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Objects;
import java.util.WeakHashMap;

/** Exact adapter for the plugin-owned floating HyperOS Volume panel and VolumeColumn views. */
public final class MiuiVolumePluginGlassAdapter implements AutoCloseable {
    private static final String PLUGIN_PACKAGE = "miui.systemui.plugin";
    private static final String PANEL_RADIUS = "o3_miui_volume_bg_radius";

    private final SystemUiGlassHostController hosts;
    private final MiuiVolumePanelMaterialController panelMaterial;
    private final MiuiVolumeColumnMaterialController columnMaterial;

    private final Field mExpandBgView;
    private final Field mVolumeContentView;
    private final Method getView;
    private final Method getSlider;
    private final Method getProgressViewBg;
    private final Method getRadius;

    private final WeakHashMap<Object, PanelSlot> panels = new WeakHashMap<>();
    private final WeakHashMap<Object, ColumnSlot> columns = new WeakHashMap<>();

    public MiuiVolumePluginGlassAdapter(
            SystemUiGlassCore core,
            MiuiVolumePanelMaterialController panelMaterial,
            MiuiVolumeColumnMaterialController columnMaterial,
            Field mExpandBgView,
            Field mVolumeContentView,
            Method getView,
            Method getSlider,
            Method getProgressViewBg,
            Method getRadius) {
        this.hosts = new SystemUiGlassHostController(
                Objects.requireNonNull(core, "core"),
                SystemUiGlassDomain.VOLUME,
                "miui-plugin.floating-volume");
        this.panelMaterial = Objects.requireNonNull(panelMaterial, "panelMaterial");
        this.columnMaterial = Objects.requireNonNull(columnMaterial, "columnMaterial");
        this.mExpandBgView = accessible(mExpandBgView);
        this.mVolumeContentView = accessible(mVolumeContentView);
        this.getView = accessible(getView);
        this.getSlider = accessible(getSlider);
        this.getProgressViewBg = accessible(getProgressViewBg);
        this.getRadius = accessible(getRadius);
    }

    /** initPanelView() has replaced every authoritative panel field before this call. */
    public void bindPanel(Object controller) throws ReflectiveOperationException {
        Objects.requireNonNull(controller, "controller");
        View host = requireView(mExpandBgView.get(controller), "mExpandBgView");
        View content = requireView(mVolumeContentView.get(controller), "mVolumeContentView");

        PanelSlot slot = panels.get(controller);
        if (slot == null) {
            slot = new PanelSlot();
            panels.put(controller, slot);
        }
        if (slot.host != null && slot.host != host) {
            releasePanelView(slot, "volume-panel-root-replaced");
        }
        slot.host = host;
        slot.content = content;
        panelMaterial.observe(host, content);
        ensurePanelAttachListener(slot);
        activatePanel(slot);
    }

    /** updateExpandedH() and related native material work may recreate panel backgrounds. */
    public void refreshPanel(Object controller) throws ReflectiveOperationException {
        bindPanel(controller);
    }

    /** Called before reInit() replaces VolumePanelView and its full child tree. */
    public void beforeReInit(Object controller) {
        PanelSlot panel = panels.get(controller);
        if (panel != null) releasePanelView(panel, "volume-panel-reinit");
        for (ColumnSlot slot : new ArrayList<>(columns.values())) {
            releaseColumnView(slot, "volume-column-reinit");
        }
    }

    public void destroyController(Object controller) {
        PanelSlot panel = panels.remove(controller);
        if (panel != null) releasePanelView(panel, "volume-controller-destroy");
        for (Object column : new ArrayList<>(columns.keySet())) {
            releaseColumn(column);
        }
    }

    /** VolumeColumn.initColumn() resolved exact root/slider/progress host identities. */
    public void bindColumn(Object column) throws ReflectiveOperationException {
        Objects.requireNonNull(column, "column");
        View blurRoot = requireView(getView.invoke(column), "VolumeColumn.getView");
        View slider = requireView(getSlider.invoke(column), "VolumeColumn.getSlider");
        View host = requireView(getProgressViewBg.invoke(column), "VolumeColumn.getProgressViewBg");

        ColumnSlot slot = columns.get(column);
        if (slot == null) {
            slot = new ColumnSlot();
            columns.put(column, slot);
        }
        if (slot.host != null && slot.host != host) {
            releaseColumnView(slot, "volume-column-root-replaced");
        }
        slot.column = column;
        slot.host = host;
        slot.blurRoot = blurRoot;
        slot.slider = slider;
        columnMaterial.observe(host, blurRoot, slider);
        ensureColumnAttachListener(slot);
        activateColumn(slot);
    }

    /** updateColumnH/setSliderBlendColor/setSliderResource are native material/radius authorities. */
    public void refreshColumn(Object column) throws ReflectiveOperationException {
        bindColumn(column);
    }

    public void releaseColumn(Object column) {
        ColumnSlot slot = columns.remove(column);
        if (slot != null) releaseColumnView(slot, "volume-column-release");
    }

    @Override
    public void close() {
        for (PanelSlot slot : new ArrayList<>(panels.values())) {
            releasePanelView(slot, "volume-session-close");
        }
        for (ColumnSlot slot : new ArrayList<>(columns.values())) {
            releaseColumnView(slot, "volume-session-close");
        }
        panels.clear();
        columns.clear();
        hosts.close();
        panelMaterial.restoreAll();
        columnMaterial.restoreAll();
    }

    private void activatePanel(PanelSlot slot) {
        View host = slot.host;
        if (host == null || !host.isAttachedToWindow()) return;
        GlassHostGeometry geometry = GlassHostGeometry.rounded(panelRadius(host), 1f, 0);
        if (!slot.registered) {
            hosts.register(host, SystemUiMaterialHostKind.VOLUME_PANEL, geometry, panelMaterial);
            slot.registered = true;
            return;
        }
        if (!panelMaterial.refresh(host)) {
            hosts.unregister(host, "volume-panel-native-refresh-failed");
            panelMaterial.forget(host);
            slot.registered = false;
            return;
        }
        hosts.update(host, geometry);
    }

    private void activateColumn(ColumnSlot slot) throws ReflectiveOperationException {
        View host = slot.host;
        if (host == null || !host.isAttachedToWindow()) return;
        float radius = number(getRadius.invoke(slot.column));
        if (radius <= 0f) {
            int min = Math.min(host.getWidth(), host.getHeight());
            radius = min > 0 ? min * 0.5f : 0f;
        }
        GlassHostGeometry geometry = GlassHostGeometry.rounded(radius, 1f, 0);
        if (!slot.registered) {
            hosts.register(host, SystemUiMaterialHostKind.VOLUME_SLIDER, geometry, columnMaterial);
            slot.registered = true;
            return;
        }
        if (!columnMaterial.refresh(host)) {
            hosts.unregister(host, "volume-column-native-refresh-failed");
            columnMaterial.forget(host);
            slot.registered = false;
            return;
        }
        hosts.update(host, geometry);
    }

    private void ensurePanelAttachListener(PanelSlot slot) {
        if (slot.listener != null || slot.host == null) return;
        slot.listener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                try {
                    activatePanel(slot);
                } catch (Throwable error) {
                    android.util.Log.e("LiquidUI", "[LUI][PluginVolume] panel attach failed", error);
                }
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                if (!slot.registered) return;
                hosts.unregister(v, "volume-panel-detached");
                slot.registered = false;
            }
        };
        slot.host.addOnAttachStateChangeListener(slot.listener);
    }

    private void ensureColumnAttachListener(ColumnSlot slot) {
        if (slot.listener != null || slot.host == null) return;
        slot.listener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                try {
                    activateColumn(slot);
                } catch (Throwable error) {
                    android.util.Log.e("LiquidUI", "[LUI][PluginVolume] column attach failed", error);
                }
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                if (!slot.registered) return;
                hosts.unregister(v, "volume-column-detached");
                slot.registered = false;
            }
        };
        slot.host.addOnAttachStateChangeListener(slot.listener);
    }

    private void releasePanelView(PanelSlot slot, String reason) {
        if (slot.host != null && slot.listener != null) {
            try { slot.host.removeOnAttachStateChangeListener(slot.listener); } catch (Throwable ignored) {}
        }
        if (slot.host != null && slot.registered) hosts.unregister(slot.host, reason);
        if (slot.host != null) panelMaterial.forget(slot.host);
        slot.host = null;
        slot.content = null;
        slot.listener = null;
        slot.registered = false;
    }

    private void releaseColumnView(ColumnSlot slot, String reason) {
        if (slot.host != null && slot.listener != null) {
            try { slot.host.removeOnAttachStateChangeListener(slot.listener); } catch (Throwable ignored) {}
        }
        if (slot.host != null && slot.registered) hosts.unregister(slot.host, reason);
        if (slot.host != null) columnMaterial.forget(slot.host);
        slot.host = null;
        slot.blurRoot = null;
        slot.slider = null;
        slot.listener = null;
        slot.registered = false;
    }

    private static float panelRadius(View host) {
        int id = host.getResources().getIdentifier(PANEL_RADIUS, "dimen", PLUGIN_PACKAGE);
        if (id != 0) {
            try {
                float value = host.getResources().getDimension(id);
                if (value > 0f) return value;
            } catch (RuntimeException ignored) {}
        }
        int min = Math.min(host.getWidth(), host.getHeight());
        return min > 0 ? min * 0.5f : 0f;
    }

    private static View requireView(Object value, String label) {
        if (value instanceof View view) return view;
        throw new IllegalStateException(label + " is not android.view.View");
    }

    private static float number(Object value) {
        return value instanceof Number number ? number.floatValue() : 0f;
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        Objects.requireNonNull(value, "reflection member");
        value.setAccessible(true);
        return value;
    }

    private static final class PanelSlot {
        View host;
        View content;
        View.OnAttachStateChangeListener listener;
        boolean registered;
    }

    private static final class ColumnSlot {
        Object column;
        View host;
        View blurRoot;
        View slider;
        View.OnAttachStateChangeListener listener;
        boolean registered;
    }
}
