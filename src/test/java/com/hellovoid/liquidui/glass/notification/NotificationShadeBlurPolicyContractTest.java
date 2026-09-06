package com.hellovoid.liquidui.glass.notification;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

public class NotificationShadeBlurPolicyContractTest {
    private static Class<?> policyClass() throws Exception {
        Class<?> value = null;
        try { value = Class.forName("com.hellovoid.liquidui.glass.notification.NotificationShadeBlurPolicy"); }
        catch (ClassNotFoundException ignored) {}
        assertNotNull(value);
        return value;
    }

    private static Class<?> stateClass() throws Exception {
        Class<?> value = null;
        try { value = Class.forName("com.hellovoid.liquidui.glass.notification.NotificationGlassActivityState"); }
        catch (ClassNotFoundException ignored) {}
        assertNotNull(value);
        return value;
    }

    @Test
    public void activeGlassNeutralizesOnlyRequestedShadeBlurValues() throws Exception {
        Class<?> type = policyClass();
        Method ratio = type.getDeclaredMethod("blurRatio", boolean.class, float.class);
        Method radius = type.getDeclaredMethod("blurRadius", boolean.class, int.class);
        Method enabled = type.getDeclaredMethod("enabled", boolean.class, boolean.class);
        ratio.setAccessible(true);
        radius.setAccessible(true);
        enabled.setAccessible(true);

        assertEquals(Float.valueOf(0f), ratio.invoke(null, true, 0.82f));
        assertEquals(0L, ((Integer) radius.invoke(null, true, 96)).longValue());
        assertEquals(Boolean.FALSE, enabled.invoke(null, true, true));

        assertEquals(Float.valueOf(0.82f), ratio.invoke(null, false, 0.82f));
        assertEquals(96L, ((Integer) radius.invoke(null, false, 96)).longValue());
        assertEquals(Boolean.TRUE, enabled.invoke(null, false, true));
    }

    @Test
    public void activityStateIsReferenceCountedAcrossShadeSessions() throws Exception {
        Class<?> type = stateClass();
        Object state = type.getDeclaredConstructor().newInstance();
        Method activate = type.getDeclaredMethod("activate");
        Method deactivate = type.getDeclaredMethod("deactivate");
        Method active = type.getDeclaredMethod("isActive");
        activate.setAccessible(true);
        deactivate.setAccessible(true);
        active.setAccessible(true);

        assertEquals(Boolean.FALSE, active.invoke(state));
        activate.invoke(state);
        activate.invoke(state);
        assertEquals(Boolean.TRUE, active.invoke(state));
        deactivate.invoke(state);
        assertEquals(Boolean.TRUE, active.invoke(state));
        deactivate.invoke(state);
        assertEquals(Boolean.FALSE, active.invoke(state));
        // Extra release must fail closed instead of going negative.
        deactivate.invoke(state);
        assertEquals(Boolean.FALSE, active.invoke(state));
    }
}
