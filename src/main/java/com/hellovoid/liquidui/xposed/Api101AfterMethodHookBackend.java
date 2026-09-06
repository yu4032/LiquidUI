package com.hellovoid.liquidui.xposed;

import com.hellovoid.liquidui.Api101Bridge;
import com.hellovoid.liquidui.diagnostics.LiquidUiLog;
import com.hellovoid.liquidui.hook.AfterMethodHookBackend;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;

/** API101 adapter for exact after-invocation callbacks. */
public final class Api101AfterMethodHookBackend implements AfterMethodHookBackend {
    private final boolean diagnosticsEnabled;

    public Api101AfterMethodHookBackend(boolean diagnosticsEnabled) {
        this.diagnosticsEnabled = diagnosticsEnabled;
    }

    @Override
    public Registration intercept(Method method, int priority, AfterInvocation after) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(after, "after");
        method.setAccessible(true);
        AtomicBoolean firstHit = new AtomicBoolean();
        XposedInterface.HookHandle handle = Api101Bridge.module()
                .hook(method)
                .setPriority(priority)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    if (diagnosticsEnabled && firstHit.compareAndSet(false, true)) {
                        Api101Bridge.log(LiquidUiLog.format(
                                "hook hit " + method.getDeclaringClass().getName()
                                        + "#" + method.getName() + " [after]"));
                    }
                    Object thisObject = chain.getThisObject();
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    try {
                        return chain.proceed();
                    } finally {
                        after.after(thisObject, args);
                    }
                });
        return handle::unhook;
    }
}
