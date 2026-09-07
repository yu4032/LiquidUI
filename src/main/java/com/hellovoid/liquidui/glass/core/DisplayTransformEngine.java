package com.hellovoid.liquidui.glass.core;

/** Sole policy for composing Window geometry, orientation, and SurfaceTexture transforms. */
public final class DisplayTransformEngine {
    private DisplayTransformEngine() {}

    public static DisplayTransform compose(DisplayTransformSnapshot snapshot) {
        if (snapshot == null) throw new NullPointerException("snapshot");

        float windowX = (snapshot.hostLeft() - snapshot.windowLeft())
                / (float) snapshot.windowWidth();
        float windowY = 1f - (snapshot.hostTop() + snapshot.hostHeight()
                - snapshot.windowTop()) / (float) snapshot.windowHeight();
        float windowW = snapshot.hostWidth() / (float) snapshot.windowWidth();
        float windowH = snapshot.hostHeight() / (float) snapshot.windowHeight();

        float[] hostToWindow = affine(
                windowW, 0f, windowX,
                0f, windowH, windowY);

        // ViewRoot's configRotation is the buffer transform hint: it describes how SurfaceFlinger
        // rotates the producer buffer to present it in the Window. This engine maps in the reverse
        // direction (Window UV -> producer/OES UV), so applying the hint forward double-signs the
        // two landscape orientations. Invert the quarter-turn here, then let SurfaceTexture's
        // authoritative matrix apply its own crop/flip transform last.
        int inverseBufferRotation = (4 - snapshot.configRotation()) & 3;
        float[] windowToBuffer = orientation(inverseBufferRotation);
        float[] finalMatrix = multiply(
                snapshot.surfaceTextureMatrix(),
                multiply(windowToBuffer, hostToWindow));
        return new DisplayTransform(
                finalMatrix,
                snapshot.rootGeneration(),
                snapshot.producerGeneration());
    }

    private static float[] orientation(int rotation) {
        return switch (rotation) {
            case 0 -> affine(1f, 0f, 0f, 0f, 1f, 0f);
            case 1 -> affine(0f, 1f, 0f, -1f, 0f, 1f);
            case 2 -> affine(-1f, 0f, 1f, 0f, -1f, 1f);
            case 3 -> affine(0f, -1f, 1f, 1f, 0f, 0f);
            default -> throw new IllegalArgumentException("rotation must be in [0,3]");
        };
    }

    private static float[] affine(
            float xx, float xy, float tx,
            float yx, float yy, float ty) {
        return new float[]{
                xx, yx, 0f, 0f,
                xy, yy, 0f, 0f,
                0f, 0f, 1f, 0f,
                tx, ty, 0f, 1f
        };
    }

    private static float[] multiply(float[] left, float[] right) {
        float[] result = new float[16];
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                float value = 0f;
                for (int index = 0; index < 4; index++) {
                    value += left[index * 4 + row] * right[column * 4 + index];
                }
                result[column * 4 + row] = value;
            }
        }
        return result;
    }
}
