package com.hellovoid.liquidui;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.hellovoid.liquidui.config.ConfigKey;
import com.hellovoid.liquidui.config.ConfigSchema;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** Module-app bridge that mirrors the minimal local UI schema to API101 Remote Preferences. */
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
            reconcile(ConfigSchema.ENABLED);
            reconcile(ConfigSchema.DIAGNOSTICS_ENABLED);
            reconcile(ConfigSchema.NOTIFICATION_GLASS_ENABLED);
        } catch (Throwable error) {
            Log.w("LiquidUI", "Remote Preferences reconciliation failed", error);
        }
    }

    @Override
    public void onServiceDied(XposedService value) {
        if (service == value) service = null;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (reconciling || key == null) return;
        if (ConfigSchema.ENABLED.name().equals(key)) {
            sync(ConfigSchema.ENABLED);
        } else if (ConfigSchema.DIAGNOSTICS_ENABLED.name().equals(key)) {
            sync(ConfigSchema.DIAGNOSTICS_ENABLED);
        } else if (ConfigSchema.NOTIFICATION_GLASS_ENABLED.name().equals(key)) {
            sync(ConfigSchema.NOTIFICATION_GLASS_ENABLED);
        }
    }

    private void reconcile(ConfigKey<Boolean> key) {
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
            localPreferences.edit()
                    .putBoolean(name, remote.getBoolean(name, key.defaultValue()))
                    .apply();
        } finally {
            reconciling = false;
        }
    }

    private void sync(ConfigKey<Boolean> key) {
        SharedPreferences remote = remotePreferences();
        if (remote == null || localPreferences == null) return;
        String name = key.name();
        remote.edit()
                .putBoolean(name, localPreferences.getBoolean(name, key.defaultValue()))
                .apply();
    }

    private static SharedPreferences remotePreferences() {
        XposedService value = service;
        return value == null ? null : value.getRemotePreferences(REMOTE_GROUP);
    }
}
