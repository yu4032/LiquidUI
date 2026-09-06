package com.hellovoid.liquidui.hook.systemui.notification;

import org.junit.Test;

import static org.junit.Assert.*;

public class NotificationRedBackgroundPolicyContractTest {
    @Test
    public void opaqueRedConstantIsExactArgbRed() {
        assertEquals((long) 0xFFFF0000,
                (long) NotificationRedBackgroundPolicy.OPAQUE_RED);
    }

    @Test
    public void rewriteTintAlwaysReturnsOpaqueRed() {
        assertEquals((long) 0xFFFF0000,
                (long) NotificationRedBackgroundPolicy.rewriteTint(0));
        assertEquals((long) 0xFFFF0000,
                (long) NotificationRedBackgroundPolicy.rewriteTint(0x55FFFFFF));
        assertEquals((long) 0xFFFF0000,
                (long) NotificationRedBackgroundPolicy.rewriteTint(0xFF00FF00));
    }
}
