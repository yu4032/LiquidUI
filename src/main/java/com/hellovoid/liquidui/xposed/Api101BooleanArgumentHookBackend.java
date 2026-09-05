package com.hellovoid.liquidui.xposed;

import com.hellovoid.liquidui.Api101Bridge;
import com.hellovoid.liquidui.hook.BooleanArgumentHookBackend;

import java.lang.reflect.Method;
import java.util.Objects;

import io.github.libxposed.api.XposedInterface;

/** API101 adapter for pure boolean-argument hook contracts. */
public final class Api101BooleanArgumentHookBackend implements BooleanArgumentHookBackend {
    public static final Api101BooleanArgumentHookBackend INSTANCE =
            new Api101BooleanArgumentHookBackend();

    private Api101BooleanArgumentHookBackend() {}

    @Override
    public Registration intercept(
            Method method,
            int argumentIndex,
            int priority,
            BooleanRewriter rewriter) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(rewriter, "rewriter");
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (argumentIndex < 0 || argumentIndex >= parameterTypes.length) {
            throw new IllegalArgumentException("argument index out of range: " + argumentIndex);
        }
        if (parameterTypes[argumentIndex] != boolean.class) {
            throw new IllegalArgumentException(
                    "argument is not boolean: " + method + " index=" + argumentIndex);
        }

        method.setAccessible(true);
        XposedInterface.HookHandle handle = Api101Bridge.module()
                .hook(method)
                .setPriority(priority)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    Object current = args[argumentIndex];
                    if (!(current instanceof Boolean)) {
                        return chain.proceed();
                    }
                    args[argumentIndex] = rewriter.applyAsBoolean((Boolean) current);
                    return chain.proceed(args);
                });
        return handle::unhook;
    }
}
