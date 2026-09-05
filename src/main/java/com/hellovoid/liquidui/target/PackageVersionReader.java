package com.hellovoid.liquidui.target;

@FunctionalInterface
public interface PackageVersionReader {
    PackageVersion read(String packageName) throws Exception;
}
