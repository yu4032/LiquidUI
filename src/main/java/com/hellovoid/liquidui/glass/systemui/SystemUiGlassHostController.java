package com.hellovoid.liquidui.glass.systemui;

import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import com.hellovoid.liquidui.glass.core.GlassNode;
import com.hellovoid.liquidui.glass.core.SystemUiGlassCore;
import com.hellovoid.liquidui.glass.core.WindowGlassSession;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Shared adapter bridge for bounded SystemUI material hosts.
 *
 * <p>This class owns no rendering or capture resources. It translates exact domain-discovered
 * Views into generic GlassNodes in the existing WindowGlassSession, and gates native material
 * suppression on exact presentation authorization.</p>
 */
public final class SystemUiGlassHostController implements AutoCloseable {
    private final SystemUiGlassCore core;
    private final SystemUiGlassDomain domain;
    private final String adapterId;
    private final WeakHashMap<View, HostSlot> slots = new WeakHashMap<>();
    private final IdentityHashMap<WindowGlassSession, WindowSlot> windows = new IdentityHashMap<>();

    private long nextNodeSequence;
    private long nextWindowSequence;
    private boolean closed;

    public SystemUiGlassHostController(
            SystemUiGlassCore core,
            SystemUiGlassDomain domain,
            String adapterId) {
        this.core = Objects.requireNonNull(core, "core");
        this.domain = Objects.requireNonNull(domain, "domain");
        String id = Objects.requireNonNull(adapterId, "adapterId");
        if (id.isBlank()) throw new IllegalArgumentException("adapterId blank");
        this.adapterId = id;
    }

    public long register(
            View host,
            SystemUiMaterialHostKind kind,
            GlassHostGeometry geometry,
            NativeMaterialController<View> material) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(material, "material");
        if (closed) throw new IllegalStateException("controller closed");

        HostSlot slot = slots.get(host);
        if (slot != null && slot.active) unregisterInternal(host, slot, "re-register");
        if (slot == null || slot.state.failed()) {
            slot = new HostSlot();
            slots.put(host, slot);
        }

        SystemUiGlassWindowHost.SessionHost target =
                SystemUiGlassWindowHost.ensureSessionHost(core, host);
        WindowSlot window = windows.get(target.session());
        if (window == null || window.failed) {
            window = newWindow(target.session(), target.sceneHost());
            windows.put(target.session(), window);
        }

        String nodeId = domain.name().toLowerCase()
                + ":" + adapterId + ":" + (++nextNodeSequence);
        long generation = slot.state.register(nodeId);
        slot.nodeId = nodeId;
        slot.generation = generation;
        slot.kind = kind;
        slot.geometry = geometry;
        slot.material = material;
        slot.window = window;
        slot.active = true;
        slot.broken = false;

