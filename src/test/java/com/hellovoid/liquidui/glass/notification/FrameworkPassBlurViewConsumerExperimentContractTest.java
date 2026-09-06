package com.hellovoid.liquidui.glass.notification;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class FrameworkPassBlurViewConsumerExperimentContractTest {
    @Test
    public void sharedRendererUsesFrameworkViewConsumerWithoutOwningShadeProducer() throws Exception {
        Path helperPath = Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/FrameworkPassBlurViewConsumerExperiment.java");
        assertTrue(Files.exists(helperPath));
        String helper = Files.readString(helperPath);
        String renderer = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationPassBlurTextureView.java"));

        assertTrue(helper.contains("setPassTextureScale"));
        assertTrue(helper.contains("setPassWindowBlurEnabled"));
        assertTrue(helper.contains("FRAMEWORK_TEXTURE_SCALE"));
        assertTrue(renderer.contains("FrameworkPassBlurViewConsumerExperiment.enable(this)"));
        assertTrue(renderer.contains("FrameworkPassBlurViewConsumerExperiment.disable(this)"));

        // A2 is deliberately non-owning: the notification renderer must stop supplying a Surface
        // to NotificationShade's PassBlur producer endpoint.
        assertFalse(renderer.contains("SystemUiPassBlurBridge.bind("));
        assertFalse(renderer.contains("SystemUiPassBlurBridge.resumeUpdates("));
        assertFalse(renderer.contains("SystemUiPassBlurBridge.pauseUpdates("));
    }

    @Test
    public void frameworkTextureAvailabilityIsObservedOnlyForLiquidUiRenderer() throws Exception {
        Path hookPath = Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/FrameworkPassBlurViewConsumerHook.java");
        assertTrue(Files.exists(hookPath));
        String hook = Files.readString(hookPath);

        assertTrue(hook.contains("setTextureAvailable"));
        assertTrue(hook.contains("NotificationPassBlurTextureView"));
        assertTrue(hook.contains("[NotifGlass][FrameworkPB][Consumer]"));
        assertTrue(hook.contains("available="));
        assertTrue(hook.contains("value="));
        assertTrue(hook.contains("scale="));
        assertTrue(hook.contains("BeforeMethodHookBackend"));
        assertFalse(hook.contains("ArgumentRewriteHookBackend"));
    }

    @Test
    public void activeNotificationGlassNeutralizesNsslBlurRadius() throws Exception {
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java"));
        assertTrue(hook.contains("setBlurRadius"));
        assertTrue(hook.contains("stackBlurRadius"));
        assertTrue(hook.contains("if (!activityState.isActive()"));
        assertTrue(hook.contains("args[0] = NotificationShadeBlurPolicy.blurRatio(true, radius)"));
    }
}
