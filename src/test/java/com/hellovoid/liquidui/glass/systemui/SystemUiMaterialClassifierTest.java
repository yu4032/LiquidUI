package com.hellovoid.liquidui.glass.systemui;

import static org.junit.Assert.assertEquals;

import com.hellovoid.liquidui.glass.core.GlassMaterialProfile;

import org.junit.Test;

public final class SystemUiMaterialClassifierTest {
    @Test
    public void mapsHostKindsToExistingProfiles() {
        assertEquals(
                GlassMaterialProfile.CARD,
                SystemUiMaterialClassifier.profileFor(SystemUiMaterialHostKind.MEDIA_CARD));
        assertEquals(
                GlassMaterialProfile.TILE,
                SystemUiMaterialClassifier.profileFor(SystemUiMaterialHostKind.QS_TILE));
        assertEquals(
                GlassMaterialProfile.SLIDER,
                SystemUiMaterialClassifier.profileFor(SystemUiMaterialHostKind.BRIGHTNESS_SLIDER));
        assertEquals(
                GlassMaterialProfile.PANEL,
                SystemUiMaterialClassifier.profileFor(SystemUiMaterialHostKind.VOLUME_PANEL));
        assertEquals(
                GlassMaterialProfile.FLOATING,
                SystemUiMaterialClassifier.profileFor(SystemUiMaterialHostKind.STATUS_CAPSULE));
    }

    @Test
    public void everySemanticHostKindHasOneProfile() {
        for (SystemUiMaterialHostKind kind : SystemUiMaterialHostKind.values()) {
            GlassMaterialProfile profile = SystemUiMaterialClassifier.profileFor(kind);
            if (profile == null) {
                throw new AssertionError("missing material profile for " + kind);
            }
        }
    }
}
