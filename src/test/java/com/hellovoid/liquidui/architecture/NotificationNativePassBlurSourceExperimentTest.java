package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class NotificationNativePassBlurSourceExperimentTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    public void materialDispatchIsTheOnlyActiveNotificationTargetAuthority() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");

        assertTrue(hook.contains("NotificationUtil"));
        assertTrue(hook.contains("applyElementViewBlend"));
        assertTrue(hook.contains("setMiBackgroundBlendColors"));
        assertTrue(hook.contains("NotificationMaterialTargetRegistry"));
        assertTrue(hook.contains("setRoundRect"));
        assertTrue(hook.contains("setChildrenExpanded"));

        assertFalse(hook.contains("onAttachedToWindow"));
        assertFalse(hook.contains("onReinflated"));
        assertFalse(hook.contains("ShadeBlendBlurController"));
        assertFalse(hook.contains("NotificationPassBlurAuthorityState"));
        assertFalse(hook.contains("NotificationGlassRuntime"));
    }

    @Test
    public void exactTargetClearsAllFiveVendorBlurStatesThenUsesViewPassBlur() throws Exception {
        String controller = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationVendorMaterialController.java");

        assertTrue(controller.contains("clearMiBackgroundBlendColor"));
        assertTrue(controller.contains("setMiBackgroundBlurMode"));
        assertTrue(controller.contains("setMiViewBlurMode"));
        assertTrue(controller.contains("setMiBackgroundBlurRadius"));
        assertTrue(controller.contains("setPassWindowBlurEnabled"));
        assertTrue(controller.contains("setPassWindowBlurEnabled.invoke(target, true)"));
        assertFalse(controller.contains("backgroundNormal"));
        assertFalse(controller.contains("background.setAlpha(0f)"));
    }

    @Test
    public void activeHookDoesNotOwnNotificationShadeSurfaceEndpointOrPrismalOesProducer() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        String module = read("src/main/java/com/hellovoid/liquidui/ModuleMain.java");

        assertFalse(hook.contains("SystemUiPassBlurBridge"));
        assertFalse(hook.contains("NotificationPassBlurTextureView"));
        assertFalse(hook.contains("SetPassBlurSurface"));
        assertFalse(hook.contains("NotificationShadeWindowView"));
        assertTrue(module.contains("Api101AfterMethodHookBackend"));
    }

    @Test
    public void roundStateComesFromSystemUiFinalRoundDispatch() throws Exception {
        String collector = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassNodeCollector.java");
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");

        assertTrue(collector.contains("NotificationMaterialTargetRegistry"));
        assertTrue(collector.contains("roundState"));
        assertTrue(hook.contains("observeRoundRect"));
    }
}
