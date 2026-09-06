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
    public void crossClassUpdateBackgroundIsTheActiveMaterialAuthority() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        assertTrue(hook.contains("ExpandableNotificationRowInjector"));
        assertTrue(hook.contains("getDeclaredMethod(\"updateBackground$1\")"));
        assertTrue(hook.contains("injectorViewField"));
        assertTrue(hook.contains("mBackgroundNormal"));
        assertTrue(hook.contains("afterBackend.intercept(\n                    updateBackground"));
        assertFalse(hook.contains("\"updateBlurBg\","));
        assertFalse(hook.contains("\"applyElementViewBlend\""));
        assertFalse(hook.contains("MI_BLUR_COMPAT"));
        assertFalse(hook.contains("onAttachedToWindow"));
        assertFalse(hook.contains("onReinflated"));
        assertFalse(hook.contains("NotificationGlassRuntime"));
    }

    @Test
    public void injectorAndBackgroundFieldsAreResolvedFromSuperclassContracts() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        assertTrue(hook.contains("injectorClass.getField(\"view\")"));
        assertTrue(hook.contains("rowClass.getField(\"mBackgroundNormal\")"));
        assertFalse(hook.contains("injectorClass.getDeclaredField(\"view\")"));
        assertFalse(hook.contains("rowClass.getDeclaredField(\"mBackgroundNormal\")"));
    }

    @Test
    public void systemUiElementBlurIsSuppressedBeforeCustomMaterial() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        String controller = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationVendorMaterialController.java");
        assertTrue(controller.contains("suppressSystemUiElementMaterial"));
        assertTrue(controller.contains("clearMiBackgroundBlendColor"));
        assertTrue(controller.contains("setMiViewBlurMode.invoke(target, 0)"));
        assertTrue(controller.contains("applyHyperLightElementMaterial"));
        assertTrue(hook.contains("materialController.suppressSystemUiElementMaterial(target)"));
        assertTrue(hook.indexOf("materialController.suppressSystemUiElementMaterial(target)")
                < hook.indexOf("materialController.applyHyperLightElementMaterial(target, registeredRow)"));
    }

    @Test
    public void shadeBackdropBlurIsZeroedWhileNotificationGlassIsEnabled() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        String module = read("src/main/java/com/hellovoid/liquidui/ModuleMain.java");
        String policy = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationShadeBlurPolicy.java");

        assertTrue(hook.contains("ArgumentRewriteHookBackend"));
        assertTrue(hook.contains("ShadeBlendBlurController$BlurProvider"));
        assertTrue(hook.contains("ShadeBlendBlurController$BlendBackground"));
        assertTrue(hook.contains("NotificationShadeWindowView"));
        assertTrue(hook.contains("BlurUtils"));
        assertTrue(hook.contains("NotificationShadeBlurPolicy.blurRatio(true"));
        assertTrue(hook.contains("NotificationShadeBlurPolicy.blurRadius(true"));
        assertTrue(hook.contains("NotificationShadeBlurPolicy.enabled(true"));
        assertTrue(module.contains("Api101ArgumentRewriteHookBackend"));
        assertTrue(policy.contains("return glassActive ? 0f : requested"));
        assertTrue(policy.contains("return glassActive ? 0 : requested"));
        assertTrue(policy.contains("return glassActive ? false : requested"));
    }

    @Test
    public void hyperLightElementMaterialRunsAfterCompleteSystemUiBackgroundUpdate() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        String controller = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationVendorMaterialController.java");
        assertTrue(hook.contains("SystemUI has completed"));
        assertTrue(hook.contains("materialController.applyHyperLightElementMaterial(target, registeredRow)"));
        assertTrue(controller.contains("applyHyperLightElementMaterial"));
        assertTrue(controller.contains("setMixEffectEnabled"));
        assertTrue(controller.contains("setMiViewBlurMode"));
        assertTrue(controller.contains("setMiBackgroundBlendColors"));
        assertTrue(controller.contains("setMiBloomStroke"));
    }

    @Test
    public void notificationCardUsesVerifiedNativePassBlurTupleAtProductionRadius() throws Exception {
        String controller = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationVendorMaterialController.java");
        assertTrue(controller.contains("CARD_PASS_BLUR_RADIUS_DP = 2.0f"));
        assertFalse(controller.contains("CARD_PASS_BLUR_RADIUS_DP = 24.0f"));
        assertFalse(controller.contains("DIAGNOSTIC_PASS_BLUR_RADIUS_DP"));
        assertTrue(controller.contains("View.class.getMethod(\"setMiBackgroundBlurMode\", int.class)"));
        assertTrue(controller.contains("View.class.getMethod(\"setMiBackgroundBlurRadius\", int.class)"));
        assertTrue(controller.contains("View.class.getMethod(\"setPassWindowBlurEnabled\", boolean.class)"));
        assertTrue(controller.contains("setMiBackgroundBlurMode.invoke(target, 1)"));
        assertTrue(controller.contains("setMiBackgroundBlurRadius.invoke(target, radiusPx)"));
        assertTrue(controller.contains("setPassWindowBlurEnabled.invoke(target, true)"));
        assertTrue(controller.contains("enabled card pass-blur"));
    }

    @Test
    public void agslRefractionProbeWaitsForRealLayout() throws Exception {
        String controller = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationVendorMaterialController.java");
        assertTrue(controller.contains("android.graphics.RuntimeShader"));
        assertTrue(controller.contains("android.graphics.RenderEffect"));
        assertTrue(controller.contains("uniform shader content"));
        assertTrue(controller.contains("chromaticAberration"));
        assertTrue(controller.contains("createRuntimeShaderEffect"));
        assertTrue(controller.contains("target.setRenderEffect"));
        assertTrue(controller.contains("scheduleAgslRefractionProbe"));
        assertTrue(controller.contains("addOnLayoutChangeListener"));
        assertTrue(controller.contains("removeOnLayoutChangeListener"));
        assertTrue(controller.contains("applied AGSL refraction probe"));
        assertTrue(controller.indexOf("enableGpuBackdropContainer(row, target)")
                < controller.indexOf("scheduleAgslRefractionProbe(target)"));
        assertFalse(controller.contains("PixelCopy"));
        assertFalse(controller.contains("MediaProjection"));
        assertFalse(controller.contains("ScreenCapture"));
        assertFalse(controller.contains("SurfaceControl.capture"));
    }

    @Test
    public void gpuOnlyBackdropProbeMirrorsVerifiedSystemUiClockContainerTuple() throws Exception {
        String controller = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationVendorMaterialController.java");
        assertTrue(controller.contains("chooseBackgroundBlurContainer"));
        assertTrue(controller.contains("setPassTextureScale"));
        assertTrue(controller.contains("disableMiBackgroundContainBelow"));
        assertTrue(controller.contains("setPassWindowBlurEnabled.invoke(container, true)"));
        assertTrue(controller.contains("setMiBackgroundBlurMode.invoke(container, 1)"));
        assertTrue(controller.contains("setMiBackgroundBlurRadius.invoke(container, radiusPx)"));
        assertTrue(controller.contains("setPassTextureScale.invoke(container, 0.0f)"));
        assertTrue(controller.contains("disableMiBackgroundContainBelow.invoke(container, true)"));
        assertTrue(controller.contains("chooseBackgroundBlurContainer.invoke(container, member)"));
        assertTrue(controller.contains("GPU backdrop container"));
        assertFalse(controller.contains("Bitmap"));
        assertFalse(controller.contains("PixelCopy"));
        assertFalse(controller.contains("ScreenCapture"));
    }

    @Test
    public void validatedGlassPathIsNotLabelledAsTemporaryProbe() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        String controller = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationVendorMaterialController.java");
        assertFalse(hook.contains("shade-blur diagnostic"));
        assertFalse(hook.contains("Diagnostic:"));
        assertFalse(controller.contains("DIAGNOSTIC_"));
        assertFalse(controller.contains("diagnostic native PassBlur"));
    }

    @Test
    public void activeHookDoesNotOwnNotificationShadeSurfaceEndpointOrPrismalOesProducer() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        assertFalse(hook.contains("SystemUiPassBlurBridge"));
        assertFalse(hook.contains("NotificationPassBlurTextureView"));
        assertFalse(hook.contains("SetPassBlurSurface"));
        assertTrue(hook.contains("NotificationShadeWindowView"));
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
    }
}
