package com.hellovoid.liquidui.glass.notification;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class FrameworkPassBlurViewConsumerExperimentContractTest {
    @Test
    public void shadeRootFrameworkConsumerRegistrationAndCallbacksAreObservedReadOnly() throws Exception {
        Path hookPath = Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/FrameworkPassBlurViewConsumerHook.java");
        assertTrue(Files.exists(hookPath));
        String hook = Files.readString(hookPath);
        String moduleMain = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/ModuleMain.java"));

        assertTrue(hook.contains("android.view.ViewRootImpl"));
        assertTrue(hook.contains("com.android.systemui.shade.NotificationShadeWindowView"));
        assertTrue(hook.contains("addTextureView"));
        assertTrue(hook.contains("clearTextureView"));
        assertTrue(hook.contains("setTextureAvailable"));
        assertTrue(hook.contains("getRootView()"));
        assertTrue(hook.contains("shadeWindowClass.isInstance"));
        assertTrue(hook.contains("consumer-register"));
        assertTrue(hook.contains("consumer-clear"));
        assertTrue(hook.contains("texture-callback"));
        assertTrue(hook.contains("[NotifGlass][FrameworkPB][Consumer]"));
        assertTrue(hook.contains("available="));
        assertTrue(hook.contains("value="));
        assertTrue(hook.contains("scale="));
        assertTrue(hook.contains("BeforeMethodHookBackend"));
        assertTrue(moduleMain.contains("FrameworkPassBlurViewConsumerHook"));
        assertTrue(moduleMain.contains("new FrameworkPassBlurViewConsumerHook("));

        // Probe only. It may observe these framework methods but must never invoke/register them.
        assertFalse(hook.contains("ArgumentRewriteHookBackend"));
        assertFalse(hook.contains("addTextureView.invoke"));
        assertFalse(hook.contains("clearTextureView.invoke"));
        assertFalse(hook.contains("setTextureAvailable.invoke"));
        assertFalse(hook.contains("setPassWindowBlurEnabled.invoke"));
    }

    @Test
    public void activeGlassNeutralizesExactShadeBackgroundBlurProvider() throws Exception {
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java"));

        assertTrue(hook.contains("SHADE_BACKGROUND_VIEW"));
        assertTrue(hook.contains("com.miui.systemui.shade.ShadeBackgroundView"));
        assertTrue(hook.contains("shadeBackgroundClass"));
        assertTrue(hook.contains("|| shadeBackgroundClass.isInstance(value)"));
        assertTrue(hook.contains("if (shadeBackgroundClass.isInstance(value)) return true;"));
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
