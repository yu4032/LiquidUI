package com.hellovoid.liquidui.glass.core;

import android.os.Handler;
import android.os.HandlerThread;
import android.view.Display;
import android.view.View;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Process-global ownership root for SystemUI glass. */
public final class SystemUiGlassCore implements AutoCloseable {
    /** Factory boundary used by production sessions that need the one shared render Handler. */
    public interface SessionFactory {
        WindowGlassSession create(WindowKey key, Handler renderHandler);
    }

    private final HandlerThread renderThread;
    private final Handler renderHandler;
    private final WindowGlassRegistry windows;
    private final AtomicBoolean closed = new AtomicBoolean();

    /** Production path: owns the process-global SystemUI glass render thread. */
    public SystemUiGlassCore(SessionFactory sessionFactory) {
        Objects.requireNonNull(sessionFactory, "sessionFactory");
        renderThread = new HandlerThread("LiquidUI-SystemUiGlass");
        renderThread.start();
        renderHandler = new Handler(renderThread.getLooper());
        windows = new WindowGlassRegistry(key -> sessionFactory.create(key, renderHandler));
    }

    /**
     * Pure registry path used by SDK-independent lifecycle tests. Package-private by design so
     * production callers cannot accidentally construct a core without the shared render thread.
     */
    SystemUiGlassCore(WindowGlassRegistry.Factory sessionFactory) {
        windows = new WindowGlassRegistry(Objects.requireNonNull(sessionFactory, "sessionFactory"));
        renderThread = null;
        renderHandler = null;
    }

    /** Resolve any attached component View to one ViewRoot/display session, never to the component. */
    public WindowGlassSession sessionFor(View host) {
        Objects.requireNonNull(host, "host");
        if (closed.get()) throw new IllegalStateException("core closed");
        Object root = null;
        try {
            root = SystemUiPassBlurBridge.getViewRootImpl(host);
        } catch (Throwable ignored) {
            // Fall through to the root View identity. This is still Window-scoped for one attach.
        }
        if (root == null) root = host.getRootView();
        if (root == null) throw new IllegalStateException("Window root unavailable");
        Display display = host.getDisplay();
        int displayId = display != null ? display.getDisplayId() : Display.DEFAULT_DISPLAY;
        return sessionFor(root, displayId);
    }

    public WindowGlassSession sessionFor(Object root, int displayId) {
        if (closed.get()) throw new IllegalStateException("core closed");
        return windows.sessionFor(new WindowKey(root, displayId));
    }

    Handler renderHandler() {
        return renderHandler;
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
        if (renderThread != null) renderThread.quitSafely();
    }
}
