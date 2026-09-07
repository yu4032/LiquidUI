package com.hellovoid.liquidui;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.hellovoid.liquidui.config.ConfigKey;
import com.hellovoid.liquidui.config.ConfigSchema;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** Mirrors the complete typed local settings schema to API101 Remote Preferences. */
public final class LiquidUiApp extends Application
        implements XposedServiceHelper.OnServiceListener,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String REMOTE_GROUP = "config";
    private static volatile XposedService service;

    private SharedPreferences localPreferences;
    private boolean reconciling;

    @Override
    public void onCreate() {
        super.onCreate();
        localPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        localPreferences.registerOnSharedPreferenceChangeListener(this);
        XposedServiceHelper.registerListener(this);
    }

    @Override
    public void onServiceBind(XposedService value) {
        service = value;
        try {
            for (ConfigKey<?> key : ConfigSchema.all()) reconcile(key);
        } catch (Throwable error) {
            Log.w("LiquidUI", "Remote Preferences reconciliation failed", error);
        }
    }

    @Override
    public void onServiceDied(XposedService value) {
        if (service == value) service = null;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String changedKey) {
        if (reconciling || changedKey == null) return;
        for (ConfigKey<?> key : ConfigSchema.all()) {
            if (!key.name().equals(changedKey)) continue;
            sync(key);
            return;
        }
    }

    private void reconcile(ConfigKey<?> key) {
        SharedPreferences remote = remotePreferences();
        if (remote == null || localPreferences == null) return;
        String name = key.name();
        if (localPreferences.contains(name)) {
            sync(key);
            return;
        }
        if (!remote.contains(name)) return;

        reconciling = true;
        try {
            SharedPreferences.Editor editor = localPreferences.edit();
            if (key.kind() == ConfigKey.Kind.BOOLEAN) {
                boolean fallback = (Boolean) key.defaultValue();
                editor.putBoolean(name, remote.getBoolean(name, fallback));
            } else if (key.kind() == ConfigKey.Kind.INT) {
                int fallback = (Integer) key.defaultValue();
                editor.putInt(name, remote.getInt(name, fallback));
            }
            editor.apply();
        } finally {
            reconciling = false;
        }
    }

    private void sync(ConfigKey<?> key) {
        SharedPreferences remote = remotePreferences();
        if (remote == null || localPreferences == null) return;
        String name = key.name();
        SharedPreferences.Editor editor = remote.edit();
        if (key.kind() == ConfigKey.Kind.BOOLEAN) {
            boolean fallback = (Boolean) key.defaultValue();
            editor.putBoolean(name, localPreferences.getBoolean(name, fallback));
        } else if (key.kind() == ConfigKey.Kind.INT) {
            int fallback = (Integer) key.defaultValue();
            editor.putInt(name, localPreferences.getInt(name, fallback));
        }
        editor.apply();
    }

    private static SharedPreferences remotePreferences() {
        XposedService value = service;
        return value == null ? null : value.getRemotePreferences(REMOTE_GROUP);
    }
}
