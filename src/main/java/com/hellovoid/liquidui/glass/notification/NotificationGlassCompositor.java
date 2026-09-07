package com.hellovoid.liquidui.glass.notification;

import com.hellovoid.prismal.PrismalGeometry;
import com.hellovoid.prismal.PrismalHighlightProfile;
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalRenderer;

/** Batches every visible notification over one prepared PassBlur backdrop. */
final class NotificationGlassCompositor {
    // First production validation deliberately disables every decorative highlight. Any visible
    // edge motion/RGB separation must therefore come from Prismal's optical displacement path.
    private static final PrismalHighlightProfile REFRACTION_ONLY =
            new PrismalHighlightProfile(
                    false, false, false, false, false, false, false, false, false);

    private final NotificationGlassSceneState sceneState;

    NotificationGlassCompositor(NotificationGlassSceneState sceneState) {
        this.sceneState = sceneState;
    }

    NotificationGlassSceneSnapshot latestScene() { return sceneState.latest(); }

    void drawFrame(
            PrismalRenderer renderer,
            PrismalParams params,
            NotificationGlassSceneSnapshot scene,
            int framebufferWidth,
            int framebufferHeight,
            int insetLeft,
            int insetTop) {
        renderer.beginGlassFrame();
        if (scene == null) return;
        for (NotificationGlassNode node : scene.nodes) {
            if (node == null || !node.drawable()) continue;
            float centerX = insetLeft + node.left + node.width * 0.5f;
            float centerY = insetTop + node.top + node.height * 0.5f;
            PrismalGeometry geometry = new PrismalGeometry(
                    framebufferWidth,
                    framebufferHeight,
                    centerX,
                    centerY,
                    node.width,
                    node.height,
                    node.topLeftRadius,
                    node.topRightRadius,
                    node.bottomRightRadius,
                    node.bottomLeftRadius);
            renderer.drawGlass(geometry, params, REFRACTION_ONLY, node.opacity);
        }
    }
}
