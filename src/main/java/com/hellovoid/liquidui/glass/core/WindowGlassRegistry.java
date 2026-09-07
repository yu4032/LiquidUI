package com.hellovoid.liquidui.glass.core;

import com.hellovoid.liquidui.config.GlassStyleConfig;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/** Owns exactly one live session for each Window root/display identity. */
public final class WindowGlassRegistry implements AutoCloseable {
    public interface Factory {
        WindowGlassSession create(WindowKey key);
    }

    private final Factory factory;
    private final Map<WindowKey, WindowGlassSession> sessions = new HashMap<>();
    private GlassStyleConfig latestStyle = GlassStyleConfig.defaults();
    private long latestStyleVersion = 1L;
    private boolean closed;

    public WindowGlassRegistry(Factory factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    public synchronized WindowGlassSession sessionFor(WindowKey key) {
        Objects.requireNonNull(key, "key");
        if (closed) throw new IllegalStateException("registry closed");
        if (key.isCollected()) throw new IllegalArgumentException("root already collected");
        purgeCollected();
        WindowGlassSession current = sessions.get(key);
        if (current != null && !current.isClosed()) return current;
        WindowGlassSession created = Objects.requireNonNull(factory.create(key), "session");
        created.updateGlassStyles(latestStyle, latestStyleVersion);
        sessions.put(key, created);
        return created;
    }

    synchronized void updateGlassStyles(GlassStyleConfig style, long version) {
        Objects.requireNonNull(style, "style");
        if (closed || version <= latestStyleVersion) return;
        latestStyle = style;
        latestStyleVersion = version;
        purgeCollected();
        for (WindowGlassSession session : sessions.values()) {
            if (!session.isClosed()) session.updateGlassStyles(style, version);
        }
    }

    public synchronized boolean remove(WindowKey key, WindowGlassSession session) {
        if (!sessions.remove(key, session)) return false;
        session.close();
        return true;
    }

    public synchronized int purgeCollected() {
        int removed = 0;
        Iterator<Map.Entry<WindowKey, WindowGlassSession>> iterator =
                sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<WindowKey, WindowGlassSession> entry = iterator.next();
            if (!entry.getKey().isCollected()) continue;
            iterator.remove();
            entry.getValue().close();
            removed++;
        }
        return removed;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        for (WindowGlassSession session : sessions.values()) session.close();
        sessions.clear();
    }
}
