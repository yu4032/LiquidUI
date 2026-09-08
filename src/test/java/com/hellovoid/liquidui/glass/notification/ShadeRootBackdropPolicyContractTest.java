package com.hellovoid.liquidui.glass.notification;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

/** Contract for restoring only the Window-level Shade backdrop while page-local backdrops stay clear. */
public class ShadeRootBackdropPolicyContractTest {
    private static Class<?> policyClass() throws Exception {
        Class<?> value = null;
        try {
            value = Class.forName(
                    "com.hellovoid.liquidui.glass.notification.NotificationShadeBlurPolicy");
        } catch (ClassNotFoundException ignored) {}
        assertNotNull(value);
        return value;
    }

    @Test
    public void rootBackdropPreservesHyperOsRequestedMaterial() throws Exception {
        Class<?> type = policyClass();
        Method ratio = type.getDeclaredMethod("rootBlurRatio", float.class);
        Method radius = type.getDeclaredMethod("rootWindowBlurRadius", int.class);
        Method enabled = type.getDeclaredMethod("rootBlendEnabled", boolean.class);
        ratio.setAccessible(true);
        radius.setAccessible(true);
        enabled.setAccessible(true);

        assertEquals(Float.valueOf(0.82f), ratio.invoke(null, 0.82f));
        assertEquals(96L, ((Integer) radius.invoke(null, 96)).longValue());
        assertEquals(Boolean.TRUE, enabled.invoke(null, true));
        assertEquals(Boolean.FALSE, enabled.invoke(null, false));
    }

    @Test
    public void pageLocalBackdropRemainsTransparentAboveSharedGlass() throws Exception {
        Class<?> type = policyClass();
        Method ratio = type.getDeclaredMethod("pageBlurRatio", float.class);
        Method enabled = type.getDeclaredMethod("pageBlendEnabled", boolean.class);
        ratio.setAccessible(true);
        enabled.setAccessible(true);

        assertEquals(Float.valueOf(0f), ratio.invoke(null, 0.82f));
        assertEquals(Boolean.FALSE, enabled.invoke(null, true));
        assertEquals(Boolean.FALSE, enabled.invoke(null, false));
    }
}
