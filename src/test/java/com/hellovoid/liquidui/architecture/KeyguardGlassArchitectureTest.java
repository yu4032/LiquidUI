package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Guards the verified systemui-001 bounded Keyguard quick-affordance contract. */
public final class KeyguardGlassArchitectureTest {
    private static final Path ROOT = Path.of(
            "src/main/java/com/hellovoid/liquidui/glass/keyguard");

    @Test
    public void quickAffordancesUseExactSectionLifecycleAndBoundedButtons() throws Exception {
        assertTrue(Files.exists(ROOT.resolve("KeyguardGlassHook.java")));
        assertTrue(Files.exists(ROOT.resolve("KeyguardGlassAdapter.java")));
        assertTrue(Files.exists(ROOT.resolve("KeyguardNativeMaterialController.java")));

        String hook = Files.readString(ROOT.resolve("KeyguardGlassHook.java"));
        String adapter = Files.readString(ROOT.resolve("KeyguardGlassAdapter.java"));
        String material = Files.readString(ROOT.resolve("KeyguardNativeMaterialController.java"));

        assertTrue(hook.contains(
                "com.android.systemui.keyguard.ui.view.layout.sections.DefaultShortcutsSection"));
        assertTrue(hook.contains("addViews"));
        assertTrue(hook.contains("removeViews"));
        assertTrue(hook.contains("start_button"));
        assertTrue(hook.contains("end_button"));

        assertTrue(adapter.contains("SystemUiGlassDomain.KEYGUARD"));
        assertTrue(adapter.contains("SystemUiMaterialHostKind.KEYGUARD_TILE"));
        assertTrue(adapter.contains("keyguard_affordance_fixed_radius"));
        assertTrue(adapter.contains("hosts.register("));
        assertTrue(adapter.contains("hosts.unregister("));
        assertFalse(adapter.contains("attachRenderer("));

        assertTrue(material.contains("getBackground()"));
        assertTrue(material.contains("getAlpha()"));
        assertTrue(material.contains("setAlpha(0)"));
        assertFalse(material.contains("setVisibility("));
    }

    @Test
    public void keyguardGlassDoesNotRegisterRootsScrimsOrBouncer() throws Exception {
        String adapter = Files.readString(ROOT.resolve("KeyguardGlassAdapter.java"));
        String hook = Files.readString(ROOT.resolve("KeyguardGlassHook.java"));

        assertFalse(adapter.contains("KeyguardRootView"));
        assertFalse(adapter.contains("HyperOSKeyguardRootView"));
        assertFalse(adapter.contains("Scrim"));
        assertFalse(adapter.contains("Bouncer"));
        assertFalse(adapter.contains("SecurityContainer"));
        assertFalse(hook.contains("setMiBackgroundBlurRadius"));
        assertFalse(hook.contains("setPassWindowBlurEnabled"));
    }

    @Test
    public void keyguardUsesExistingShadeWindowRendererOnly() throws Exception {
        String hook = Files.readString(ROOT.resolve("KeyguardGlassHook.java"));
        String adapter = Files.readString(ROOT.resolve("KeyguardGlassAdapter.java"));
        String module = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/ModuleMain.java"));

        assertFalse(hook.contains("attachRenderer("));
        assertFalse(adapter.contains("attachRenderer("));
        assertTrue(module.contains("new KeyguardGlassHook("));
    }
}
