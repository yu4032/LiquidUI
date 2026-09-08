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
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Converts exact plugin-owned Control Center views into generic shared Window glass nodes. */
public final class MiuiControlCenterPluginGlassAdapter implements AutoCloseable {
    private static final String UNIVERSAL_RADIUS = "control_center_universal_corner_radius";
    private static final String PLUGIN_PACKAGE = "miui.systemui.plugin";

    private final SystemUiGlassHostController hosts;
    private final MiuiControlCenterTileMaterialController tileMaterial;
    private final MiuiControlCenterSurfaceMaterialController surfaceMaterial;

    private final Method tileGetBinding;
    private final Method tileGetIcon;
    private final Method tileIconGetIcon;
    private final Method tileIconGetCornerRadius;
    private final Field iconFrame;

    private final Method cardGetCornerRadius;

    private final Method sliderGetBinding;
    private final Method sliderGetOutlineRadius;
    private final Field toggleSliderInner;
    private final Field progressBg;

    private final WeakHashMap<Object, TileSlot> tiles = new WeakHashMap<>();
    private final WeakHashMap<Object, TileSlot> tileByIcon = new WeakHashMap<>();
    private final WeakHashMap<Object, SurfaceSlot> cards = new WeakHashMap<>();
    private final WeakHashMap<Object, SurfaceSlot> sliders = new WeakHashMap<>();

    public MiuiControlCenterPluginGlassAdapter(
            SystemUiGlassCore core,
            MiuiControlCenterTileMaterialController tileMaterial,
            MiuiControlCenterSurfaceMaterialController surfaceMaterial,
            Method tileGetBinding,
            Method tileGetIcon,
            Method tileIconGetIcon,
            Method tileIconGetCornerRadius,
            Field iconFrame,
            Method cardGetCornerRadius,
            Method sliderGetBinding,
            Method sliderGetOutlineRadius,
            Field toggleSliderInner,
            Field progressBg) {
        this.hosts = new SystemUiGlassHostController(
                Objects.requireNonNull(core, "core"),
                SystemUiGlassDomain.CONTROL_CENTER,
                "miui-plugin.control-center");
        this.tileMaterial = Objects.requireNonNull(tileMaterial, "tileMaterial");
        this.surfaceMaterial = Objects.requireNonNull(surfaceMaterial, "surfaceMaterial");
        this.tileGetBinding = accessible(tileGetBinding);
        this.tileGetIcon = accessible(tileGetIcon);
        this.tileIconGetIcon = accessible(tileIconGetIcon);
        this.tileIconGetCornerRadius = accessible(tileIconGetCornerRadius);
        this.iconFrame = accessible(iconFrame);
        this.cardGetCornerRadius = accessible(cardGetCornerRadius);
        this.sliderGetBinding = accessible(sliderGetBinding);
        this.sliderGetOutlineRadius = accessible(sliderGetOutlineRadius);
        this.toggleSliderInner = accessible(toggleSliderInner);
        this.progressBg = accessible(progressBg);
    }

    /** QSTileItemView.init(): bind exact iconFrame and icon material identity, deferring Window work. */
    public void bindTile(Object tile) throws ReflectiveOperationException {
        Objects.requireNonNull(tile, "tile");
        Object binding = tileGetBinding.invoke(tile);
        View host = requireView(iconFrame.get(binding), "iconFrame");
        Object iconObject = tileGetIcon.invoke(tile);
        View materialView = requireView(tileIconGetIcon.invoke(iconObject), "tile icon ImageView");
        float radius = positiveRadius(number(tileIconGetCornerRadius.invoke(iconObject)), host);

        TileSlot slot = tiles.get(tile);
        if (slot == null) {
            slot = new TileSlot();
            tiles.put(tile, slot);
        }
        if (slot.iconObject != null && slot.iconObject != iconObject) {
            tileByIcon.remove(slot.iconObject);
        }
        if (slot.host != null && slot.host != host) {
            releaseTileSlot(slot, "tile-host-replaced");
        }

        slot.host = host;
        slot.iconObject = iconObject;
        slot.radius = radius;
        tileByIcon.put(iconObject, slot);
        tileMaterial.observe(host, materialView);
        ensureAttachListener(slot, true);
        activateTile(slot);
    }

