package com.hellovoid.liquidui.glass.notification;

import com.hellovoid.prismal.PrismalParams;

/** Exact LiquidDock/Prismal v1.0.6 default material used for the first notification PassBlur test. */
final class NotificationGlassMaterial {
    private NotificationGlassMaterial() {}

    static PrismalParams defaults(float density) {
        float d = Math.max(0.1f, density);
        PrismalParams.Builder b = PrismalParams.builder();
        b.ior = 1.55f;
        b.glassThicknessPx = 18f * d;
        b.normalStrength = 1.15f;
        b.displacementScale = 1.15f;
        b.heightTransitionWidthPx = 19f * d;
        b.sminSmoothingPx = 1.8f;
        b.refractionInsetPx = 20f;
        b.edgeRefractionFalloff = 4f;
        b.liquidDome = 1.30f;
        b.fresnelReflect = 1.98f;
        b.lensRefractionScale = 1.30f;
        b.lensDepthEffect = 1f;
        b.chromaticAberration = 26f;
        b.dispersionR = 1f;
        b.dispersionB = 1f;
        b.vibrancy = 1.28f;
        b.plainHighlight = 0.08f;
        b.brightness = 1.08f;
        b.highlightWidth = 1f;
        b.lightDirX = -0.5f;
        b.lightDirY = -0.8f;
        b.specular = 1.52f;
        b.shininess = 88f;
        b.rimStrength = 1.22f;
        b.causticIntensity = 0.28f;
        b.shadowSoftness = 10f;
        b.transmittance = 1f;
        b.backdropScaleX = 1f;
        b.backdropScaleY = 1f;
        b.parallaxScale = 1f;
        b.blurRadiusPx = 2f;
        b.tintR = 0f;
        b.tintG = 0f;
        b.tintB = 1f;
        b.tintA = 35f / 255f;
        b.shadowR = 1f;
        b.shadowG = 1f;
        b.shadowB = 1f;
        b.shadowA = 35f / 255f;
        b.showNormals = false;
        return b.build();
    }
}
