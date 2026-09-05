package com.hellovoid.liquidui.xposed;

import com.hellovoid.liquidui.Api101Bridge;
import com.hellovoid.liquidui.diagnostics.LiquidUiLog;
import com.hellovoid.liquidui.hook.BeforeMethodHookBackend;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;

/** API101 adapter for exact before-invocation callbacks. */
public final class Api101BeforeMethodHookBackend implements BeforeMethodHookBackend {
    private final boolean diagnosticsEnabled;

    public Api101BeforeMethodHookBackend(boolean diagnosticsEnabled) {
        this.diagnosticsEnabled = diagnosticsEnabled;
    }

    @Override
    public Registration intercept(Method method, int priority, BeforeInvocation before) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(before, "before");
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
                                        + "#" + method.getName()));
                    }
                    before.before(chain.getThisObject(), chain.getArgs().toArray(new Object[0]));
                    return chain.proceed();
                });
        return handle::unhook;
    }
}
