package com.hellovoid.liquidui.glass.notification;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Exact NotificationShade content authority derived from NotificationShadeWindowState.keyguardShowing. */
final class NotificationPassBlurContentAuthorityState {
    interface Listener {
        void onContentAuthorityChanged(Snapshot snapshot);
    }

    static final class Snapshot {
        private final boolean known;
        private final boolean keyguardShowing;
        private final long generation;

        Snapshot(boolean known, boolean keyguardShowing, long generation) {
            this.known = known;
            this.keyguardShowing = keyguardShowing;
            this.generation = generation;
        }

        boolean known() { return known; }
        boolean keyguardShowing() { return keyguardShowing; }
        long generation() { return generation; }
        boolean excludeLockWallpaper() { return known && !keyguardShowing; }
    }

    private final List<WeakReference<Listener>> listeners = new ArrayList<>();
    private boolean known;
    private boolean keyguardShowing;
    private long generation;

    synchronized Snapshot snapshot() {
        return new Snapshot(known, keyguardShowing, generation);
    }

    void observe(boolean keyguardShowing) {
        Snapshot changed;
        List<Listener> notify;
        synchronized (this) {
            if (known && this.keyguardShowing == keyguardShowing) return;
            known = true;
            this.keyguardShowing = keyguardShowing;
            changed = new Snapshot(true, keyguardShowing, ++generation);
            notify = collectListenersLocked();
        }
        for (Listener listener : notify) listener.onContentAuthorityChanged(changed);
    }

    void addListener(Listener listener) {
        if (listener == null) return;
        Snapshot current;
        synchronized (this) {
            pruneLocked();
            listeners.add(new WeakReference<>(listener));
            current = snapshot();
        }
        if (current.known()) listener.onContentAuthorityChanged(current);
    }

    synchronized void removeListener(Listener listener) {
        if (listener == null) return;
        Iterator<WeakReference<Listener>> iterator = listeners.iterator();
        while (iterator.hasNext()) {
            Listener value = iterator.next().get();
            if (value == null || value == listener) iterator.remove();
        }
    }

    private List<Listener> collectListenersLocked() {
        List<Listener> result = new ArrayList<>();
        Iterator<WeakReference<Listener>> iterator = listeners.iterator();
        while (iterator.hasNext()) {
            Listener value = iterator.next().get();
            if (value == null) iterator.remove();
            else result.add(value);
        }
        return result;
    }

    private void pruneLocked() {
        Iterator<WeakReference<Listener>> iterator = listeners.iterator();
        while (iterator.hasNext()) if (iterator.next().get() == null) iterator.remove();
    }
}
