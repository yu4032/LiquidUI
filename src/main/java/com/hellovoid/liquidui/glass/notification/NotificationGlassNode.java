package com.hellovoid.liquidui.glass.notification;

/** Immutable notification-row glass geometry in shared-host top-left coordinates. */
final class NotificationGlassNode {
    final float left;
    final float top;
    final float width;
    final float height;
    final float topLeftRadius;
    final float topRightRadius;
    final float bottomRightRadius;
    final float bottomLeftRadius;
    final float opacity;

    NotificationGlassNode(
            float left, float top, float width, float height,
            float topLeftRadius, float topRightRadius,
            float bottomRightRadius, float bottomLeftRadius,
            float opacity) {
        this.left = left;
        this.top = top;
        this.width = Math.max(0f, width);
        this.height = Math.max(0f, height);
        this.topLeftRadius = Math.max(0f, topLeftRadius);
        this.topRightRadius = Math.max(0f, topRightRadius);
        this.bottomRightRadius = Math.max(0f, bottomRightRadius);
        this.bottomLeftRadius = Math.max(0f, bottomLeftRadius);
        this.opacity = Math.max(0f, Math.min(1f, opacity));
    }

    @Override public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof NotificationGlassNode other)) return false;
        return Float.compare(left, other.left) == 0
                && Float.compare(top, other.top) == 0
                && Float.compare(width, other.width) == 0
                && Float.compare(height, other.height) == 0
                && Float.compare(topLeftRadius, other.topLeftRadius) == 0
                && Float.compare(topRightRadius, other.topRightRadius) == 0
                && Float.compare(bottomRightRadius, other.bottomRightRadius) == 0
                && Float.compare(bottomLeftRadius, other.bottomLeftRadius) == 0
                && Float.compare(opacity, other.opacity) == 0;
    }

    @Override public int hashCode() {
        int result = Float.floatToIntBits(left);
        result = 31 * result + Float.floatToIntBits(top);
        result = 31 * result + Float.floatToIntBits(width);
        result = 31 * result + Float.floatToIntBits(height);
        result = 31 * result + Float.floatToIntBits(topLeftRadius);
        result = 31 * result + Float.floatToIntBits(topRightRadius);
        result = 31 * result + Float.floatToIntBits(bottomRightRadius);
        result = 31 * result + Float.floatToIntBits(bottomLeftRadius);
        result = 31 * result + Float.floatToIntBits(opacity);
        return result;
    }

    boolean drawable() {
        return width > 0.5f && height > 0.5f && opacity > 0.001f;
    }
}
