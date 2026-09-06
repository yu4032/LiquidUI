package com.hellovoid.prismal;

/* JADX INFO: loaded from: classes.dex */
public abstract class PrismalSampling {
    public static int requiredGuardPx(PrismalParams p0, float glassWidth, float glassHeight, boolean horizontal) {
        PrismalParams p = p0 != null ? p0 : PrismalParams.builder().build();
        float width = Math.max(1.0f, glassWidth);
        float height = Math.max(1.0f, glassHeight);
        float axis = horizontal ? width : height;
        float halfMin = Math.min(width, height) * 0.5f;
        float pxNorm = clamp(halfMin / 108.0f, 0.36f, 1.0f) + (smoothstep(88.0f, 220.0f, halfMin) * 0.45f);
        float sampleScale = Math.max(0.01f, Math.abs(horizontal ? p.backdropScaleX : p.backdropScaleY));
        float scaleExpansion = Math.max(0.0f, (1.0f / sampleScale) - 1.0f) * axis * 0.5f;
        float dome = clamp(p.liquidDome, 0.0f, 2.0f);
        float refractionHeight = Math.max(p.heightTransitionWidthPx * ((0.55f * dome) + 1.0f), 1.0f);
        float lensPx = clamp(refractionHeight * 2.0f * Math.abs(p.displacementScale) * Math.abs(p.lensRefractionScale), 4.0f, Math.max(4.0f, Math.min(width, height) * 0.85f));
        float lens = 1.45f * lensPx * 1.12f;
        float parallax = Math.abs(p.displacementScale) * 1.508f * Math.abs(p.parallaxScale) * 1.12f;
        float fAbs = Math.abs(p.glassThicknessPx) * 0.85f;
        float refractionHeight2 = p.displacementScale;
        float snell = fAbs * Math.abs(refractionHeight2) * 1.18f * pxNorm;
        float bulge = ((0.01f * dome) + 0.014f) * axis * pxNorm;
        float dispersion = Math.max(Math.abs(p.dispersionR), Math.abs(p.dispersionB));
        float chromatic = Math.abs(p.chromaticAberration) * 0.0018f * dispersion * pxNorm * axis;
        float reflection = 56.0f * pxNorm;
        return Math.max(0, (int) Math.ceil(scaleExpansion + lens + parallax + snell + bulge + chromatic + reflection + 30.0f + 2.0f));
    }

    public static float smoothstep(float edge0, float edge1, float x) {
        if (edge0 == edge1) {
            return x < edge0 ? 0.0f : 1.0f;
        }
        float t = clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - (2.0f * t));
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
