package com.hellovoid.liquidui.hook;

import com.hellovoid.liquidui.target.SystemUiTargetProfile;
import com.hellovoid.liquidui.target.profiles.SystemUi001Profile;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class SystemUiHookRegistryContractTest {
    private static final SystemUiTargetProfile PROFILE = SystemUi001Profile.INSTANCE;
    private static final ClassLoader LOADER = SystemUiHookRegistryContractTest.class.getClassLoader();

    @Test
    public void registryPreservesEveryInstallStatus() {
        SystemUiHookRegistry registry = new SystemUiHookRegistry(List.of(
                hook("installed", HookInstallResult.installed("installed")),
                hook("unsupported", HookInstallResult.unsupported("unsupported", "contract absent")),
                hook("failed", HookInstallResult.failed("failed", "install failed", new IllegalStateException("boom"))),
                hook("disabled", HookInstallResult.disabled("disabled"))));

        HookRegistryReport report = registry.installAll(LOADER, PROFILE);

        assertEquals(HookInstallStatus.INSTALLED, report.result("installed").status());
        assertEquals(HookInstallStatus.UNSUPPORTED, report.result("unsupported").status());
        assertEquals(HookInstallStatus.FAILED, report.result("failed").status());
        assertEquals(HookInstallStatus.DISABLED, report.result("disabled").status());
        assertTrue(report.hasFailures());
        assertFalse(report.isCleanSuccess());
    }

    @Test
    public void thrownHookFailureIsIsolatedAndLaterHooksStillRun() {
        final boolean[] laterInstalled = {false};
        SystemUiHook throwing = new SystemUiHook() {
            @Override public String id() { return "throwing"; }
            @Override public HookInstallResult install(ClassLoader classLoader, SystemUiTargetProfile profile) {
                throw new IllegalArgumentException("unexpected");
            }
        };
        SystemUiHook later = new SystemUiHook() {
            @Override public String id() { return "later"; }
            @Override public HookInstallResult install(ClassLoader classLoader, SystemUiTargetProfile profile) {
                laterInstalled[0] = true;
                return HookInstallResult.installed(id());
            }
        };

        HookRegistryReport report = new SystemUiHookRegistry(List.of(throwing, later))
                .installAll(LOADER, PROFILE);

        assertEquals(HookInstallStatus.FAILED, report.result("throwing").status());
        assertTrue(report.result("throwing").hasError());
        assertEquals(HookInstallStatus.INSTALLED, report.result("later").status());
        assertTrue(laterInstalled[0]);
        assertTrue(report.hasFailures());
    }

    @Test
    public void duplicateHookIdsAreRejectedAtConstruction() {
        boolean threw = false;
        try {
            new SystemUiHookRegistry(List.of(
                    hook("same", HookInstallResult.installed("same")),
                    hook("same", HookInstallResult.disabled("same"))));
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        assertTrue(threw);
    }

    @Test
    public void allInstalledOrDisabledIsCleanSuccess() {
        HookRegistryReport report = new SystemUiHookRegistry(List.of(
                hook("one", HookInstallResult.installed("one")),
                hook("two", HookInstallResult.disabled("two"))))
                .installAll(LOADER, PROFILE);
        assertFalse(report.hasFailures());
        assertTrue(report.isCleanSuccess());
    }

    private static SystemUiHook hook(String id, HookInstallResult result) {
        return new SystemUiHook() {
            @Override public String id() { return id; }
            @Override public HookInstallResult install(ClassLoader classLoader, SystemUiTargetProfile profile) {
                return result;
            }
        };
    }
}
