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
    public void updateBlurBgIsTheActiveMaterialAuthority() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        assertTrue(hook.contains("ExpandableNotificationRowInjector"));
        assertTrue(hook.contains("\"updateBlurBg\", int.class, int.class, boolean.class"));
        assertTrue(hook.contains("injectorViewField"));
        assertTrue(hook.contains("mBackgroundNormal"));
        assertTrue(hook.contains("afterBackend.intercept(\n                    updateBlurBg"));
        assertFalse(hook.contains("applyElementViewBlend"));
        assertFalse(hook.contains("MiBlurCompat"));
        assertFalse(hook.contains("onAttachedToWindow"));
        assertFalse(hook.contains("onReinflated"));
        assertFalse(hook.contains("NotificationGlassRuntime"));
    }

    @Test
    public void hyperLightElementMaterialRunsAfterSystemUiMaterialUpdate() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        String controller = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationVendorMaterialController.java");
        assertTrue(hook.contains("Apply AFTER SystemUI"));
        assertTrue(controller.contains("applyHyperLightElementMaterial"));
        assertTrue(controller.contains("setMixEffectEnabled"));
        assertTrue(controller.contains("setMiViewBlurMode"));
        assertTrue(controller.contains("setMiBackgroundBlendColors"));
        assertTrue(controller.contains("setMiBloomStroke"));
        assertTrue(controller.contains("via updateBlurBg"));
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
        assertFalse(hook.contains("SystemUiPassBlurBridge"));
        assertFalse(hook.contains("NotificationPassBlurTextureView"));
        assertFalse(hook.contains("SetPassBlurSurface"));
        assertFalse(hook.contains("NotificationShadeWindowView"));
    }

    @Test
    public void systemUiRoundAuthorityIsPreserved() throws Exception {
        String collector = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassNodeCollector.java");
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        String registry = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationMaterialTargetRegistry.java");
        String controller = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationVendorMaterialController.java");
        assertTrue(collector.contains("topCornerRadius.invoke(rowObject)"));
        assertTrue(collector.contains("bottomCornerRadius.invoke(rowObject)"));
        assertTrue(hook.contains("observeRoundRect"));
        assertTrue(registry.contains("OutlineState"));
        assertTrue(registry.contains("useActualHeightGeometry"));
        assertTrue(registry.contains("useFlipRadius"));
        assertFalse(controller.contains("setOutlineProvider"));
        assertFalse(controller.contains("24.0f"));
    }
}
