package com.hellovoid.liquidui.target;

import com.hellovoid.liquidui.target.profiles.SystemUi001Profile;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class SystemUiTargetResolverContractTest {
    private static final SystemUiRuntimeInfo EXACT = new SystemUiRuntimeInfo(
            "com.android.systemui", 202501210L, "16.03.251211.r", 36);

    @Test
    public void systemUi001ProfileExposesVerifiedIdentity() {
        SystemUiTargetProfile profile = SystemUi001Profile.INSTANCE;
        assertEquals("systemui-001", profile.id());
        assertEquals("com.android.systemui", profile.packageName());
        assertEquals(202501210L, profile.versionCode());
        assertEquals("16.03.251211.r", profile.versionName());
        assertEquals(36L, profile.sdkInt());
    }

    @Test
    public void exactMetadataAndPassingProbeIsSupported() {
        SystemUiTargetProfile profile = fakeProfile(List.of(
                new StructuralProbe("present") {
                    @Override public boolean isSatisfied(ClassLoader classLoader) { return true; }
                }));
        TargetResolution result = new SystemUiTargetResolver(List.of(profile))
                .resolve(EXACT, getClass().getClassLoader());
        assertEquals(TargetResolutionStatus.SUPPORTED, result.status());
        assertEquals(profile, result.profile());
        assertFalse(result.hasError());
    }

    @Test
    public void anyMetadataMismatchIsUnsupported() {
        SystemUiTargetResolver resolver = new SystemUiTargetResolver(List.of(fakeProfile(List.of())));
        assertEquals(TargetResolutionStatus.UNSUPPORTED,
                resolver.resolve(new SystemUiRuntimeInfo("wrong", 202501210L, "16.03.251211.r", 36), getClass().getClassLoader()).status());
        assertEquals(TargetResolutionStatus.UNSUPPORTED,
                resolver.resolve(new SystemUiRuntimeInfo("com.android.systemui", 1L, "16.03.251211.r", 36), getClass().getClassLoader()).status());
        assertEquals(TargetResolutionStatus.UNSUPPORTED,
                resolver.resolve(new SystemUiRuntimeInfo("com.android.systemui", 202501210L, "wrong", 36), getClass().getClassLoader()).status());
        assertEquals(TargetResolutionStatus.UNSUPPORTED,
                resolver.resolve(new SystemUiRuntimeInfo("com.android.systemui", 202501210L, "16.03.251211.r", 35), getClass().getClassLoader()).status());
    }

    @Test
    public void missingStructuralProbeIsUnsupported() {
        SystemUiTargetProfile profile = fakeProfile(List.of(
                new StructuralProbe("missing") {
                    @Override public boolean isSatisfied(ClassLoader classLoader) { return false; }
                }));
        TargetResolution result = new SystemUiTargetResolver(List.of(profile))
                .resolve(EXACT, getClass().getClassLoader());
        assertEquals(TargetResolutionStatus.UNSUPPORTED, result.status());
        assertTrue(result.detail().contains("missing"));
    }

    @Test
    public void probeInfrastructureFailureIsFailed() {
        RuntimeException expected = new RuntimeException("linkage failure");
        SystemUiTargetProfile profile = fakeProfile(List.of(
                new StructuralProbe("throws") {
                    @Override public boolean isSatisfied(ClassLoader classLoader) { throw expected; }
                }));
        TargetResolution result = new SystemUiTargetResolver(List.of(profile))
                .resolve(EXACT, getClass().getClassLoader());
        assertEquals(TargetResolutionStatus.FAILED, result.status());
        assertEquals(expected, result.error());
        assertTrue(result.hasError());
    }

    private static SystemUiTargetProfile fakeProfile(List<StructuralProbe> probes) {
        return new SystemUiTargetProfile() {
            @Override public String id() { return "fake"; }
            @Override public String packageName() { return EXACT.packageName(); }
            @Override public long versionCode() { return EXACT.versionCode(); }
            @Override public String versionName() { return EXACT.versionName(); }
            @Override public int sdkInt() { return EXACT.sdkInt(); }
            @Override public List<StructuralProbe> structuralProbes() { return probes; }
        };
    }
}
