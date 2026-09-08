package com.hellovoid.liquidui.glass.core;

/**
 * Tracks which blur radius is currently materialized in PrismalRenderer.blurTextureV.
 *
 * PrismalRenderer.prepareBackdrop() already rebuilds the blur texture for the base CARD profile.
 * Geometry-only scene frames must reuse that texture instead of running the Gaussian passes again.
 */
final class PrismalBlurReuseState {
    private boolean prepared;
    private float activeBlurRadius;

    void onBackdropPrepared(float blurRadius) {
        activeBlurRadius = blurRadius;
        prepared = true;
    }

    boolean needsRebuild(float blurRadius) {
        return !prepared || Float.compare(activeBlurRadius, blurRadius) != 0;
    }

    void markPrepared(float blurRadius) {
        activeBlurRadius = blurRadius;
        prepared = true;
    }
}
