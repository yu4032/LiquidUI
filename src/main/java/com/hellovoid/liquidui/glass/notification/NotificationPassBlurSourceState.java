package com.hellovoid.liquidui.glass.notification;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Process-local authority for WM Shell RootTaskDisplayArea source leashes. */
final class NotificationPassBlurSourceState {
    interface Listener {
        void onSourceChanged(Snapshot snapshot);
    }

    static final class Snapshot {
        private final int displayId;
        private final Object source;
        private final long generation;

        Snapshot(int displayId, Object source, long generation) {
            this.displayId = displayId;
            this.source = source;
            this.generation = generation;
        }

        int displayId() { return displayId; }
        Object source() { return source; }
        long generation() { return generation; }
        boolean available() { return source != null; }
    }

    private static final class Entry {
        Object source;
        long generation;
    }

    private static final class ListenerEntry {
        final int displayId;
        final WeakReference<Listener> listener;
        ListenerEntry(int displayId, Listener listener) {
            this.displayId = displayId;
            this.listener = new WeakReference<>(listener);
        }
    }

    private final Map<Integer, Entry> entries = new HashMap<>();
    private final List<ListenerEntry> listeners = new ArrayList<>();
    private long nextGeneration;

    synchronized Snapshot snapshot(int displayId) {
        Entry entry = entries.get(displayId);
        return entry == null
                ? new Snapshot(displayId, null, 0L)
                : new Snapshot(displayId, entry.source, entry.generation);
    }

    void observe(int displayId, Object source) {
        if (source == null) return;
        Snapshot changed;
        List<Listener> notify;
        synchronized (this) {
            Entry entry = entries.get(displayId);
            if (entry != null && entry.source == source) return;
            if (entry == null) {
                entry = new Entry();
                entries.put(displayId, entry);
            }
            entry.source = source;
            entry.generation = ++nextGeneration;
            changed = new Snapshot(displayId, source, entry.generation);
            notify = collectListenersLocked(displayId);
        }
        for (Listener listener : notify) listener.onSourceChanged(changed);
    }

    void remove(int displayId, Object expectedSource) {
        Snapshot changed;
        List<Listener> notify;
        synchronized (this) {
            Entry entry = entries.get(displayId);
            if (entry == null || (expectedSource != null && entry.source != expectedSource)) return;
            entries.remove(displayId);
            changed = new Snapshot(displayId, null, ++nextGeneration);
            notify = collectListenersLocked(displayId);
        }
        for (Listener listener : notify) listener.onSourceChanged(changed);
    }

    void addListener(int displayId, Listener listener) {
        if (listener == null) return;
        Snapshot current;
        synchronized (this) {
            pruneLocked();
            listeners.add(new ListenerEntry(displayId, listener));
            current = snapshot(displayId);
        }
        if (current.available()) listener.onSourceChanged(current);
    }

    synchronized void removeListener(Listener listener) {
        if (listener == null) return;
        Iterator<ListenerEntry> iterator = listeners.iterator();
        while (iterator.hasNext()) {
            Listener value = iterator.next().listener.get();
            if (value == null || value == listener) iterator.remove();
        }
    }

    private List<Listener> collectListenersLocked(int displayId) {
        List<Listener> result = new ArrayList<>();
        Iterator<ListenerEntry> iterator = listeners.iterator();
        while (iterator.hasNext()) {
            ListenerEntry entry = iterator.next();
            Listener value = entry.listener.get();
            if (value == null) iterator.remove();
            else if (entry.displayId == displayId) result.add(value);
        }
        return result;
    }

    private void pruneLocked() {
        Iterator<ListenerEntry> iterator = listeners.iterator();
        while (iterator.hasNext()) if (iterator.next().listener.get() == null) iterator.remove();
    }
}
