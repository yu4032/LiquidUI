package com.hellovoid.liquidui.glass.notification;

import com.hellovoid.prismal.PrismalParams;

/** Exaggerated refraction-only profile for the first shared notification GPU validation build. */
final class NotificationGlassMaterial {
    private NotificationGlassMaterial() {}

    static PrismalParams defaults(float density) {
        float d = Math.max(0.1f, density);
        PrismalParams.Builder b = PrismalParams.builder();
        b.ior = 1.55f;
        b.glassThicknessPx = 22f * d;
        b.normalStrength = 1.35f;
        b.displacementScale = 1.70f;
        b.heightTransitionWidthPx = 20f * d;
        b.sminSmoothingPx = 1.8f;
        b.refractionInsetPx = 20f;
        b.edgeRefractionFalloff = 4f;
        b.liquidDome = 1.35f;
        b.fresnelReflect = 1.0f;
        b.lensRefractionScale = 2.20f;
        b.lensDepthEffect = 1.0f;
        b.chromaticAberration = 42f;
        b.dispersionR = 1f;
        b.dispersionB = 1f;
        b.vibrancy = 1.30f;
        b.plainHighlight = 0f;
        b.brightness = 1.0f;
        b.highlightWidth = 0f;
        b.lightDirX = -0.5f;
        b.lightDirY = -0.8f;
        b.specular = 0f;
        b.shininess = 88f;
        b.rimStrength = 0f;
        b.causticIntensity = 0f;
        b.shadowSoftness = 0f;
        b.transmittance = 1f;
        b.backdropScaleX = 1f;
        b.backdropScaleY = 1f;
        b.parallaxScale = 1f;
        b.blurRadiusPx = 2f;
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
