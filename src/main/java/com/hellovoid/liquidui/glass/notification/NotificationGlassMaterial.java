package com.hellovoid.liquidui.glass.notification;

import com.hellovoid.prismal.PrismalParams;

/** Temporary near-identity optical profile used only by the three-way OES mapping probe. */
final class NotificationGlassMaterial {
    static final boolean MAPPING_PROBE_IDENTITY = true;

    private NotificationGlassMaterial() {}

    static PrismalParams defaults(float density) {
        float d = Math.max(0.1f, density);
        PrismalParams.Builder b = PrismalParams.builder();
        b.ior = 1.0f;
        b.glassThicknessPx = 0f;
        b.normalStrength = 0f;
        b.displacementScale = 0f;
        b.heightTransitionWidthPx = 1f * d;
        b.sminSmoothingPx = 1f;
        b.refractionInsetPx = 0f;
        b.edgeRefractionFalloff = 1f;
        b.liquidDome = 0f;
        b.fresnelReflect = 0f;
        b.lensRefractionScale = 0f;
        b.lensDepthEffect = 0f;
        b.chromaticAberration = 0f;
        b.dispersionR = 0f;
        b.dispersionB = 0f;
        b.vibrancy = 1.0f;
        b.plainHighlight = 0f;
        b.brightness = 1.0f;
        b.highlightWidth = 0f;
        b.lightDirX = -0.5f;
        b.lightDirY = -0.8f;
        b.specular = 0f;
        b.shininess = 1f;
        b.rimStrength = 0f;
        b.causticIntensity = 0f;
        b.shadowSoftness = 0f;
        b.transmittance = 1f;
        b.backdropScaleX = 1f;
        b.backdropScaleY = 1f;
        b.parallaxScale = 0f;
        b.blurRadiusPx = 0f;
        b.tintR = 0f;
        b.tintG = 0f;
        b.tintB = 0f;
        b.tintA = 0f;
        b.shadowR = 0f;
        b.shadowG = 0f;
        b.shadowB = 0f;
        b.shadowA = 0f;
        b.showNormals = false;
        return b.build();
    }
}
