package com.hellovoid.liquidui.glass.systemui;

import android.view.View;
import android.view.ViewGroup;

import com.hellovoid.liquidui.glass.core.SystemUiGlassCore;
import com.hellovoid.liquidui.glass.core.WindowGlassSession;

import java.util.Objects;

/** Resolves one component anchor onto the already shared Window session/scene host. */
public final class SystemUiGlassWindowHost {
    public record SessionHost(WindowGlassSession session, View sceneHost) {}

    private SystemUiGlassWindowHost() {}

    public static SessionHost ensureSessionHost(SystemUiGlassCore core, View anchor) {
        Objects.requireNonNull(core, "core");
        Objects.requireNonNull(anchor, "anchor");
        WindowGlassSession session = core.sessionFor(anchor);
        View existing = session.sceneHost();
        if (existing != null) return new SessionHost(session, existing);

        View root = anchor.getRootView();
        if (!(root instanceof ViewGroup rootGroup)) {
            throw new IllegalStateException("SystemUI Window root is not a ViewGroup");
        }
        View sceneHost = session.attachRenderer(anchor, rootGroup, 0, true);
        return new SessionHost(session, sceneHost);
    }
}
