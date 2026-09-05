package com.hellovoid.liquidui.target;

import org.junit.Test;

import static org.junit.Assert.*;

public class StructuralProbeContractTest {
    public static final class Fixture {
        public void ready() {}
    }

    @Test
    public void requiredMethodProbeAcceptsExactMethodSignature() throws Throwable {
        StructuralProbe probe = new RequiredMethodProbe(
                Fixture.class.getName(), "ready", new Class<?>[0]);
        assertTrue(probe.isSatisfied(getClass().getClassLoader()));
    }

    @Test
    public void requiredMethodProbeRejectsMissingMethod() throws Throwable {
        StructuralProbe probe = new RequiredMethodProbe(
                Fixture.class.getName(), "missing", new Class<?>[0]);
        assertFalse(probe.isSatisfied(getClass().getClassLoader()));
    }

    @Test
    public void requiredMethodProbeNameDescribesContract() {
        StructuralProbe probe = new RequiredMethodProbe(
                "com.android.systemui.SystemUIApplication", "onCreate", new Class<?>[0]);
        assertEquals("com.android.systemui.SystemUIApplication#onCreate()", probe.name());
    }
}
