package com.hellovoid.liquidui.hook;

import java.lang.reflect.Method;
import java.util.function.IntUnaryOperator;

/** Pure boundary for installing an interceptor that rewrites one int method argument. */
public interface IntArgumentHookBackend {
    int PRIORITY_HIGHEST = Integer.MAX_VALUE;

    Registration intercept(
            Method method,
            int argumentIndex,
            int priority,
            IntUnaryOperator rewriter) throws Throwable;

    interface Registration {
        void unhook();
    }
}
