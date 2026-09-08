package com.hellovoid.liquidui;

import android.content.SharedPreferences;
import android.os.Build;

import com.hellovoid.liquidui.config.ConfigReader;
import com.hellovoid.liquidui.config.GlassConfigRuntime;
import com.hellovoid.liquidui.config.GlassStyleConfig;
import com.hellovoid.liquidui.config.LiquidUiConfig;
import com.hellovoid.liquidui.diagnostics.BootstrapDiagnosticsPolicy;
import com.hellovoid.liquidui.diagnostics.LiquidUiLog;
import com.hellovoid.liquidui.glass.controlcenter.ControlCenterGlassHook;
import com.hellovoid.liquidui.glass.core.SystemUiGlassCore;
import com.hellovoid.liquidui.glass.core.WindowGlassSession;
import com.hellovoid.liquidui.glass.keyguard.KeyguardGlassHook;
import com.hellovoid.liquidui.glass.media.MediaGlassHook;
import com.hellovoid.liquidui.glass.media.MediaOutputDialogGlassHook;
import com.hellovoid.liquidui.glass.notification.NotificationSharedGlassHook;
import com.hellovoid.liquidui.glass.plugin.MiuiControlCenterMediaPluginGlassSession;
import com.hellovoid.liquidui.glass.plugin.MiuiControlCenterPluginGlassSession;
import com.hellovoid.liquidui.glass.plugin.MiuiSecondaryPanelPluginGlassSession;
import com.hellovoid.liquidui.glass.plugin.MiuiSystemUiPluginGlassHook;
import com.hellovoid.liquidui.glass.plugin.MiuiVolumePluginGlassSession;
import com.hellovoid.liquidui.hook.HookRegistryReport;
import com.hellovoid.liquidui.hook.SystemUiHookRegistry;
import com.hellovoid.liquidui.target.FrameworkPackageVersionReader;
import com.hellovoid.liquidui.target.SystemUiRuntimeInfo;
import com.hellovoid.liquidui.target.SystemUiRuntimeInfoProvider;
import com.hellovoid.liquidui.target.SystemUiTargetResolver;
import com.hellovoid.liquidui.target.TargetResolution;
import com.hellovoid.liquidui.target.TargetResolutionStatus;
import com.hellovoid.liquidui.xposed.Api101AfterMethodHookBackend;
import com.hellovoid.liquidui.xposed.Api101ArgumentRewriteHookBackend;
import com.hellovoid.liquidui.xposed.Api101BeforeMethodHookBackend;

import java.util.List;

import io.github.libxposed.api.XposedModule;

/** libxposed API 101 composition root for the exact SystemUI target. */
public final class ModuleMain extends XposedModule {
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";

