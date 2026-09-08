package com.hellovoid.liquidui.glass.core;

import android.view.View;
import android.view.ViewGroup;

import java.util.Objects;

/**
 * Shared renderer-establishment boundary for exact target-specific Window authorities.
 *
 * <p>Domain hooks must prove the concrete Window root and safe insertion lane before calling this
 * helper. The helper itself owns no vendor discovery: it only reuses or establishes the one
 * WindowGlassSession renderer through the process-global core.</p>
 */
public final class VerifiedWindowRendererAuthority {
    private VerifiedWindowRendererAuthority() {}

    public static View ensure(
            SystemUiGlassCore core,
            View materialHost,
            ViewGroup parent,
            int insertionIndex,
            boolean vendorPassBlurEnabled) {
        Objects.requireNonNull(core, "core");
        Objects.requireNonNull(materialHost, "materialHost");
        Objects.requireNonNull(parent, "parent");
        if (!materialHost.isAttachedToWindow() || !parent.isAttachedToWindow()) {
            throw new IllegalStateException("verified Window authority is not attached");
        }
        if (materialHost.getRootView() != parent.getRootView()) {
            throw new IllegalStateException("verified Window authority crosses ViewRoots");
        }

        WindowGlassSession session = core.sessionFor(materialHost);
        View existing = session.sceneHost();
        if (existing != null) return existing;

        int safeIndex = Math.max(0, Math.min(insertionIndex, parent.getChildCount()));
        return session.attachRenderer(
                materialHost, parent, safeIndex, vendorPassBlurEnabled);
    }
}
