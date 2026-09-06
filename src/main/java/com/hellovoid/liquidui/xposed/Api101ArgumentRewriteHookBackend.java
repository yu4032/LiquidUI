package com.hellovoid.liquidui.xposed;

import com.hellovoid.liquidui.Api101Bridge;
import com.hellovoid.liquidui.diagnostics.LiquidUiLog;
import com.hellovoid.liquidui.hook.ArgumentRewriteHookBackend;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;

/** API101 adapter for exact conditional argument rewriting. */
public final class Api101ArgumentRewriteHookBackend implements ArgumentRewriteHookBackend {
    private final boolean diagnosticsEnabled;

    public Api101ArgumentRewriteHookBackend(boolean diagnosticsEnabled) {
        this.diagnosticsEnabled = diagnosticsEnabled;
    }

    @Override
    public Registration intercept(Method method, int priority, InvocationRewriter rewriter) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(rewriter, "rewriter");
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
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    rewriter.rewrite(chain.getThisObject(), args);
                    return chain.proceed(args);
                });
        return handle::unhook;
    }
}
