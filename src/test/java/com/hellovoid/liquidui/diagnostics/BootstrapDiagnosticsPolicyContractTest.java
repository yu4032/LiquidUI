package com.hellovoid.liquidui.diagnostics;

import com.hellovoid.liquidui.hook.HookRegistryReport;
import com.hellovoid.liquidui.hook.SystemUiHookRegistry;
import com.hellovoid.liquidui.target.SystemUiRuntimeInfo;
import com.hellovoid.liquidui.target.SystemUiTargetResolver;
import com.hellovoid.liquidui.target.TargetResolution;
import com.hellovoid.liquidui.target.profiles.SystemUi001Profile;

import org.junit.Test;

import static org.junit.Assert.*;

public class BootstrapDiagnosticsPolicyContractTest {
    @Test
    public void unsupportedTargetHidesDetailedReasonWhenDiagnosticsAreOff() {
        TargetResolution resolution = SystemUiTargetResolver.defaults().resolve(
                new SystemUiRuntimeInfo("com.android.systemui", 1L, "wrong", 36),
                getClass().getClassLoader());

        String message = BootstrapDiagnosticsPolicy.targetResolutionMessage(resolution, false);

        assertTrue(message.contains("UNSUPPORTED"));
        assertFalse(message.contains(resolution.detail()));
    }

    @Test
    public void unsupportedTargetIncludesDetailedReasonWhenDiagnosticsAreOn() {
        TargetResolution resolution = SystemUiTargetResolver.defaults().resolve(
                new SystemUiRuntimeInfo("com.android.systemui", 1L, "wrong", 36),
                getClass().getClassLoader());

        String message = BootstrapDiagnosticsPolicy.targetResolutionMessage(resolution, true);

        assertTrue(message.contains("UNSUPPORTED"));
        assertTrue(message.contains(resolution.detail()));
    }

    @Test
    public void successfulRegistrySummaryIsCompactWithoutDiagnostics() {
        HookRegistryReport report = SystemUiHookRegistry.empty().installAll(
                getClass().getClassLoader(), SystemUi001Profile.INSTANCE);

        String message = BootstrapDiagnosticsPolicy.hookRegistryMessage(
                SystemUi001Profile.INSTANCE, report, false);

        assertEquals("SystemUI bootstrap installed", message);
    }

    @Test
    public void successfulRegistrySummaryIncludesTargetAndCountsWithDiagnostics() {
        HookRegistryReport report = SystemUiHookRegistry.empty().installAll(
                getClass().getClassLoader(), SystemUi001Profile.INSTANCE);

        String message = BootstrapDiagnosticsPolicy.hookRegistryMessage(
                SystemUi001Profile.INSTANCE, report, true);

        assertTrue(message.contains("target=systemui-001"));
        assertTrue(message.contains("hooks=0"));
        assertTrue(message.contains("failed=false"));
    }
}
