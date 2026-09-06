package com.hellovoid.liquidui.hook;

import java.lang.reflect.Method;

/** Backend contract for conditionally rewriting an exact invocation's arguments. */
public interface ArgumentRewriteHookBackend {
    int PRIORITY_HIGHEST = Integer.MAX_VALUE;

    @FunctionalInterface
    interface InvocationRewriter {
        void rewrite(Object thisObject, Object[] args) throws Throwable;
    }

    interface Registration {
        void unhook();
    }

    Registration intercept(Method method, int priority, InvocationRewriter rewriter);
}
