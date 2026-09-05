package com.hellovoid.liquidui.target;

import java.util.Objects;
import java.util.StringJoiner;

/** Exact declared-method structural probe resolved through the target process ClassLoader. */
public final class RequiredMethodProbe extends StructuralProbe {
    private final String className;
    private final String methodName;
    private final Class<?>[] parameterTypes;

    public RequiredMethodProbe(String className, String methodName, Class<?>[] parameterTypes) {
        super(describe(className, methodName, parameterTypes));
        this.className = Objects.requireNonNull(className, "className");
        this.methodName = Objects.requireNonNull(methodName, "methodName");
        this.parameterTypes = Objects.requireNonNull(parameterTypes, "parameterTypes").clone();
    }

    @Override
    public boolean isSatisfied(ClassLoader classLoader) throws Throwable {
        Class<?> targetClass = Objects.requireNonNull(classLoader, "classLoader").loadClass(className);
        try {
            targetClass.getDeclaredMethod(methodName, parameterTypes);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    private static String describe(String className, String methodName, Class<?>[] parameterTypes) {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(parameterTypes, "parameterTypes");
        StringJoiner parameters = new StringJoiner(",");
        for (Class<?> parameterType : parameterTypes) {
            parameters.add(Objects.requireNonNull(parameterType, "parameterType").getName());
        }
        return className + "#" + methodName + "(" + parameters + ")";
    }
}
