package com.hellovoid.liquidui.hook;

import java.lang.reflect.Method;

/** Backend contract for executing a callback after an exact method invocation, including throws. */
public interface AfterMethodHookBackend {
    int PRIORITY_HIGHEST = Integer.MAX_VALUE;

    @FunctionalInterface
    interface AfterInvocation {
        void after(Object thisObject, Object[] args) throws Throwable;
    }

    interface Registration {
        void unhook();
    }

    Registration intercept(Method method, int priority, AfterInvocation after);
}
