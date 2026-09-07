package com.hellovoid.liquidui.glass.core;

import com.hellovoid.liquidui.config.GlassHighlight;
import com.hellovoid.liquidui.config.GlassParameter;
import com.hellovoid.liquidui.config.GlassStyleConfig;
import com.hellovoid.prismal.PrismalHighlightProfile;
import com.hellovoid.prismal.PrismalParams;

/** Pure mapping from normalized LiquidUI style state into portable Prismal state. */
final class GlassPrismalAdapter {
    private GlassPrismalAdapter() {}

    static PrismalParams toPrismal(GlassStyleConfig.ResolvedStyle style, float density) {
        if (style == null) style = GlassStyleConfig.defaults().global();
        float d = Math.max(0.1f, density);
        float normal = style.value(GlassParameter.NORMAL_STRENGTH);
        float manualDepth = style.value(GlassParameter.LENS_DEPTH);

        PrismalParams.Builder b = PrismalParams.builder();
        b.ior = style.value(GlassParameter.IOR);
        b.glassThicknessPx = Math.max(0f, style.value(GlassParameter.THICKNESS) * d);
        b.normalStrength = normal;
        b.displacementScale = style.value(GlassParameter.DISPLACEMENT_SCALE);
        b.heightTransitionWidthPx = Math.max(
                1f, style.value(GlassParameter.HEIGHT_TRANSITION_WIDTH) * d);
        b.sminSmoothingPx = Math.max(0f, style.value(GlassParameter.SMIN_SMOOTHING));
        b.refractionInsetPx = Math.max(0f, style.value(GlassParameter.REFRACTION_INSET));
        b.edgeRefractionFalloff = Math.max(
                0.05f, style.value(GlassParameter.EDGE_REFRACTION_FALLOFF));
        b.liquidDome = style.value(GlassParameter.DOME);
        b.fresnelReflect = style.value(GlassParameter.FRESNEL_REFLECT);
        b.lensRefractionScale = Math.max(
                0.25f, style.value(GlassParameter.LENS_REFRACTION));
        b.lensDepthEffect = manualDepth > 0f
                ? clamp(manualDepth, 0f, 1f)
                : clamp(normal * 0.9f, 0f, 1f);
        b.chromaticAberration = Math.max(0f, style.value(GlassParameter.CHROMATIC));
        b.dispersionR = style.value(GlassParameter.DISPERSION_R);
        b.dispersionB = style.value(GlassParameter.DISPERSION_B);
        b.vibrancy = style.value(GlassParameter.VIBRANCY);
        b.plainHighlight = style.value(GlassParameter.PLAIN_HIGHLIGHT);
        b.brightness = style.value(GlassParameter.BRIGHTNESS);
        b.highlightWidth = style.value(GlassParameter.HIGHLIGHT_WIDTH);
        b.lightDirX = style.value(GlassParameter.LIGHT_DIR_X);
        b.lightDirY = style.value(GlassParameter.LIGHT_DIR_Y);
        b.specular = style.value(GlassParameter.SPECULAR_STRENGTH);
        b.shininess = style.value(GlassParameter.SPECULAR_SHARPNESS);
        b.rimStrength = style.value(GlassParameter.RIM_LIGHT);
        b.causticIntensity = style.value(GlassParameter.CAUSTICS);
        b.shadowSoftness = style.value(GlassParameter.SHADOW_SOFTNESS);
        b.transmittance = style.value(GlassParameter.TRANSMITTANCE);
        b.backdropScaleX = style.value(GlassParameter.BACKDROP_SCALE_X);
        b.backdropScaleY = style.value(GlassParameter.BACKDROP_SCALE_Y);
        b.parallaxScale = style.value(GlassParameter.PARALLAX_SCALE);
        b.blurRadiusPx = Math.max(0f, style.value(GlassParameter.BLUR));
        b.tintR = style.value(GlassParameter.TINT_R);
        b.tintG = style.value(GlassParameter.TINT_G);
        b.tintB = style.value(GlassParameter.TINT_B);
        b.tintA = style.value(GlassParameter.TINT_ALPHA);
        b.shadowR = style.value(GlassParameter.SHADOW_R);
        b.shadowG = style.value(GlassParameter.SHADOW_G);
        b.shadowB = style.value(GlassParameter.SHADOW_B);
        b.shadowA = style.value(GlassParameter.SHADOW_ALPHA);
        b.showNormals = style.value(GlassParameter.SHOW_NORMALS) >= 0.5f;
        return b.build();
    }

    static PrismalHighlightProfile toHighlights(GlassStyleConfig.ResolvedStyle style) {
        if (style == null) style = GlassStyleConfig.defaults().global();
        return new PrismalHighlightProfile(
                style.highlight(GlassHighlight.SKY_HAZE),
                style.highlight(GlassHighlight.SPECULAR),
                style.highlight(GlassHighlight.RIM_LIT),
                style.highlight(GlassHighlight.OPPOSITE_RIM),
                style.highlight(GlassHighlight.CORNER_RIM),
                style.highlight(GlassHighlight.FACE_SHEEN),
                style.highlight(GlassHighlight.PLAIN_HIGHLIGHT),
                style.highlight(GlassHighlight.CAUSTICS),
                style.highlight(GlassHighlight.PRESS_GLOW));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
