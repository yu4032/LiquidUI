package com.hellovoid.liquidui.hook;

import java.lang.reflect.Method;

/** Pure boundary for installing an interceptor that rewrites one boolean method argument. */
public interface BooleanArgumentHookBackend {
    int PRIORITY_HIGHEST = Integer.MAX_VALUE;

    Registration intercept(
            Method method,
            int argumentIndex,
            int priority,
            BooleanRewriter rewriter) throws Throwable;

    @FunctionalInterface
    interface BooleanRewriter {
        boolean applyAsBoolean(boolean value);
    }

    interface Registration {
        void unhook();
    }
}
