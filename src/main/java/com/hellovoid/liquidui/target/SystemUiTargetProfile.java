package com.hellovoid.liquidui.target;

import java.util.List;

public interface SystemUiTargetProfile {
    String id();
    String packageName();
    long versionCode();
    String versionName();
    int sdkInt();
    List<StructuralProbe> structuralProbes();

    default boolean matches(SystemUiRuntimeInfo runtime) {
        return packageName().equals(runtime.packageName())
                && versionCode() == runtime.versionCode()
                && versionName().equals(runtime.versionName())
                && sdkInt() == runtime.sdkInt();
    }
}
