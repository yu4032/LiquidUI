package com.hellovoid.liquidui.xposed;

import com.hellovoid.liquidui.Api101Bridge;
import com.hellovoid.liquidui.hook.IntArgumentHookBackend;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

import io.github.libxposed.api.XposedInterface;

/** API101 adapter for pure int-argument hook contracts. */
public final class Api101IntArgumentHookBackend implements IntArgumentHookBackend {
    public static final Api101IntArgumentHookBackend INSTANCE =
            new Api101IntArgumentHookBackend();

    private Api101IntArgumentHookBackend() {}

    @Override
    public Registration intercept(
            Method method,
            int argumentIndex,
            int priority,
            IntUnaryOperator rewriter) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(rewriter, "rewriter");
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (argumentIndex < 0 || argumentIndex >= parameterTypes.length) {
            throw new IllegalArgumentException("argument index out of range: " + argumentIndex);
        }
        if (parameterTypes[argumentIndex] != int.class) {
            throw new IllegalArgumentException(
                    "argument is not int: " + method + " index=" + argumentIndex);
        }

        method.setAccessible(true);
        XposedInterface.HookHandle handle = Api101Bridge.module()
                .hook(method)
                .setPriority(priority)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    Object current = args[argumentIndex];
                    if (!(current instanceof Integer)) {
                        return chain.proceed();
                    }
                    args[argumentIndex] = rewriter.applyAsInt((Integer) current);
                    return chain.proceed(args);
                });
        return handle::unhook;
    }
}
