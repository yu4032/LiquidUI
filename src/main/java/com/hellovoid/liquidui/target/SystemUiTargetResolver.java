package com.hellovoid.liquidui.target;

import com.hellovoid.liquidui.target.profiles.SystemUi001Profile;

import java.util.List;
import java.util.Objects;

public final class SystemUiTargetResolver {
    private final List<SystemUiTargetProfile> profiles;

    public SystemUiTargetResolver(List<SystemUiTargetProfile> profiles) {
        this.profiles = List.copyOf(Objects.requireNonNull(profiles, "profiles"));
    }

    public static SystemUiTargetResolver defaults() {
        return new SystemUiTargetResolver(List.of(SystemUi001Profile.INSTANCE));
    }

    public TargetResolution resolve(SystemUiRuntimeInfo runtimeInfo, ClassLoader targetClassLoader) {
        Objects.requireNonNull(runtimeInfo, "runtimeInfo");
        Objects.requireNonNull(targetClassLoader, "targetClassLoader");

        SystemUiTargetProfile candidate = null;
        for (SystemUiTargetProfile profile : profiles) {
            if (profile.matches(runtimeInfo)) {
                candidate = profile;
                break;
            }
        }
        if (candidate == null) {
            return TargetResolution.unsupported("no exact target profile for "
                    + runtimeInfo.packageName() + "@" + runtimeInfo.versionName()
                    + " (" + runtimeInfo.versionCode() + ", sdk=" + runtimeInfo.sdkInt() + ")");
        }

        for (StructuralProbe probe : candidate.structuralProbes()) {
            try {
                if (!probe.isSatisfied(targetClassLoader)) {
                    return TargetResolution.unsupported("structural probe missing: " + probe.name());
                }
            } catch (ClassNotFoundException error) {
                return TargetResolution.unsupported("structural probe missing: " + probe.name());
            } catch (Throwable error) {
                return TargetResolution.failed("structural probe failed: " + probe.name(), error);
            }
        }
        return TargetResolution.supported(candidate);
    }
}