    /** QSTileItemIconView.updateIcon(): native blend/LayerDrawable authority may have changed. */
    public void onTileIconUpdated(Object iconObject) throws ReflectiveOperationException {
        TileSlot slot = tileByIcon.get(iconObject);
        if (slot == null || slot.host == null) return;
        View materialView = requireView(tileIconGetIcon.invoke(iconObject), "tile icon ImageView");
        slot.radius = positiveRadius(number(tileIconGetCornerRadius.invoke(iconObject)), slot.host);
        tileMaterial.observe(slot.host, materialView);
        activateTile(slot);
    }

    public void recycleTile(Object tile) {
        TileSlot slot = tiles.remove(tile);
        if (slot == null) return;
        if (slot.iconObject != null) tileByIcon.remove(slot.iconObject);
        releaseTileSlot(slot, "tile-recycle");
    }

    /** QSCardItemView init/updateBackground authority. */
    public void bindCard(Object card) throws ReflectiveOperationException {
        Objects.requireNonNull(card, "card");
        View host = requireView(card, "QSCardItemView");
        float radius = positiveRadius(number(cardGetCornerRadius.invoke(card)), host);

        SurfaceSlot slot = cards.get(card);
        if (slot == null) {
            slot = new SurfaceSlot(SystemUiMaterialHostKind.QS_TILE);
            cards.put(card, slot);
        }
        if (slot.host != null && slot.host != host) releaseSurfaceSlot(slot, "card-host-replaced");
        slot.host = host;
        slot.materialView = host;
        slot.radius = radius;
        surfaceMaterial.observe(host, host);
        ensureAttachListener(slot, false);
        activateSurface(slot);
    }

    public void onCardBackgroundUpdated(Object card) throws ReflectiveOperationException {
        bindCard(card);
    }

    public void recycleCard(Object card) {
        SurfaceSlot slot = cards.remove(card);
        if (slot != null) releaseSurfaceSlot(slot, "card-recycle");
    }

    /** ToggleSliderViewHolder attach/updateBlendBlur authority. */
    public void bindSlider(Object holder) throws ReflectiveOperationException {
        Objects.requireNonNull(holder, "holder");
        Object binding = sliderGetBinding.invoke(holder);
        View host = requireView(toggleSliderInner.get(binding), "toggleSliderInner");
        View materialView = requireView(progressBg.get(binding), "progressBg");
        float radius = positiveRadius(number(sliderGetOutlineRadius.invoke(holder)), host);

        SurfaceSlot slot = sliders.get(holder);
        if (slot == null) {
            slot = new SurfaceSlot(SystemUiMaterialHostKind.BRIGHTNESS_SLIDER);
            sliders.put(holder, slot);
        }
        if (slot.host != null && slot.host != host) releaseSurfaceSlot(slot, "slider-host-replaced");
        slot.host = host;
        slot.materialView = materialView;
        slot.radius = radius;
        surfaceMaterial.observe(host, materialView);
        ensureAttachListener(slot, false);
        activateSurface(slot);
    }

    public void onSliderMaterialUpdated(Object holder) throws ReflectiveOperationException {
        bindSlider(holder);
    }

    public void detachSlider(Object holder) {
        SurfaceSlot slot = sliders.get(holder);
        if (slot == null || slot.host == null) return;
        if (slot.registered) {
            hosts.unregister(slot.host, "slider-detached");
            slot.registered = false;
        }
    }

    public void recycleSlider(Object holder) {
        SurfaceSlot slot = sliders.remove(holder);
        if (slot != null) releaseSurfaceSlot(slot, "slider-recycle");
    }

    @Override
    public void close() {
        for (TileSlot slot : new ArrayList<>(tiles.values())) {
            releaseTileSlot(slot, "plugin-session-close");
        }
        for (SurfaceSlot slot : new ArrayList<>(cards.values())) {
            releaseSurfaceSlot(slot, "plugin-session-close");
        }
        for (SurfaceSlot slot : new ArrayList<>(sliders.values())) {
            releaseSurfaceSlot(slot, "plugin-session-close");
        }
        tiles.clear();
        tileByIcon.clear();
        cards.clear();
        sliders.clear();
        hosts.close();
        tileMaterial.restoreAll();
        surfaceMaterial.restoreAll();
    }

    private void activateTile(TileSlot slot) {
        View host = slot.host;
        if (host == null || !host.isAttachedToWindow()) return;
        GlassHostGeometry geometry = GlassHostGeometry.rounded(slot.radius, 1f, 0);
        if (!slot.registered) {
            hosts.register(host, SystemUiMaterialHostKind.QS_TILE, geometry, tileMaterial);
            slot.registered = true;
            return;
        }
        if (!tileMaterial.refresh(host)) {
            hosts.unregister(host, "tile-native-refresh-failed");
            tileMaterial.forget(host);
            slot.registered = false;
            return;
        }
        hosts.update(host, geometry);
    }

