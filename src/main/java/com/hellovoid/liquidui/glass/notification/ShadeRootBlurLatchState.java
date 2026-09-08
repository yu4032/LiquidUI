package com.hellovoid.liquidui.glass.notification;

/**
 * Pure state for the Shade root backdrop latch.
 *
 * <p>HyperOS enables its combined root blur only after the vendor combined ratio becomes positive.
 * LiquidUI glass nodes can become visible before that threshold. Treat visible Window glass demand
 * as an earlier authority signal so the root backdrop can be prewarmed without restoring per-frame
 * blur-radius animation.</p>
 */
final class ShadeRootBlurLatchState {
    private float vendorRatio;
    private boolean glassDemand;

    void observeVendorRatio(float value) {
        vendorRatio = Math.max(0f, value);
    }

    void observeGlassDemand(boolean active) {
        glassDemand = active;
    }

    boolean effectiveActive() {
        return vendorRatio > 0f || glassDemand;
    }

    float effectiveRatio() {
        return effectiveActive() ? 1f : 0f;
    }
}