        window.hosts.put(host, slot);
        host.addOnAttachStateChangeListener(slot.attachListener);
        host.addOnLayoutChangeListener(slot.layoutListener);
        refreshWindow(window);
        return generation;
    }

    public void update(View host, GlassHostGeometry geometry) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(geometry, "geometry");
        if (closed) return;
        HostSlot slot = slots.get(host);
        if (slot == null || !slot.active || slot.broken) return;
        slot.geometry = geometry;
        refreshWindow(slot.window);
    }

    public void unregister(View host, String reason) {
        if (host == null || closed) return;
        HostSlot slot = slots.get(host);
        if (slot == null || !slot.active) return;
        unregisterInternal(host, slot, reason == null ? "unregister" : reason);
    }

    /** Refresh all current Windows. High-frequency identical geometry is coalesced by node equality. */
    public void refresh() {
        if (closed) return;
        for (WindowSlot window : List.copyOf(windows.values())) refreshWindow(window);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (Map.Entry<WindowGlassSession, WindowSlot> entry :
                List.copyOf(windows.entrySet())) {
            WindowSlot window = entry.getValue();
            for (Map.Entry<View, HostSlot> hostEntry :
                    List.copyOf(window.hosts.entrySet())) {
                releaseHost(hostEntry.getKey(), hostEntry.getValue(), "controller-close");
            }
            disposeWindow(window, true);
        }
        windows.clear();
        slots.clear();
    }

    private WindowSlot newWindow(WindowGlassSession session, View sceneHost) {
        WindowSlot window = new WindowSlot(session, sceneHost);
        String bindingId = adapterId + ":window:" + (++nextWindowSequence);
        window.binding = session.bindAdapter(bindingId, new WindowGlassSession.AdapterPresentationListener() {
            @Override
            public void onPresentationChanged(Set<String> authorizedNodeIds, Set<String> revokedNodeIds) {
                handlePresentation(window, authorizedNodeIds, revokedNodeIds);
            }

            @Override
            public void onTerminalFailure(String stage, Throwable error) {
                handleTerminalFailure(window, stage, error);
            }
        });
        ViewTreeObserver observer = sceneHost.getViewTreeObserver();
        if (observer.isAlive()) observer.addOnPreDrawListener(window.preDrawListener);
        return window;
    }

    private void handlePresentation(
            WindowSlot window,
            Set<String> authorizedNodeIds,
            Set<String> revokedNodeIds) {
        if (closed || window.failed) return;
        Set<String> authorized = authorizedNodeIds == null ? Set.of() : authorizedNodeIds;
        Set<String> revoked = revokedNodeIds == null ? Set.of() : revokedNodeIds;
        boolean changed = false;

        for (Map.Entry<View, HostSlot> entry : List.copyOf(window.hosts.entrySet())) {
            View host = entry.getKey();
            HostSlot slot = entry.getValue();
            if (!slot.active || slot.broken) continue;
            if (revoked.contains(slot.nodeId)
                    && slot.state.revoke(slot.nodeId, slot.generation)) {
                safeRestore(host, slot);
                changed = true;
            }
            if (authorized.contains(slot.nodeId)
                    && slot.state.authorize(slot.nodeId, slot.generation)) {
                try {
                    slot.material.suppress(host, slot.generation);
                } catch (Throwable error) {
                    slot.state.revoke(slot.nodeId, slot.generation);
                    safeRestore(host, slot);
                    slot.broken = true;
                    changed = true;
                }
            }
        }
        if (changed) refreshWindow(window);
    }

    private void handleTerminalFailure(WindowSlot window, String stage, Throwable error) {
        if (window.failed) return;
        window.failed = true;
        for (Map.Entry<View, HostSlot> entry : List.copyOf(window.hosts.entrySet())) {
            View host = entry.getKey();
            HostSlot slot = entry.getValue();
            if (!slot.active) continue;
            slot.state.failTerminal();
            safeRestore(host, slot);
            try { slot.material.restoreAll(); } catch (Throwable ignored) {}
            slot.active = false;
            detachHostListeners(host, slot);
        }
        window.hosts.clear();
        window.lastNodes = List.of();
        removePreDraw(window);
        windows.remove(window.session);
    }

    private void unregisterInternal(View host, HostSlot slot, String reason) {
        WindowSlot window = slot.window;
        releaseHost(host, slot, reason);
        if (window == null) return;
        refreshWindow(window);
        if (window.hosts.isEmpty()) {
            disposeWindow(window, true);
            windows.remove(window.session);
        }
    }

    private void releaseHost(View host, HostSlot slot, String reason) {
        if (!slot.active) return;
        WindowSlot window = slot.window;
        slot.state.unregister(slot.nodeId, slot.generation);
        safeRestore(host, slot);
        detachHostListeners(host, slot);
        slot.active = false;
        slot.broken = false;
        if (window != null) window.hosts.remove(host);
        slot.window = null;
        slot.kind = null;
        slot.geometry = null;
        slot.material = null;
        slot.nodeId = null;
    }

    private void refreshFor(View host) {
        if (closed) return;
        HostSlot slot = slots.get(host);
        if (slot != null && slot.active && slot.window != null) refreshWindow(slot.window);
    }

    private void refreshWindow(WindowSlot window) {
        if (closed || window == null || window.failed || window.session.isClosed()) return;
        List<GlassNode> nodes = new ArrayList<>();
        for (Map.Entry<View, HostSlot> entry : List.copyOf(window.hosts.entrySet())) {
            GlassNode node = collectNode(entry.getKey(), entry.getValue(), window.sceneHost);
            if (node != null) nodes.add(node);
        }
        List<GlassNode> next = List.copyOf(nodes);
        if (!next.equals(window.lastNodes)) {
            window.lastNodes = next;
            window.binding.publish(next);
        }
        window.binding.setActive(!next.isEmpty(), "bounded-hosts");
    }

    private static GlassNode collectNode(View host, HostSlot slot, View sceneHost) {
        if (!slot.active || slot.broken || slot.geometry == null || slot.kind == null) return null;
        if (!host.isAttachedToWindow() || !host.isShown()) return null;
        float effectiveAlpha = effectiveWindowAlpha(host);
        if (effectiveAlpha <= 0.001f) return null;
        int width = host.getWidth();
        int height = host.getHeight();
        if (width <= 0 || height <= 0) return null;

        int[] hostLocation = new int[2];
        int[] sceneLocation = new int[2];
        host.getLocationInWindow(hostLocation);
        sceneHost.getLocationInWindow(sceneLocation);
        float left = hostLocation[0] - sceneLocation[0];
        float top = hostLocation[1] - sceneLocation[1];
        float right = left + width;
        float bottom = top + height;
        if (right <= 0f || bottom <= 0f
                || left >= sceneHost.getWidth() || top >= sceneHost.getHeight()) return null;

        float maxRadius = Math.min(width, height) * 0.5f;
        GlassHostGeometry g = slot.geometry;
        float opacity = Math.max(0f, Math.min(1f, g.opacity() * effectiveAlpha));
        return new GlassNode(
                slot.nodeId,
                slot.generation,
                left,
                top,
                width,
                height,
                Math.min(g.topLeftRadius(), maxRadius),
                Math.min(g.topRightRadius(), maxRadius),
                Math.min(g.bottomRightRadius(), maxRadius),
                Math.min(g.bottomLeftRadius(), maxRadius),
                opacity,
                g.zOrder(),
                SystemUiMaterialClassifier.profileFor(slot.kind));
    }

    /** View.isShown() ignores ancestor alpha; page switching on HyperOS does not. */
    private static float effectiveWindowAlpha(View host) {
        float alpha = 1f;
        View current = host;
        while (current != null) {
            if (current.getVisibility() != View.VISIBLE) return 0f;
            alpha *= current.getAlpha();
            if (alpha <= 0.001f) return 0f;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return alpha;
    }

    private static void safeRestore(View host, HostSlot slot) {
        if (slot.material == null) return;
        try { slot.material.restore(host, slot.generation); } catch (Throwable ignored) {}
    }

    private void disposeWindow(WindowSlot window, boolean closeBinding) {
        removePreDraw(window);
        if (!closeBinding || window.binding == null || window.session.isClosed()) return;
        try {
            window.binding.setActive(false, "window-empty");
            if (!window.lastNodes.isEmpty()) window.binding.publish(List.of());
        } catch (Throwable ignored) {}
        try { window.binding.close(); } catch (Throwable ignored) {}
    }

    private static void removePreDraw(WindowSlot window) {
        ViewTreeObserver observer = window.sceneHost.getViewTreeObserver();
        if (observer.isAlive()) {
            try { observer.removeOnPreDrawListener(window.preDrawListener); } catch (Throwable ignored) {}
        }
    }

    private static void detachHostListeners(View host, HostSlot slot) {
        try { host.removeOnAttachStateChangeListener(slot.attachListener); } catch (Throwable ignored) {}
        try { host.removeOnLayoutChangeListener(slot.layoutListener); } catch (Throwable ignored) {}
    }

    private final class HostSlot {
        final SystemUiGlassHostState state = new SystemUiGlassHostState();
        final View.OnAttachStateChangeListener attachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { refreshFor(v); }
            @Override public void onViewDetachedFromWindow(View v) { unregister(v, "detached"); }
        };
        final View.OnLayoutChangeListener layoutListener =
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> refreshFor(v);

        String nodeId;
        long generation;
        SystemUiMaterialHostKind kind;
        GlassHostGeometry geometry;
        NativeMaterialController<View> material;
        WindowSlot window;
        boolean active;
        boolean broken;
    }

    private final class WindowSlot {
        final WindowGlassSession session;
        final View sceneHost;
        final IdentityHashMap<View, HostSlot> hosts = new IdentityHashMap<>();
        final ViewTreeObserver.OnPreDrawListener preDrawListener = () -> {
            refreshWindow(this);
            return true;
        };
        WindowGlassSession.AdapterBinding binding;
        List<GlassNode> lastNodes = List.of();
        boolean failed;

        WindowSlot(WindowGlassSession session, View sceneHost) {
            this.session = session;
            this.sceneHost = sceneHost;
        }
    }
}
