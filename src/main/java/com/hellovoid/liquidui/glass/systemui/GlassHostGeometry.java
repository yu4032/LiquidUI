package com.hellovoid.liquidui.glass.systemui;

/** Component-local shape/opacity/z metadata; Window/OES mapping stays core-owned. */
public record GlassHostGeometry(
        float topLeftRadius,
        float topRightRadius,
        float bottomRightRadius,
        float bottomLeftRadius,
        float opacity,
        int zOrder) {
    public GlassHostGeometry {
        topLeftRadius = Math.max(0f, topLeftRadius);
        topRightRadius = Math.max(0f, topRightRadius);
        bottomRightRadius = Math.max(0f, bottomRightRadius);
        bottomLeftRadius = Math.max(0f, bottomLeftRadius);
        opacity = Math.max(0f, Math.min(1f, opacity));
    }

    public static GlassHostGeometry rounded(float radius, float opacity, int zOrder) {
        return new GlassHostGeometry(radius, radius, radius, radius, opacity, zOrder);
    }
}
