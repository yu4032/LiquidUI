package com.hellovoid.prismal;

/* JADX INFO: loaded from: classes.dex */
public final class PrismalGeometry {
    public final float bottomLeftRadius;
    public final float bottomRightRadius;
    public final float centerX;
    public final float centerY;
    public final int framebufferHeight;
    public final int framebufferWidth;
    public final float glassHeight;
    public final float glassWidth;
    public final float topLeftRadius;
    public final float topRightRadius;

    public PrismalGeometry(int framebufferWidth, int framebufferHeight, float centerX, float centerY, float glassWidth, float glassHeight, float cornerRadius) {
        this(framebufferWidth, framebufferHeight, centerX, centerY, glassWidth, glassHeight, cornerRadius, cornerRadius, cornerRadius, cornerRadius);
    }

    public PrismalGeometry(int framebufferWidth, int framebufferHeight, float centerX, float centerY, float glassWidth, float glassHeight, float topLeftRadius, float topRightRadius, float bottomRightRadius, float bottomLeftRadius) {
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            throw new IllegalArgumentException("framebuffer must be positive");
        }
        if (glassWidth <= 0.0f || glassHeight <= 0.0f) {
            throw new IllegalArgumentException("glass size must be positive");
        }
        this.framebufferWidth = framebufferWidth;
        this.framebufferHeight = framebufferHeight;
        this.centerX = centerX;
        this.centerY = centerY;
        this.glassWidth = glassWidth;
        this.glassHeight = glassHeight;
        this.topLeftRadius = Math.max(0.0f, topLeftRadius);
        this.topRightRadius = Math.max(0.0f, topRightRadius);
        this.bottomRightRadius = Math.max(0.0f, bottomRightRadius);
        this.bottomLeftRadius = Math.max(0.0f, bottomLeftRadius);
    }
}
