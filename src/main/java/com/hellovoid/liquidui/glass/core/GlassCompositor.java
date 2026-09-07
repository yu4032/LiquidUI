package com.hellovoid.liquidui.glass.core;

import com.hellovoid.prismal.PrismalGeometry;
import com.hellovoid.prismal.PrismalHighlightProfile;
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalRenderer;

/** Batches every visible component in one Window over one prepared PassBlur backdrop. */
final class GlassCompositor {
    private static final PrismalHighlightProfile REFRACTION_ONLY =
            new PrismalHighlightProfile(
                    false, false, false, false, false, false, false, false, false);

    private final GlassSceneState sceneState;
    private final MaterialProfileRegistry materialProfiles;

    GlassCompositor(GlassSceneState sceneState, MaterialProfileRegistry materialProfiles) {
        this.sceneState = sceneState;
        this.materialProfiles = materialProfiles;
    }

    GlassSceneSnapshot latestScene() {
        return sceneState.latest();
    }

    void drawFrame(
            PrismalRenderer renderer,
            GlassSceneSnapshot scene,
            int framebufferWidth,
            int framebufferHeight,
            int insetLeft,
            int insetTop) {
        PrismalPerformanceTuner.ensureFastBackdrop(renderer);
        renderer.beginGlassFrame();
        if (scene == null) return;
        for (GlassNode node : scene.nodes()) {
            if (node == null || !node.drawable()) continue;
            PrismalParams params = materialProfiles.paramsFor(node.materialProfile());
            float centerX = insetLeft + node.left() + node.width() * 0.5f;
            float centerY = insetTop + node.top() + node.height() * 0.5f;
            PrismalGeometry geometry = new PrismalGeometry(
                    framebufferWidth,
                    framebufferHeight,
                    centerX,
                    centerY,
                    node.width(),
                    node.height(),
                    node.topLeftRadius(),
                    node.topRightRadius(),
                    node.bottomRightRadius(),
                    node.bottomLeftRadius());
            renderer.drawGlass(geometry, params, REFRACTION_ONLY, node.opacity());
        }
    }
}
