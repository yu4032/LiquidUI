package com.hellovoid.liquidui.glass.core;

import com.hellovoid.liquidui.config.GlassStyleConfig;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Android-free latest-state queue for high-frequency glass style updates.
 *
 * A burst of submits owns one scheduled drain. The drain delivers the newest immutable snapshot;
 * updates that arrive after that drain begins schedule exactly one follow-up drain.
 */
public final class GlassStyleUpdateQueue {
    public record Snapshot(long version, GlassStyleConfig style) {
        public Snapshot {
            if (version <= 0L) throw new IllegalArgumentException("version must be positive");
            Objects.requireNonNull(style, "style");
        }
    }

    private final Consumer<Runnable> scheduler;
    private final Consumer<Snapshot> consumer;
    private Snapshot current = new Snapshot(1L, GlassStyleConfig.defaults());
    private boolean drainScheduled;

    public GlassStyleUpdateQueue(
            Consumer<Runnable> scheduler,
            Consumer<Snapshot> consumer) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.consumer = Objects.requireNonNull(consumer, "consumer");
    }

    public synchronized Snapshot current() {
        return current;
    }

    public long submit(GlassStyleConfig style) {
        Runnable drain = null;
        long version;
        synchronized (this) {
            current = new Snapshot(current.version() + 1L, Objects.requireNonNull(style, "style"));
            version = current.version();
            if (!drainScheduled) {
                drainScheduled = true;
                drain = this::drain;
            }
        }
        if (drain != null) scheduler.accept(drain);
        return version;
    }

    private void drain() {
        Snapshot snapshot;
        synchronized (this) {
            snapshot = current;
            drainScheduled = false;
        }
        consumer.accept(snapshot);
    }
}
