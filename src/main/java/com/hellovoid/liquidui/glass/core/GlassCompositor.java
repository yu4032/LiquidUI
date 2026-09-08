package com.hellovoid.liquidui.glass.core;

import com.hellovoid.prismal.PrismalGeometry;
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalRenderer;

/** Batches every visible component in one Window over one prepared PassBlur backdrop. */
final class GlassCompositor {
    private final GlassSceneState sceneState;
    private final MaterialProfileRegistry materialProfiles;
    private final PrismalBlurReuseState blurReuseState = new PrismalBlurReuseState();

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
        // prepareBackdrop() sets glassFrameBegun=false after rebuilding the source + base CARD blur.
        // Scene-only geometry frames leave it true, so the existing blur texture remains valid.
        if (!renderer.glassFrameBegun) {
            PrismalParams baseParams = materialProfiles.paramsFor(GlassMaterialProfile.CARD);
            blurReuseState.onBackdropPrepared(baseParams.blurRadiusPx);
        }

        renderer.beginGlassFrame();
        if (scene == null) return;
        for (GlassNode node : scene.nodes()) {
            if (node == null || !node.drawable()) continue;
            MaterialProfileRegistry.MaterialState state =
                    materialProfiles.stateFor(node.materialProfile());
            PrismalParams params = state.params();
            float blurRadius = params.blurRadiusPx;
            if (blurReuseState.needsRebuild(blurRadius)
                    || !PrismalPerformanceTuner.modeMatches(renderer, params)) {
                PrismalPerformanceTuner.prepareNodeBackdrop(renderer, params);
                blurReuseState.markPrepared(blurRadius);
            }
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
            renderer.drawGlass(
                    geometry,
                    params,
                    state.highlights(),
                    node.opacity());
        }
    }
}
