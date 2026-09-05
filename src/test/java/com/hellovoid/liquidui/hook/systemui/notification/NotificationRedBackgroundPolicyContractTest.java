package com.hellovoid.liquidui.hook.systemui.notification;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

public class NotificationRedBackgroundPolicyContractTest {
    private static final String POLICY =
            "com.hellovoid.liquidui.hook.systemui.notification.NotificationRedBackgroundPolicy";

    @Test
    public void opaqueRedConstantIsExactArgbRed() throws Exception {
        Class<?> type = Class.forName(POLICY);
        Field field = type.getField("OPAQUE_RED");
        assertEquals((long) 0xFFFF0000, ((Integer) field.get(null)).longValue());
    }

    @Test
    public void rewriteTintAlwaysReturnsOpaqueRed() throws Exception {
        Class<?> type = Class.forName(POLICY);
        Method method = type.getMethod("rewriteTint", int.class);
        assertEquals((long) 0xFFFF0000, ((Integer) method.invoke(null, 0)).longValue());
        assertEquals((long) 0xFFFF0000, ((Integer) method.invoke(null, 0x55FFFFFF)).longValue());
        assertEquals((long) 0xFFFF0000, ((Integer) method.invoke(null, 0xFF00FF00)).longValue());
    }
}
