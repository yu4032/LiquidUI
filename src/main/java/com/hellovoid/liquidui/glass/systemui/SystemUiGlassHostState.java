package com.hellovoid.liquidui.glass.systemui;

import java.util.Objects;

/** Android-free exact lifecycle/presentation authority for one reusable material host. */
public final class SystemUiGlassHostState {
    private long generation;
    private String nodeId;
    private boolean registered;
    private boolean authorized;
    private boolean failed;

    /** Starts a new semantic lifecycle. A terminally failed state cannot be reused. */
    public synchronized long register(String nextNodeId) {
        if (failed) return generation;
        String id = Objects.requireNonNull(nextNodeId, "nodeId");
        if (id.isBlank()) throw new IllegalArgumentException("nodeId blank");
        nodeId = id;
        generation++;
        registered = true;
        authorized = false;
        return generation;
    }

    public synchronized boolean isCurrent(String id, long candidateGeneration) {
        return !failed
                && registered
                && candidateGeneration == generation
                && Objects.equals(nodeId, id);
    }

    /** Returns true only for the transition from current/unpresented to current/authorized. */
    public synchronized boolean authorize(String id, long candidateGeneration) {
        if (!isCurrent(id, candidateGeneration) || authorized) return false;
        authorized = true;
        return true;
    }

    /** Returns true only when the exact current lifecycle was actually authorized. */
    public synchronized boolean revoke(String id, long candidateGeneration) {
        if (!isCurrent(id, candidateGeneration) || !authorized) return false;
        authorized = false;
        return true;
    }

    /** Ends only the exact current lifecycle; generation remains monotonic for later reuse. */
    public synchronized boolean unregister(String id, long candidateGeneration) {
        if (!isCurrent(id, candidateGeneration)) return false;
        authorized = false;
        registered = false;
        nodeId = null;
        return true;
    }

    /** Terminal failure permanently disables this state instance and revokes presentation. */
    public synchronized boolean failTerminal() {
        if (failed) return false;
        failed = true;
        authorized = false;
        registered = false;
        nodeId = null;
        return true;
    }

    public synchronized boolean failed() {
        return failed;
    }

    public synchronized long generation() {
        return generation;
    }

    public synchronized boolean authorized() {
        return authorized;
    }
}
