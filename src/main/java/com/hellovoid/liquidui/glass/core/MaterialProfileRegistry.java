package com.hellovoid.liquidui.glass.core;

import com.hellovoid.prismal.PrismalParams;

import java.util.EnumMap;
import java.util.Map;

/** Core-owned mapping from semantic component material to numeric Prismal optics. */
final class MaterialProfileRegistry {
    private static final long PROFILE_VERSION = 1L;

    private final Map<GlassMaterialProfile, PrismalParams> profiles =
            new EnumMap<>(GlassMaterialProfile.class);

    MaterialProfileRegistry(float density) {
        PrismalParams phaseZero = buildPhaseZeroProfile(density);
        // Phase 0 deliberately preserves the already validated optical profile for every semantic
        // material. Later tuning may diverge here without giving component adapters numeric control.
        for (GlassMaterialProfile profile : GlassMaterialProfile.values()) {
            profiles.put(profile, phaseZero);
        }
    }

    PrismalParams paramsFor(GlassMaterialProfile profile) {
        PrismalParams params = profiles.get(profile);
        return params != null ? params : profiles.get(GlassMaterialProfile.CARD);
    }

    long profileVersion() {
        return PROFILE_VERSION;
    }

    private static PrismalParams buildPhaseZeroProfile(float density) {
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
