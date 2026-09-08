package com.hellovoid.liquidui.glass.media;

import android.view.View;

import com.hellovoid.liquidui.glass.core.SystemUiGlassCore;
import com.hellovoid.liquidui.glass.systemui.GlassHostGeometry;
import com.hellovoid.liquidui.glass.systemui.SystemUiGlassDomain;
import com.hellovoid.liquidui.glass.systemui.SystemUiGlassHostController;
import com.hellovoid.liquidui.glass.systemui.SystemUiMaterialHostKind;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Exact adapter for Miui notification media player cards. */
public final class MediaGlassAdapter implements AutoCloseable {
    private static final String MEDIA_RADIUS_DIMEN = "notification_item_bg_radius";

    private final SystemUiGlassHostController hosts;
    private final MediaNativeMaterialController material;
    private final Field playerField;
    private final Field mediaBgField;
    private final WeakHashMap<Object, View> controllerHosts = new WeakHashMap<>();

    public MediaGlassAdapter(
            SystemUiGlassCore core,
            MediaNativeMaterialController material,
            Field playerField,
            Field mediaBgField) {
        this.hosts = new SystemUiGlassHostController(
                Objects.requireNonNull(core, "core"),
                SystemUiGlassDomain.MEDIA,
                "media.notification-card");
        this.material = Objects.requireNonNull(material, "material");
        this.playerField = accessible(Objects.requireNonNull(playerField, "playerField"));
        this.mediaBgField = accessible(Objects.requireNonNull(mediaBgField, "mediaBgField"));
    }

    /** Called after MiuiMediaViewControllerImpl.attach(holder) completed. */
    public void attach(Object controller, Object holder) throws IllegalAccessException {
        if (controller == null || holder == null) return;
        Object playerObject = playerField.get(holder);
        Object materialObject = mediaBgField.get(holder);
        if (!(playerObject instanceof View player) || !(materialObject instanceof View mediaBg)) {
            throw new IllegalStateException("MiuiMediaViewHolder player/mediaBg contract changed");
        }

        View previous = controllerHosts.get(controller);
        if (previous != null && previous != player) {
            hosts.unregister(previous, "media-player-replaced");
            material.forget(previous);
        }

        material.observe(player, mediaBg);
        GlassHostGeometry geometry = GlassHostGeometry.rounded(mediaRadiusPx(player), 1f, 0);
        if (previous == player) {
            hosts.update(player, geometry);
            if (!material.refresh(player)) {
                hosts.unregister(player, "media-native-refresh-failed");
                material.forget(player);
                controllerHosts.remove(controller);
            }
            return;
        }

        hosts.register(player, SystemUiMaterialHostKind.MEDIA_CARD, geometry, material);
        controllerHosts.put(controller, player);
    }

    /** Called after SystemUI's authoritative updateMediaBackground() rebuilt native material. */
    public void onBackgroundUpdated(Object controller) {
        if (controller == null) return;
        View player = controllerHosts.get(controller);
        if (player == null) return;
        if (!material.refresh(player)) {
            hosts.unregister(player, "media-background-refresh-failed");
            material.forget(player);
            controllerHosts.remove(controller);
            return;
        }
        hosts.update(player, GlassHostGeometry.rounded(mediaRadiusPx(player), 1f, 0));
    }

    /** Called after MiuiMediaViewControllerImpl.detach() completed. */
    public void detach(Object controller) {
        if (controller == null) return;
        View player = controllerHosts.remove(controller);
        if (player == null) return;
        hosts.unregister(player, "media-controller-detach");
        material.forget(player);
    }

    @Override
    public void close() {
        hosts.close();
        material.restoreAll();
        for (Map.Entry<Object, View> entry : new ArrayList<>(controllerHosts.entrySet())) {
            material.forget(entry.getValue());
        }
        controllerHosts.clear();
    }

    private static float mediaRadiusPx(View player) {
        int id = player.getResources().getIdentifier(
                MEDIA_RADIUS_DIMEN, "dimen", "com.android.systemui");
        if (id == 0) {
            throw new IllegalStateException("missing SystemUI dimen " + MEDIA_RADIUS_DIMEN);
        }
        return Math.max(0f, player.getResources().getDimension(id));
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }
}
