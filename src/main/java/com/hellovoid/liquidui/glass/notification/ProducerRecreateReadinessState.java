package com.hellovoid.liquidui.glass.notification;

/** Android-free gate that prevents producer rollover before the output EGL surface is current. */
final class ProducerRecreateReadinessState {
    enum Action { NONE, DEFER, RUN_NOW }

    private boolean outputReady;
    private boolean recreatePending;

    synchronized Action requestRecreate() {
        if (outputReady) return Action.RUN_NOW;
        recreatePending = true;
        return Action.DEFER;
    }

    synchronized Action onOutputReady() {
        outputReady = true;
        if (!recreatePending) return Action.NONE;
        recreatePending = false;
        return Action.RUN_NOW;
    }

    synchronized void onOutputUnavailable() {
        outputReady = false;
    }

    synchronized boolean isOutputReady() {
        return outputReady;
    }

    synchronized boolean hasPendingRecreate() {
        return recreatePending;
    }
}
