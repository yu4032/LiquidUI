package com.hellovoid.liquidui.diagnostics;

import com.hellovoid.liquidui.hook.HookRegistryReport;
import com.hellovoid.liquidui.target.SystemUiTargetProfile;
import com.hellovoid.liquidui.target.TargetResolution;

import java.util.Objects;

/** Controls bootstrap log detail without hiding the authoritative install status. */
public final class BootstrapDiagnosticsPolicy {
    private BootstrapDiagnosticsPolicy() {}

    public static String targetResolutionMessage(
            TargetResolution resolution, boolean diagnosticsEnabled) {
        Objects.requireNonNull(resolution, "resolution");
        String message = "target resolution " + resolution.status();
        if (diagnosticsEnabled && resolution.detail() != null && !resolution.detail().isEmpty()) {
            message += ": " + resolution.detail();
        }
        return message;
    }

    public static String hookRegistryMessage(
            SystemUiTargetProfile profile,
            HookRegistryReport report,
            boolean diagnosticsEnabled) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(report, "report");
        if (!diagnosticsEnabled) {
            return report.hasFailures()
                    ? "SystemUI bootstrap installed with hook failures"
                    : "SystemUI bootstrap installed";
        }
        return "target=" + profile.id()
                + " hooks=" + report.results().size()
                + " failed=" + report.hasFailures();
    }
}
