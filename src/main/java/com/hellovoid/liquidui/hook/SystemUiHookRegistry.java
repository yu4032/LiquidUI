package com.hellovoid.liquidui.hook;

import com.hellovoid.liquidui.target.SystemUiTargetProfile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SystemUiHookRegistry {
    private final List<SystemUiHook> hooks;

    public SystemUiHookRegistry(List<SystemUiHook> hooks) {
        this.hooks = List.copyOf(Objects.requireNonNull(hooks, "hooks"));
        Map<String, Boolean> ids = new LinkedHashMap<>();
        for (SystemUiHook hook : this.hooks) {
            String id = Objects.requireNonNull(hook, "hook").id();
            if (ids.put(id, Boolean.TRUE) != null) {
                throw new IllegalArgumentException("duplicate hook id: " + id);
            }
        }
    }

    public static SystemUiHookRegistry empty() {
        return new SystemUiHookRegistry(List.of());
    }

    public HookRegistryReport installAll(ClassLoader classLoader, SystemUiTargetProfile profile) {
        Objects.requireNonNull(classLoader, "classLoader");
        Objects.requireNonNull(profile, "profile");
        Map<String, HookInstallResult> results = new LinkedHashMap<>();
        for (SystemUiHook hook : hooks) {
            HookInstallResult result;
            try {
                result = Objects.requireNonNull(hook.install(classLoader, profile),
                        "hook result for " + hook.id());
                if (!hook.id().equals(result.hookId())) {
                    throw new IllegalStateException("hook result id mismatch: expected=" + hook.id()
                            + " actual=" + result.hookId());
                }
            } catch (Throwable error) {
                result = HookInstallResult.failed(hook.id(), "uncaught hook install failure", error);
            }
            results.put(hook.id(), result);
        }
        return new HookRegistryReport(results);
    }
}
