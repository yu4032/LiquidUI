package com.hellovoid.liquidui.hook;

import java.lang.reflect.Method;

/** Backend contract for executing a callback immediately before an exact method invocation. */
public interface BeforeMethodHookBackend {
    int PRIORITY_HIGHEST = Integer.MAX_VALUE;

    @FunctionalInterface
    interface BeforeInvocation {
        void before(Object thisObject, Object[] args) throws Throwable;
    }

    interface Registration {
        void unhook();
    }

    Registration intercept(Method method, int priority, BeforeInvocation before);
}
