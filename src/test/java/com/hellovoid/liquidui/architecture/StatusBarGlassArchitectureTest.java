package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Guards the verified systemui-001 ongoing-activity chip glass contract. */
public final class StatusBarGlassArchitectureTest {
    @Test
    public void ongoingActivityChipUsesExactBinderAndBoundedBackgroundHost() throws Exception {
        Path root = Path.of("src/main/java/com/hellovoid/liquidui/glass/statusbar");
        Path coreAuthority = Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/core/VerifiedWindowRendererAuthority.java");
        assertTrue(Files.exists(root.resolve("StatusBarGlassHook.java")));
        assertTrue(Files.exists(root.resolve("StatusBarGlassAdapter.java")));
        assertTrue(Files.exists(root.resolve("StatusBarNativeMaterialController.java")));
        assertTrue(Files.exists(root.resolve("StatusBarWindowGlassAuthority.java")));
        assertTrue(Files.exists(coreAuthority));

        String hook = Files.readString(root.resolve("StatusBarGlassHook.java"));
        String adapter = Files.readString(root.resolve("StatusBarGlassAdapter.java"));
        String material = Files.readString(root.resolve("StatusBarNativeMaterialController.java"));
        String authority = Files.readString(root.resolve("StatusBarWindowGlassAuthority.java"));
        String rendererAuthority = Files.readString(coreAuthority);

        assertTrue(hook.contains("com.android.systemui.statusbar.chips.ui.binder.OngoingActivityChipBinder"));
        assertTrue(hook.contains("com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel$Active"));
        assertTrue(hook.contains("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView"));
        assertTrue(hook.contains("backgroundView"));
        assertTrue(hook.contains("bindChip("));
        assertTrue(hook.contains("unbindChip("));

        assertTrue(adapter.contains("SystemUiGlassDomain.STATUS_BAR"));
        assertTrue(adapter.contains("SystemUiMaterialHostKind.STATUS_CAPSULE"));
        assertTrue(adapter.contains("ongoing_activity_chip_corner_radius"));
        assertTrue(adapter.contains("hosts.register("));
        assertTrue(adapter.contains("hosts.unregister("));
        assertFalse(adapter.contains("attachRenderer("));

        assertTrue(material.contains("getBackground()"));
        assertTrue(material.contains("setAlpha(0)"));
        assertFalse(material.contains("setVisibility("));

        // The statusbar domain only proves exact root/lane authority. Renderer creation remains
        // shared/core-owned so domain packages never acquire producer APIs.
        assertTrue(authority.contains("MiuiPhoneStatusBarView"));
        assertTrue(authority.contains("VerifiedWindowRendererAuthority.ensure("));
        assertFalse(authority.contains("attachRenderer("));
        assertFalse(authority.contains("EGL14"));
        assertFalse(authority.contains("SurfaceTexture"));
        assertFalse(authority.contains("SetPassBlurSurface"));
        assertTrue(rendererAuthority.contains("session.attachRenderer("));
    }

    @Test
    public void statusBarRootAndForegroundChildrenAreNeverGlassNodes() throws Exception {
        String adapter = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/statusbar/StatusBarGlassAdapter.java"));

        assertFalse(adapter.contains("MiuiPhoneStatusBarView"));
        assertFalse(adapter.contains("status_bar_icons"));
        assertFalse(adapter.contains("status_bar_contents"));
        assertFalse(adapter.contains("FocusedNotifPromptView"));
        assertFalse(adapter.contains("DynamicIsland"));
    }
}