    private final SystemUiTargetResolver targetResolver = SystemUiTargetResolver.defaults();
    private final SystemUiRuntimeInfoProvider runtimeInfoProvider =
            new SystemUiRuntimeInfoProvider(FrameworkPackageVersionReader.INSTANCE);
    private SystemUiGlassCore glassCore;
    private GlassConfigRuntime glassConfigRuntime;

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
            ConfigReader configReader = new ConfigReader(
                    preferences::getBoolean, preferences::getInt);
            LiquidUiConfig config = LiquidUiConfig.from(configReader);
            if (!config.enabled()) {
                closeGlassConfigRuntime();
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

            if (glassCore == null || glassCore.isClosed()) {
                glassCore = new SystemUiGlassCore(
                        (key, renderHandler) -> new WindowGlassSession(key, renderHandler));
            }
            SystemUiGlassCore processGlassCore = glassCore;
            GlassStyleConfig initialStyle = GlassStyleConfig.read(configReader);
            processGlassCore.updateGlassStyles(initialStyle);

            closeGlassConfigRuntime();
            glassConfigRuntime = new GlassConfigRuntime(
                    preferences,
                    processGlassCore::updateGlassStyles);

            SystemUiHookRegistry hookRegistry = new SystemUiHookRegistry(List.of(
                    new NotificationSharedGlassHook(
                            new Api101BeforeMethodHookBackend(config.diagnosticsEnabled()),
                            new Api101AfterMethodHookBackend(config.diagnosticsEnabled()),
                            new Api101ArgumentRewriteHookBackend(config.diagnosticsEnabled()),
                            processGlassCore,
                            config.notificationGlassEnabled()),
                    new ControlCenterGlassHook(
                            new Api101AfterMethodHookBackend(config.diagnosticsEnabled()),
                            processGlassCore),
                    new KeyguardGlassHook(
                            new Api101AfterMethodHookBackend(config.diagnosticsEnabled()),
                            processGlassCore),
                    new MediaGlassHook(
                            new Api101AfterMethodHookBackend(config.diagnosticsEnabled()),
                            processGlassCore),
                    new MediaOutputDialogGlassHook(
                            new Api101AfterMethodHookBackend(config.diagnosticsEnabled()),
                            processGlassCore),
                    new MiuiSystemUiPluginGlassHook(
                            new Api101BeforeMethodHookBackend(config.diagnosticsEnabled()),
                            new Api101AfterMethodHookBackend(config.diagnosticsEnabled()),
                            (pluginClassLoader, pluginContext) -> {
                                Api101AfterMethodHookBackend pluginAfter =
                                        new Api101AfterMethodHookBackend(config.diagnosticsEnabled());
                                Api101BeforeMethodHookBackend pluginBefore =
                                        new Api101BeforeMethodHookBackend(config.diagnosticsEnabled());
                                MiuiControlCenterPluginGlassSession controlCenter =
                                        MiuiControlCenterPluginGlassSession.install(
                                                pluginClassLoader,
                                                pluginContext,
                                                processGlassCore,
                                                pluginAfter);
                                try {
                                    MiuiSecondaryPanelPluginGlassSession secondaryPanels =
                                            MiuiSecondaryPanelPluginGlassSession.install(
                                                    pluginClassLoader,
                                                    pluginContext,
                                                    processGlassCore,
                                                    pluginAfter);
                                    try {
                                        MiuiControlCenterMediaPluginGlassSession media =
                                                MiuiControlCenterMediaPluginGlassSession.install(
                                                        pluginClassLoader,
                                                        pluginContext,
                                                        processGlassCore,
                                                        pluginAfter);
                                        try {
                                            MiuiVolumePluginGlassSession volume =
                                                    MiuiVolumePluginGlassSession.install(
                                                            pluginClassLoader,
                                                            pluginContext,
                                                            processGlassCore,
                                                            pluginBefore,
                                                            pluginAfter);
                                            return () -> {
                                                volume.close();
                                                media.close();
                                                secondaryPanels.close();
                                                controlCenter.close();
                                            };
                                        } catch (Throwable error) {
                                            media.close();
                                            throw error;
                                        }
                                    } catch (Throwable error) {
                                        secondaryPanels.close();
                                        throw error;
                                    }
                                } catch (Throwable error) {
                                    controlCenter.close();
                                    throw error;
                                }
                            })));
            HookRegistryReport report = hookRegistry.installAll(classLoader, resolution.profile());
            if (report.hasFailures()) {
                closeGlassConfigRuntime();
                processGlassCore.close();
                if (glassCore == processGlassCore) glassCore = null;
            }
            Api101Bridge.log(LiquidUiLog.format(
                    BootstrapDiagnosticsPolicy.hookRegistryMessage(
                            resolution.profile(), report, config.diagnosticsEnabled())));
        } catch (Throwable error) {
            closeGlassConfigRuntime();
            SystemUiGlassCore failedCore = glassCore;
            glassCore = null;
            if (failedCore != null) {
                try { failedCore.close(); } catch (Throwable ignored) {}
            }
            Api101Bridge.log(LiquidUiLog.format("SystemUI bootstrap FAILED"), error);
        }
    }

    private void closeGlassConfigRuntime() {
        GlassConfigRuntime runtime = glassConfigRuntime;
        glassConfigRuntime = null;
        if (runtime != null) {
            try { runtime.close(); } catch (Throwable ignored) {}
        }
    }
}
