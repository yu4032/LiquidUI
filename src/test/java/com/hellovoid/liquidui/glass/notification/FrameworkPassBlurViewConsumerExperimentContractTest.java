package com.hellovoid.liquidui.glass.notification;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class FrameworkPassBlurViewConsumerExperimentContractTest {
    @Test
    public void exactShadeBackgroundConsumerIsObservedWithoutCreatingSecondFrameworkConsumer() throws Exception {
        Path hookPath = Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/FrameworkPassBlurViewConsumerHook.java");
        assertTrue(Files.exists(hookPath));
        String hook = Files.readString(hookPath);

        assertTrue(hook.contains("com.miui.systemui.shade.ShadeBackgroundView"));
        assertTrue(hook.contains("setTextureAvailable"));
        assertTrue(hook.contains("[NotifGlass][FrameworkPB][Consumer]"));
        assertTrue(hook.contains("available="));
        assertTrue(hook.contains("value="));
        assertTrue(hook.contains("scale="));
        assertTrue(hook.contains("getPassWindowBlurEnabled"));
        assertTrue(hook.contains("getPassTextureScale"));
        assertTrue(hook.contains("BeforeMethodHookBackend"));
        assertFalse(hook.contains("ArgumentRewriteHookBackend"));
        assertFalse(hook.contains("addTextureView"));
        assertFalse(hook.contains("setPassWindowBlurEnabled.invoke"));
    }

    @Test
    public void activeGlassNeutralizesExactShadeBackgroundBlurProvider() throws Exception {
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java"));

        assertTrue(hook.contains("SHADE_BACKGROUND_VIEW"));
        assertTrue(hook.contains("com.miui.systemui.shade.ShadeBackgroundView"));
        assertTrue(hook.contains("shadeBackgroundClass"));
        assertTrue(hook.contains("isShadeBlurTarget(target, shadeWindowClass, notificationPanelClass, shadeBackgroundClass)"));
        assertTrue(hook.contains("isShadeBlendTarget(target, shadeWindowClass, notificationPanelClass, shadeBackgroundClass)"));
    }

    @Test
    public void activeNotificationGlassNeutralizesNsslRenderEffectBlurRadius() throws Exception {
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java"));
        assertTrue(hook.contains("setBlurRadius"));
        assertTrue(hook.contains("stackBlurRadius"));
        assertTrue(hook.contains("if (!activityState.isActive()"));
        assertTrue(hook.contains("args[0] = NotificationShadeBlurPolicy.blurRatio(true, radius)"));
    }

    @Test
    public void experimentDoesNotPretendFrameworkTextureIsPrismalSampleableYet() throws Exception {
        String renderer = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationPassBlurTextureView.java"));
        String consumer = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/FrameworkPassBlurViewConsumerHook.java"));

        // Until the setTextureAvailable value/context semantics are proven on-device, keep the
        // existing renderer path untouched rather than feeding an unverified integer into GLES.
        assertFalse(consumer.contains("glBindTexture"));
        assertFalse(consumer.contains("GLES20"));
        assertTrue(renderer.contains("SystemUiPassBlurBridge.bind("));
    }
}
