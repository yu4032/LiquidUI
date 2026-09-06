package com.hellovoid.prismal;

/* JADX INFO: loaded from: classes.dex */
public final class PrismalParams {
    public final float backdropPinch;
    public final float backdropScaleX;
    public final float backdropScaleY;
    public final float blurRadiusPx;
    public final float brightness;
    public final float causticIntensity;
    public final float chromaticAberration;
    public final float dispersionB;
    public final float dispersionR;
    public final float displacementScale;
    public final float edgeRefractionFalloff;
    public final float fresnelReflect;
    public final float glassThicknessPx;
    public final float glowCenterX;
    public final float glowCenterY;
    public final float glowStrength;
    public final float heightTransitionWidthPx;
    public final float highlightWidth;
    public final float ior;
    public final float lensDepthEffect;
    public final float lensRefractionScale;
    public final float lightDirX;
    public final float lightDirY;
    public final float liquidDome;
    public final float normalStrength;
    public final float parallaxScale;
    public final float plainHighlight;
    public final float pressProgress;
    public final float refractionInsetPx;
    public final float rimStrength;
    public final float shadowA;
    public final float shadowB;
    public final float shadowG;
    public final float shadowR;
    public final float shadowSoftness;
    public final float shininess;
    public final boolean showNormals;
    public final float sminSmoothingPx;
    public final float specular;
    public final float tintA;
    public final float tintB;
    public final float tintG;
    public final float tintR;
    public final float transmittance;
    public final float vibrancy;

    public PrismalParams(Builder b) {
        this.ior = b.ior;
        this.glassThicknessPx = b.glassThicknessPx;
        this.normalStrength = b.normalStrength;
        this.displacementScale = b.displacementScale;
        this.heightTransitionWidthPx = b.heightTransitionWidthPx;
        this.sminSmoothingPx = b.sminSmoothingPx;
        this.refractionInsetPx = b.refractionInsetPx;
        this.edgeRefractionFalloff = b.edgeRefractionFalloff;
        this.liquidDome = b.liquidDome;
        this.fresnelReflect = b.fresnelReflect;
        this.lensRefractionScale = b.lensRefractionScale;
        this.lensDepthEffect = b.lensDepthEffect;
        this.chromaticAberration = b.chromaticAberration;
        this.dispersionR = b.dispersionR;
        this.dispersionB = b.dispersionB;
        this.vibrancy = b.vibrancy;
        this.plainHighlight = b.plainHighlight;
        this.brightness = b.brightness;
        this.highlightWidth = b.highlightWidth;
        this.lightDirX = b.lightDirX;
        this.lightDirY = b.lightDirY;
        this.specular = b.specular;
        this.shininess = b.shininess;
        this.rimStrength = b.rimStrength;
        this.causticIntensity = b.causticIntensity;
        this.shadowSoftness = b.shadowSoftness;
        this.transmittance = b.transmittance;
        this.backdropScaleX = b.backdropScaleX;
        this.backdropScaleY = b.backdropScaleY;
        this.parallaxScale = b.parallaxScale;
        this.blurRadiusPx = b.blurRadiusPx;
        this.tintR = b.tintR;
        this.tintG = b.tintG;
        this.tintB = b.tintB;
        this.tintA = b.tintA;
        this.shadowR = b.shadowR;
        this.shadowG = b.shadowG;
        this.shadowB = b.shadowB;
        this.shadowA = b.shadowA;
        this.pressProgress = b.pressProgress;
        this.backdropPinch = b.backdropPinch;
        this.glowCenterX = b.glowCenterX;
        this.glowCenterY = b.glowCenterY;
        this.glowStrength = b.glowStrength;
        this.showNormals = b.showNormals;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        public boolean showNormals;
        public float ior = 1.5f;
        public float glassThicknessPx = 15.0f;
        public float normalStrength = 1.2f;
        public float displacementScale = 1.0f;
        public float heightTransitionWidthPx = 8.0f;
        public float sminSmoothingPx = 1.0f;
        public float refractionInsetPx = 20.0f;
        public float edgeRefractionFalloff = 4.0f;
        public float liquidDome = 0.78f;
        public float fresnelReflect = 1.0f;
        public float lensRefractionScale = 1.0f;
        public float lensDepthEffect = 1.0f;
        public float chromaticAberration = 0.0f;
        public float dispersionR = 1.0f;
        public float dispersionB = 1.0f;
        public float vibrancy = 1.0f;
        public float plainHighlight = 0.0f;
        public float brightness = 1.15f;
        public float highlightWidth = 4.0f;
        public float lightDirX = -0.5f;
        public float lightDirY = -0.8f;
        public float specular = 0.8f;
        public float shininess = 48.0f;
        public float rimStrength = 0.6f;
        public float causticIntensity = 0.15f;
        public float shadowSoftness = 0.2f;
        public float transmittance = 1.0f;
        public float backdropScaleX = 1.0f;
        public float backdropScaleY = 1.0f;
        public float parallaxScale = 1.0f;
        public float blurRadiusPx = 2.5f;
        public float tintR = 1.0f;
        public float tintG = 1.0f;
        public float tintB = 1.0f;
        public float tintA = 0.0f;
        public float shadowR = 0.0f;
        public float shadowG = 0.0f;
        public float shadowB = 0.0f;
        public float shadowA = 0.3f;
        public float pressProgress = 0.0f;
        public float backdropPinch = 1.0f;
        public float glowCenterX = 0.5f;
        public float glowCenterY = 0.5f;
        public float glowStrength = 1.0f;

        public PrismalParams build() {
            return new PrismalParams(this);
        }
    }
}
