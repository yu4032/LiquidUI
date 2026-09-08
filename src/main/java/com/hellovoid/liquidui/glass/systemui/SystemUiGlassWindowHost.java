package com.hellovoid.liquidui.glass.systemui;

import android.view.View;

import com.hellovoid.liquidui.glass.core.SystemUiGlassCore;
import com.hellovoid.liquidui.glass.core.WindowGlassSession;

import java.util.Objects;

/** Resolves one component anchor onto an already authorized shared Window session/scene host. */
public final class SystemUiGlassWindowHost {
    public record SessionHost(WindowGlassSession session, View sceneHost) {}

    private SystemUiGlassWindowHost() {}

    public static SessionHost ensureSessionHost(SystemUiGlassCore core, View anchor) {
        Objects.requireNonNull(core, "core");
        Objects.requireNonNull(anchor, "anchor");
        WindowGlassSession session = core.sessionFor(anchor);
        View existing = session.sceneHost();
        if (existing != null) return new SessionHost(session, existing);

        // Generic adapters have no authority to create a renderer or open a backdrop producer on
        // an arbitrary SystemUI/plugin Window. Keep the component completely native until an exact
        // authority owner has already established the shared renderer for this Window.
        throw new IllegalStateException("Window glass renderer has no verified authority");
    }
}
