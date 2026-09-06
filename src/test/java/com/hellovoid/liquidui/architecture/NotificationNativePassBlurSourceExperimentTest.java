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
    public void materialHookMatchesDecompiledSystemUiDescriptor() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");

        // MiuiSystemUI.apk (classes2.dex):
        // MiBlurCompat#setMiBackgroundBlendColors(View, int[], float)
        assertTrue(hook.contains("\"setMiBackgroundBlendColors\", View.class, int[].class, float.class"));
        assertTrue(hook.contains("args.length < 3"));
        assertTrue(hook.contains("args[2] instanceof Number"));
    }

    @Test
    public void rowElementTargetPreservesSystemUiConsumerOwnership() throws Exception {
        String controller = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationVendorMaterialController.java");

        // Decompiled ExpandableNotificationRowInjector#updateBlurBg proves ordinary notification
        // mBackgroundNormal is an element consumer: SystemUI uses viewBlurMode + blend on it.
        // backgroundBlurMode/radius/passWindowBlur are container APIs used elsewhere, not row ownership.
        assertFalse(controller.contains("setMiBackgroundBlurMode"));
        assertFalse(controller.contains("setMiBackgroundBlurRadius"));
        assertFalse(controller.contains("setPassWindowBlurEnabled"));
        assertFalse(controller.contains("clearMiBackgroundBlendColor"));
        assertFalse(controller.contains("setMiViewBlurMode"));
        assertTrue(controller.contains("observeSystemMaterial"));
        assertFalse(controller.contains("takeOver("));
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
