package com.hellovoid.liquidui.target;

import java.util.Objects;

public final class SystemUiRuntimeInfoProvider {
    private final PackageVersionReader packageVersionReader;

    public SystemUiRuntimeInfoProvider(PackageVersionReader packageVersionReader) {
        this.packageVersionReader = Objects.requireNonNull(packageVersionReader, "packageVersionReader");
    }

    public SystemUiRuntimeInfo read(String packageName, int sdkInt) throws Exception {
        PackageVersion version = packageVersionReader.read(Objects.requireNonNull(packageName, "packageName"));
        return new SystemUiRuntimeInfo(packageName, version.versionCode(), version.versionName(), sdkInt);
    }
}
