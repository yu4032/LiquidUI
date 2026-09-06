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
    public void finalMiBlurSetterAndTargetIdentityAreTheActiveNotificationAuthority() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        assertTrue(hook.contains("setMiBackgroundBlendColors"));
        assertTrue(hook.contains("NotificationBackgroundView"));
        assertTrue(hook.contains("NotificationMaterialTargetRegistry"));
        assertTrue(hook.contains("notificationBackgroundClass.isInstance(target)"));
        assertTrue(hook.contains("setRoundRect"));
        assertTrue(hook.contains("setChildrenExpanded"));
        assertFalse(hook.contains("enterNotificationBlend"));
        assertFalse(hook.contains("inNotificationBlend"));
        assertFalse(hook.contains("onAttachedToWindow"));
        assertFalse(hook.contains("onReinflated"));
        assertFalse(hook.contains("ShadeBlendBlurController"));
        assertFalse(hook.contains("NotificationGlassRuntime"));
    }

    @Test
    public void materialHookMatchesDecompiledSystemUiDescriptor() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        assertTrue(hook.contains("\"setMiBackgroundBlendColors\", View.class, int[].class, float.class"));
        assertTrue(hook.contains("args.length < 3"));
        assertTrue(hook.contains("args[2] instanceof Number"));
    }

    @Test
    public void hyperLightElementMaterialIsAppliedBeforeSystemUiBlendSetter() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        String controller = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationVendorMaterialController.java");
        assertTrue(hook.contains("beforeBackend.intercept(\n                    setMiBackgroundBlendColors"));
        assertTrue(controller.contains("applyHyperLightElementMaterial"));
        assertTrue(controller.contains("setMixEffectEnabled"));
        assertTrue(controller.contains("setMiViewBlurMode"));
        assertTrue(controller.contains("setMiBackgroundBlendColors"));
        assertTrue(controller.contains("setMiBloomStroke"));
        assertTrue(controller.contains("LIGHT_MATERIAL_COLORS"));
        assertTrue(controller.contains("DARK_MATERIAL_COLORS"));
        assertTrue(controller.contains("LIGHT_BLOOM_STROKE"));
        assertTrue(controller.contains("DARK_BLOOM_STROKE"));
    }

    @Test
    public void rowElementMaterialNeverClaimsContainerPassBlurOwnership() throws Exception {
        String controller = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationVendorMaterialController.java");
        assertFalse(controller.contains("setMiBackgroundBlurMode"));
        assertFalse(controller.contains("setMiBackgroundBlurRadius"));
        assertFalse(controller.contains("setPassWindowBlurEnabled"));
        assertFalse(controller.contains("SYSTEM_PASS_BLUR_RADIUS_PX"));
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
    public void systemUiRoundAuthorityIsPreserved() throws Exception {
        String collector = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassNodeCollector.java");
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        String registry = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationMaterialTargetRegistry.java");
        String controller = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationVendorMaterialController.java");
        assertTrue(collector.contains("topCornerRadius.invoke(rowObject)"));
        assertTrue(collector.contains("bottomCornerRadius.invoke(rowObject)"));
        assertFalse(collector.contains("roundState"));
        assertTrue(hook.contains("observeRoundRect"));
        assertTrue(registry.contains("OutlineState"));
        assertTrue(registry.contains("useActualHeightGeometry"));
        assertTrue(registry.contains("useFlipRadius"));
        assertFalse(controller.contains("setOutlineProvider"));
        assertFalse(controller.contains("24.0f"));
    }
}
