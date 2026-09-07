package com.hellovoid.liquidui.glass.core;

import java.util.Objects;

/** Immutable component-neutral geometry published to one Window glass scene. */
public record GlassNode(
        String id,
        long lifecycleGeneration,
        float left,
        float top,
        float width,
        float height,
        float topLeftRadius,
        float topRightRadius,
        float bottomRightRadius,
        float bottomLeftRadius,
        float opacity,
        int zOrder,
        GlassMaterialProfile materialProfile) {
    public GlassNode {
        id = Objects.requireNonNull(id, "id");
        materialProfile = Objects.requireNonNull(materialProfile, "materialProfile");
        width = Math.max(0f, width);
        height = Math.max(0f, height);
        topLeftRadius = Math.max(0f, topLeftRadius);
        topRightRadius = Math.max(0f, topRightRadius);
        bottomRightRadius = Math.max(0f, bottomRightRadius);
        bottomLeftRadius = Math.max(0f, bottomLeftRadius);
        opacity = Math.max(0f, Math.min(1f, opacity));
    }

    public boolean drawable() {
        return width > 0.5f && height > 0.5f && opacity > 0.001f;
    }
}
