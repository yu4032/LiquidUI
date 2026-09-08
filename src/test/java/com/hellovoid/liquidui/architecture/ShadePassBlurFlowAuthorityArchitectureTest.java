package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/** Guards the exact systemui-001 source of notifPassBlur / ctrlPassBlur. */
public final class ShadePassBlurFlowAuthorityArchitectureTest {
    @Test
    public void aggregateAuthorityObservesVendorStateFlowCollectorDirectly() throws Exception {
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationSharedGlassHook.java"));

        assertTrue(hook.contains(
                "com.miui.systemui.shade.blur.ShadeBlendBlurController$start$6$1$1"));
        assertTrue(hook.contains("$r8$classId"));
        assertTrue(hook.contains("passBlurCollectorEmit"));
        assertTrue(hook.contains("observePassBlurFlowEmission("));
        assertTrue(hook.contains("authorityState.observeControlCenter("));
        assertTrue(hook.contains("authorityState.observeNotification("));
    }
}
