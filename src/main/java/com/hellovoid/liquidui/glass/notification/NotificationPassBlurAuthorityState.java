package com.hellovoid.liquidui.glass.notification;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Process-local mirror of the two independent HyperOS Shade PassBlur authorities.
 *
 * <p>The exact target exposes notificationPanelView/notifPassBlur and
 * ControlCenterContainer/ctrlPassBlur independently. The shared Window producer stays enabled
 * while either page still owns vendor PassBlur authority, so a page handoff does not retire the
 * producer between adjacent Shade pages.</p>
 */
final class NotificationPassBlurAuthorityState {
    interface Listener {
        void onPassBlurChanged(boolean enabled);
    }

    private final List<WeakReference<Listener>> listeners = new ArrayList<>();
    private boolean notificationKnown;
    private boolean notificationEnabled;
    private boolean controlCenterKnown;
    private boolean controlCenterEnabled;

    synchronized boolean isKnown() {
        return notificationKnown || controlCenterKnown;
    }

    synchronized boolean isEnabled() {
        return effectiveEnabledLocked();
    }

    synchronized boolean isNotificationEnabled() {
        return notificationKnown && notificationEnabled;
    }

    synchronized boolean isControlCenterEnabled() {
        return controlCenterKnown && controlCenterEnabled;
    }

    void observeNotification(boolean enabled) {
        observe(true, enabled);
    }

    void observeControlCenter(boolean enabled) {
        observe(false, enabled);
    }

    private void observe(boolean notification, boolean enabled) {
        List<Listener> notify = new ArrayList<>();
        boolean nextEffective;
        synchronized (this) {
            boolean wasKnown = notificationKnown || controlCenterKnown;
            boolean previousEffective = effectiveEnabledLocked();
            if (notification) {
                if (notificationKnown && notificationEnabled == enabled) return;
                notificationKnown = true;
                notificationEnabled = enabled;
            } else {
                if (controlCenterKnown && controlCenterEnabled == enabled) return;
                controlCenterKnown = true;
                controlCenterEnabled = enabled;
            }
            nextEffective = effectiveEnabledLocked();
            if (!wasKnown || previousEffective != nextEffective) {
                collectLiveListenersLocked(notify);
            }
        }
        for (Listener listener : notify) listener.onPassBlurChanged(nextEffective);
    }

    void addListener(Listener listener) {
        if (listener == null) return;
        Boolean current = null;
        synchronized (this) {
            pruneLocked();
            listeners.add(new WeakReference<>(listener));
            if (notificationKnown || controlCenterKnown) current = effectiveEnabledLocked();
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

    private boolean effectiveEnabledLocked() {
        return (notificationKnown && notificationEnabled)
                || (controlCenterKnown && controlCenterEnabled);
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
