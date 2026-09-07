package com.hellovoid.liquidui.glass.core;

import java.util.concurrent.atomic.AtomicBoolean;

/** Process-global ownership root for SystemUI glass. */
public final class SystemUiGlassCore implements AutoCloseable {
    private final WindowGlassRegistry windows;
    private final AtomicBoolean closed = new AtomicBoolean();

    public SystemUiGlassCore(WindowGlassRegistry.Factory sessionFactory) {
        windows = new WindowGlassRegistry(sessionFactory);
    }

    public WindowGlassSession sessionFor(Object root, int displayId) {
        if (closed.get()) throw new IllegalStateException("core closed");
        return windows.sessionFor(new WindowKey(root, displayId));
    }

    public int purgeCollectedWindows() {
        return windows.purgeCollected();
    }

    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        windows.close();
    }
}
