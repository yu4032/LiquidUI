package com.hellovoid.liquidui;

import android.content.SharedPreferences;
import android.os.Build;

import com.hellovoid.liquidui.config.ConfigReader;
import com.hellovoid.liquidui.config.LiquidUiConfig;
import com.hellovoid.liquidui.diagnostics.BootstrapDiagnosticsPolicy;
import com.hellovoid.liquidui.diagnostics.LiquidUiLog;
import com.hellovoid.liquidui.hook.HookRegistryReport;
import com.hellovoid.liquidui.hook.SystemUiHookRegistry;
import com.hellovoid.liquidui.glass.notification.NotificationLiquidGlassHook;
import com.hellovoid.liquidui.target.FrameworkPackageVersionReader;
import com.hellovoid.liquidui.target.SystemUiRuntimeInfo;
import com.hellovoid.liquidui.target.SystemUiRuntimeInfoProvider;
import com.hellovoid.liquidui.target.SystemUiTargetResolver;
import com.hellovoid.liquidui.target.TargetResolution;
import com.hellovoid.liquidui.target.TargetResolutionStatus;
import com.hellovoid.liquidui.xposed.Api101AfterMethodHookBackend;
import com.hellovoid.liquidui.xposed.Api101BeforeMethodHookBackend;

import java.util.List;

import io.github.libxposed.api.XposedModule;

/** libxposed API 101 composition root for the exact SystemUI target. */
public final class ModuleMain extends XposedModule {
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";

    private final SystemUiTargetResolver targetResolver = SystemUiTargetResolver.defaults();
    private final SystemUiRuntimeInfoProvider runtimeInfoProvider =
            new SystemUiRuntimeInfoProvider(FrameworkPackageVersionReader.INSTANCE);

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        Api101Bridge.init(this);
        Api101Bridge.log(LiquidUiLog.format("module loaded process=" + param.getProcessName()
                + " framework=" + getFrameworkName() + " api=" + getApiVersion()));
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String packageName = param.getPackageName();
        if (!SYSTEM_UI_PACKAGE.equals(packageName)) return;

        try {
            SharedPreferences preferences = Api101Bridge.remotePreferences("config");
            ConfigReader configReader = new ConfigReader(preferences::getBoolean);
            LiquidUiConfig config = LiquidUiConfig.from(configReader);
            if (!config.enabled()) {
                Api101Bridge.log(LiquidUiLog.format("bootstrap disabled by configuration"));
                return;
            }

            ClassLoader classLoader = param.getClassLoader();
            SystemUiRuntimeInfo runtimeInfo = runtimeInfoProvider.read(packageName, Build.VERSION.SDK_INT);
            TargetResolution resolution = targetResolver.resolve(runtimeInfo, classLoader);
            if (resolution.status() != TargetResolutionStatus.SUPPORTED) {
                String message = LiquidUiLog.format(
                        BootstrapDiagnosticsPolicy.targetResolutionMessage(
                                resolution, config.diagnosticsEnabled()));
                if (resolution.hasError()) {
                    Api101Bridge.log(message, resolution.error());
                } else {
                    Api101Bridge.log(message);
                }
                return;
            }

            SystemUiHookRegistry hookRegistry = new SystemUiHookRegistry(List.of(
                    new NotificationLiquidGlassHook(
                            new Api101BeforeMethodHookBackend(config.diagnosticsEnabled()),
                            new Api101AfterMethodHookBackend(config.diagnosticsEnabled()),
                            config.notificationGlassEnabled())));
            HookRegistryReport report = hookRegistry.installAll(classLoader, resolution.profile());
            Api101Bridge.log(LiquidUiLog.format(
                    BootstrapDiagnosticsPolicy.hookRegistryMessage(
                            resolution.profile(), report, config.diagnosticsEnabled())));
        } catch (Throwable error) {
            Api101Bridge.log(LiquidUiLog.format("SystemUI bootstrap FAILED"), error);
        }
    }
}
