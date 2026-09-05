package com.hellovoid.liquidui.target;

import org.junit.Test;

import static org.junit.Assert.*;

public class SystemUiRuntimeInfoProviderContractTest {
    @Test
    public void providerUsesPackageVersionAuthorityWithoutInventingMetadata() throws Exception {
        PackageVersionReader reader = packageName -> {
            assertEquals("com.android.systemui", packageName);
            return new PackageVersion(202501210L, "16.03.251211.r");
        };
        SystemUiRuntimeInfo runtime = new SystemUiRuntimeInfoProvider(reader)
                .read("com.android.systemui", 36);
        assertEquals("com.android.systemui", runtime.packageName());
        assertEquals(202501210L, runtime.versionCode());
        assertEquals("16.03.251211.r", runtime.versionName());
        assertEquals(36L, runtime.sdkInt());
    }

    @Test
    public void providerPropagatesMetadataAuthorityFailure() {
        Exception expected = new Exception("package manager unavailable");
        PackageVersionReader reader = packageName -> { throw expected; };
        boolean threw = false;
        try {
            new SystemUiRuntimeInfoProvider(reader).read("com.android.systemui", 36);
        } catch (Exception actual) {
            assertEquals(expected, actual);
            threw = true;
        }
        assertTrue(threw);
    }
}
