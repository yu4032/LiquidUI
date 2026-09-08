package com.hellovoid.liquidui.glass.statusbar;

import android.view.View;
import android.view.ViewGroup;

import com.hellovoid.liquidui.glass.core.SystemUiGlassCore;
import com.hellovoid.liquidui.glass.core.VerifiedWindowRendererAuthority;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Exact systemui-001 authority for establishing the one shared Miui status-bar Window host. */
public final class StatusBarWindowGlassAuthority implements AutoCloseable {
    private static final String STATUS_BAR_ROOT =
            "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView";
    private static final WeakHashMap<View, StatusBarWindowGlassAuthority> ACTIVE =
            new WeakHashMap<>();

    public static StatusBarWindowGlassAuthority ensure(SystemUiGlassCore core, View candidate) {
        if (core == null || candidate == null) return null;
        if (!STATUS_BAR_ROOT.equals(candidate.getClass().getName())) return null;
        if (!(candidate instanceof ViewGroup root) || !candidate.isAttachedToWindow()) return null;

        synchronized (ACTIVE) {
            StatusBarWindowGlassAuthority current = ACTIVE.get(candidate);
            if (current != null && !current.closed) return current;
            StatusBarWindowGlassAuthority created = new StatusBarWindowGlassAuthority(core, root);
            ACTIVE.put(candidate, created);
            return created;
        }
    }

    private final WeakReference<View> rootRef;
    private final View.OnAttachStateChangeListener attachListener;
    private boolean closed;

    private StatusBarWindowGlassAuthority(SystemUiGlassCore core, ViewGroup root) {
        this.rootRef = new WeakReference<>(root);
        View sceneHost = VerifiedWindowRendererAuthority.ensure(core, root, root, 0, true);
        if (sceneHost == null) {
            throw new IllegalStateException("verified status-bar Window host unavailable");
        }
        this.attachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}
            @Override public void onViewDetachedFromWindow(View v) { close(); }
        };
        root.addOnAttachStateChangeListener(attachListener);
        android.util.Log.i("LiquidUI",
                "[LUI][StatusGlass] status-bar Window authority ready root="
                        + root.getClass().getName());
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        View root = rootRef.get();
        if (root != null) {
            try { root.removeOnAttachStateChangeListener(attachListener); } catch (Throwable ignored) {}
            synchronized (ACTIVE) { ACTIVE.remove(root, this); }
        }
    }
}
