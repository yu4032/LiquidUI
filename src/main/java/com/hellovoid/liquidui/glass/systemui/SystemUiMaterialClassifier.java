package com.hellovoid.liquidui.glass.systemui;

import com.hellovoid.liquidui.glass.core.GlassMaterialProfile;

import java.util.Objects;

/** Maps proven semantic host kinds onto the five existing core-owned material profiles. */
public final class SystemUiMaterialClassifier {
    private SystemUiMaterialClassifier() {}

    public static GlassMaterialProfile profileFor(SystemUiMaterialHostKind kind) {
        return switch (Objects.requireNonNull(kind, "kind")) {
            case NOTIFICATION_CARD, MEDIA_CARD -> GlassMaterialProfile.CARD;
            case QS_TILE, DEVICE_CONTROL_TILE, KEYGUARD_TILE -> GlassMaterialProfile.TILE;
            case BRIGHTNESS_SLIDER, VOLUME_SLIDER -> GlassMaterialProfile.SLIDER;
            case CONTROL_CENTER_PANEL, VOLUME_PANEL, KEYGUARD_PANEL, SYSTEM_DIALOG ->
                    GlassMaterialProfile.PANEL;
            case STATUS_CAPSULE, TRANSIENT_FLOATING_PANEL -> GlassMaterialProfile.FLOATING;
        };
    }
}
