package com.hellovoid.liquidui.hook;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class HookRegistryReport {
    private final Map<String, HookInstallResult> results;

    HookRegistryReport(Map<String, HookInstallResult> results) {
        this.results = Collections.unmodifiableMap(new LinkedHashMap<>(results));
    }

    public Map<String, HookInstallResult> results() { return results; }

    public HookInstallResult result(String hookId) {
        HookInstallResult result = results.get(Objects.requireNonNull(hookId, "hookId"));
        if (result == null) throw new IllegalArgumentException("unknown hook: " + hookId);
        return result;
    }

    public boolean hasFailures() {
        for (HookInstallResult result : results.values()) {
            if (result.status() == HookInstallStatus.FAILED) return true;
        }
        return false;
    }

    public boolean isCleanSuccess() {
        return !hasFailures();
    }
}
