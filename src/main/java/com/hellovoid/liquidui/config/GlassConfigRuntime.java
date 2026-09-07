package com.hellovoid.liquidui.config;

import android.content.SharedPreferences;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Observes API101 Remote Preferences and republishes complete immutable glass snapshots. */
public final class GlassConfigRuntime implements
        SharedPreferences.OnSharedPreferenceChangeListener, AutoCloseable {
    private final SharedPreferences preferences;
    private final Consumer<GlassStyleConfig> publisher;
    private final AtomicBoolean closed = new AtomicBoolean();

    public GlassConfigRuntime(
            SharedPreferences preferences,
            Consumer<GlassStyleConfig> publisher) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        preferences.registerOnSharedPreferenceChangeListener(this);
    }

    public GlassStyleConfig current() {
        return GlassStyleConfig.read(new ConfigReader(
                preferences::getBoolean,
                preferences::getInt));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (closed.get() || !ConfigSchema.isGlassStyleKey(key)) return;
        publisher.accept(GlassStyleConfig.read(new ConfigReader(
                preferences::getBoolean,
                preferences::getInt)));
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        preferences.unregisterOnSharedPreferenceChangeListener(this);
    }
}
