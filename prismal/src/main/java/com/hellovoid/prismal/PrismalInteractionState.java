package com.hellovoid.prismal;

/* JADX INFO: loaded from: classes.dex */
public final class PrismalInteractionState {
    public static final PrismalInteractionState IDLE = new PrismalInteractionState(0.0f, 0.5f, 0.5f);
    public final float glowCenterX;
    public final float glowCenterY;
    public final float pressProgress;

    public PrismalInteractionState(float pressProgress, float glowCenterX, float glowCenterY) {
        this.pressProgress = clamp01(pressProgress);
        this.glowCenterX = clamp01(glowCenterX);
        this.glowCenterY = clamp01(glowCenterY);
    }

    public static float clamp01(float value) {
        if (Float.isFinite(value)) {
            return Math.max(0.0f, Math.min(1.0f, value));
        }
        return 0.5f;
    }
}
