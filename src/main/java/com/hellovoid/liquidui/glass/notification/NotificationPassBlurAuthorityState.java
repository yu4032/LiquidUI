package com.hellovoid.liquidui.glass.notification;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Process-local mirror of HyperOS notificationPanelView notifPassBlur authority. */
final class NotificationPassBlurAuthorityState {
    interface Listener {
        void onPassBlurChanged(boolean enabled);
    }

    private final List<WeakReference<Listener>> listeners = new ArrayList<>();
    private boolean known;
    private boolean enabled;

    synchronized boolean isKnown() {
        return known;
    }

    synchronized boolean isEnabled() {
        return known && enabled;
    }

    void observe(boolean nextEnabled) {
        List<Listener> notify = new ArrayList<>();
        synchronized (this) {
            if (known && enabled == nextEnabled) return;
            known = true;
            enabled = nextEnabled;
            collectLiveListenersLocked(notify);
        }
        for (Listener listener : notify) listener.onPassBlurChanged(nextEnabled);
    }

    void addListener(Listener listener) {
        if (listener == null) return;
        Boolean current = null;
        synchronized (this) {
            pruneLocked();
            listeners.add(new WeakReference<>(listener));
            if (known) current = enabled;
        }
        if (current != null) listener.onPassBlurChanged(current);
    }

    synchronized void removeListener(Listener listener) {
        if (listener == null) return;
        Iterator<WeakReference<Listener>> iterator = listeners.iterator();
        while (iterator.hasNext()) {
            Listener value = iterator.next().get();
            if (value == null || value == listener) iterator.remove();
        }
    }

    private void collectLiveListenersLocked(List<Listener> output) {
        Iterator<WeakReference<Listener>> iterator = listeners.iterator();
        while (iterator.hasNext()) {
            Listener value = iterator.next().get();
            if (value == null) iterator.remove();
            else output.add(value);
        }
    }

    private void pruneLocked() {
        Iterator<WeakReference<Listener>> iterator = listeners.iterator();
        while (iterator.hasNext()) if (iterator.next().get() == null) iterator.remove();
    }
}
