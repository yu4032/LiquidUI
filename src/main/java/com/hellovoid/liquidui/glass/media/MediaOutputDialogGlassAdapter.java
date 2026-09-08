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

/** Exact adapter for the built-in media-output SystemUIDialog. */
public final class MediaOutputDialogGlassAdapter implements AutoCloseable {
    // Verified from res/drawable/media_output_dialog_background.xml in systemui-001.
    private static final float MEDIA_OUTPUT_DIALOG_RADIUS_DP = 28f;

    private final SystemUiGlassHostController hosts;
    private final MediaOutputDialogNativeMaterialController material;
    private final Field dialogViewField;
    private final Field deviceListField;
    private final WeakHashMap<Object, View> dialogHosts = new WeakHashMap<>();

    public MediaOutputDialogGlassAdapter(
            SystemUiGlassCore core,
            MediaOutputDialogNativeMaterialController material,
            Field dialogViewField,
            Field deviceListField) {
        this.hosts = new SystemUiGlassHostController(
                Objects.requireNonNull(core, "core"),
                SystemUiGlassDomain.MEDIA,
                "media.output-dialog");
        this.material = Objects.requireNonNull(material, "material");
        this.dialogViewField = accessible(Objects.requireNonNull(dialogViewField, "dialogViewField"));
        this.deviceListField = accessible(Objects.requireNonNull(deviceListField, "deviceListField"));
    }

    /** Register after MediaOutputBaseDialog.onCreate() installed its exact window content view. */
    public void register(Object dialog) throws IllegalAccessException {
        if (dialog == null) return;
        Object rootObject = dialogViewField.get(dialog);
        Object listObject = deviceListField.get(dialog);
        if (!(rootObject instanceof View root) || !(listObject instanceof View deviceList)) {
            throw new IllegalStateException("MediaOutputBaseDialog root/list contract changed");
        }

        View previous = dialogHosts.get(dialog);
        if (previous != null && previous != root) {
            hosts.unregister(previous, "media-output-dialog-root-replaced");
            material.forget(previous);
        }

        material.observe(root, deviceList);
        GlassHostGeometry geometry = GlassHostGeometry.rounded(radiusPx(root), 1f, 0);
        if (previous == root) {
            hosts.update(root, geometry);
            if (!material.refresh(root)) {
                hosts.unregister(root, "media-output-dialog-native-refresh-failed");
                material.forget(root);
                dialogHosts.remove(dialog);
            }
            return;
        }

        hosts.register(root, SystemUiMaterialHostKind.SYSTEM_DIALOG, geometry, material);
        dialogHosts.put(dialog, root);
    }

    /** Re-apply suppression after dynamic album-derived dialog colors update native backgrounds. */
    public void onBackgroundUpdated(Object dialog) {
        if (dialog == null) return;
        View root = dialogHosts.get(dialog);
        if (root == null) return;
        if (!material.refresh(root)) {
            hosts.unregister(root, "media-output-dialog-background-refresh-failed");
            material.forget(root);
            dialogHosts.remove(dialog);
            return;
        }
        hosts.update(root, GlassHostGeometry.rounded(radiusPx(root), 1f, 0));
    }

    /** SystemUIDialog.onStop() invokes MediaOutputBaseDialog.stop() on every dismissal path. */
    public void stop(Object dialog) {
        if (dialog == null) return;
        View root = dialogHosts.remove(dialog);
        if (root == null) return;
        hosts.unregister(root, "media-output-dialog-stop");
        material.forget(root);
    }

    @Override
    public void close() {
        hosts.close();
        material.restoreAll();
        for (Map.Entry<Object, View> entry : new ArrayList<>(dialogHosts.entrySet())) {
            material.forget(entry.getValue());
        }
        dialogHosts.clear();
    }

    private static float radiusPx(View root) {
        float density = Math.max(0.1f, root.getResources().getDisplayMetrics().density);
        return MEDIA_OUTPUT_DIALOG_RADIUS_DP * density;
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }
}
