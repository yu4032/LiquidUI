package com.hellovoid.liquidui.glass.notification;

/** Reference-counted authority for whether any notification glass scene is currently presented. */
final class NotificationGlassActivityState {
    private int activeSessions;

    synchronized void activate() {
        activeSessions++;
    }

    synchronized void deactivate() {
        if (activeSessions > 0) activeSessions--;
    }

    synchronized boolean isActive() {
        return activeSessions > 0;
    }
}