    private void activateSurface(SurfaceSlot slot) {
        View host = slot.host;
        if (host == null || !host.isAttachedToWindow()) return;
        GlassHostGeometry geometry = GlassHostGeometry.rounded(slot.radius, 1f, 0);
        if (!slot.registered) {
            hosts.register(host, slot.kind, geometry, surfaceMaterial);
            slot.registered = true;
            return;
        }
        if (!surfaceMaterial.refresh(host)) {
            hosts.unregister(host, "surface-native-refresh-failed");
            surfaceMaterial.forget(host);
            slot.registered = false;
            return;
        }
        hosts.update(host, geometry);
    }

    private void ensureAttachListener(TileSlot slot, boolean tile) {
        if (slot.listener != null || slot.host == null) return;
        slot.listener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                try {
                    activateTile(slot);
                } catch (Throwable error) {
                    android.util.Log.e("LiquidUI", "[LUI][PluginCC] tile attach failed", error);
                }
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                if (!slot.registered) return;
                hosts.unregister(v, "tile-detached");
                slot.registered = false;
            }
        };
        slot.host.addOnAttachStateChangeListener(slot.listener);
    }

    private void ensureAttachListener(SurfaceSlot slot, boolean ignored) {
        if (slot.listener != null || slot.host == null) return;
        slot.listener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                try {
                    activateSurface(slot);
                } catch (Throwable error) {
                    android.util.Log.e("LiquidUI", "[LUI][PluginCC] surface attach failed", error);
                }
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                if (!slot.registered) return;
                hosts.unregister(v, "surface-detached");
                slot.registered = false;
            }
        };
        slot.host.addOnAttachStateChangeListener(slot.listener);
    }

    private void releaseTileSlot(TileSlot slot, String reason) {
        if (slot.host != null && slot.listener != null) {
            try { slot.host.removeOnAttachStateChangeListener(slot.listener); } catch (Throwable ignored) {}
        }
        if (slot.host != null && slot.registered) hosts.unregister(slot.host, reason);
        if (slot.host != null) tileMaterial.forget(slot.host);
        slot.host = null;
        slot.iconObject = null;
        slot.listener = null;
        slot.registered = false;
    }

    private void releaseSurfaceSlot(SurfaceSlot slot, String reason) {
        if (slot.host != null && slot.listener != null) {
            try { slot.host.removeOnAttachStateChangeListener(slot.listener); } catch (Throwable ignored) {}
        }
        if (slot.host != null && slot.registered) hosts.unregister(slot.host, reason);
        if (slot.host != null) surfaceMaterial.forget(slot.host);
        slot.host = null;
        slot.materialView = null;
        slot.listener = null;
        slot.registered = false;
    }

    private static View requireView(Object value, String name) {
        if (value instanceof View view) return view;
        throw new IllegalStateException(name + " is not android.view.View");
    }

    private static float number(Object value) {
        return value instanceof Number number ? number.floatValue() : 0f;
    }

    private static float positiveRadius(float radius, View host) {
        if (radius > 0f) return radius;
        int resourceId = host.getResources().getIdentifier(
                UNIVERSAL_RADIUS, "dimen", PLUGIN_PACKAGE);
        if (resourceId != 0) {
            try {
                float resolved = host.getResources().getDimension(resourceId);
                if (resolved > 0f) return resolved;
            } catch (RuntimeException ignored) {}
        }
        int min = Math.min(host.getWidth(), host.getHeight());
        return min > 0 ? min * 0.5f : 0f;
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        Objects.requireNonNull(value, "reflection member");
        value.setAccessible(true);
        return value;
    }

    private static final class TileSlot {
        View host;
        Object iconObject;
        float radius;
        View.OnAttachStateChangeListener listener;
        boolean registered;
    }

    private static final class SurfaceSlot {
        final SystemUiMaterialHostKind kind;
        View host;
        View materialView;
        float radius;
        View.OnAttachStateChangeListener listener;
        boolean registered;

        SurfaceSlot(SystemUiMaterialHostKind kind) {
            this.kind = Objects.requireNonNull(kind, "kind");
        }
    }
}
