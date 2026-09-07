package com.hellovoid.liquidui.glass.core;

/** Sole policy for composing Window geometry, orientation, and SurfaceTexture transforms. */
public final class DisplayTransformEngine {
    private static final float EPSILON = 0.000001f;
    private static final float ORTHOGONAL_EPSILON = 0.001f;

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

        // Match LiquidDock's validated Stage-B contract. ViewRoot configRotation maps current
        // root/window UV into the producer's oriented domain. SurfaceTexture may additionally
        // contain signed-permutation orientation plus crop scale/translation. The latter describes
        // BufferQueue crop metadata, not Window geometry: carrying it into the Window transform
        // makes its translation rotate into opposite vertical offsets at ROTATION_90/270.
        // Normalize an orthogonal SurfaceTexture matrix down to orientation only. If a future
        // producer supplies a real shear/non-orthogonal transform, preserve the framework matrix
        // intact rather than guessing how to neutralize it.
        float[] surfaceOrientation = orientationOnlyOrFramework(
                snapshot.surfaceTextureMatrix());
        float[] rootToProducer = orientation(snapshot.configRotation());
        float[] finalMatrix = multiply(
                surfaceOrientation,
                multiply(rootToProducer, hostToWindow));
        return new DisplayTransform(
                finalMatrix,
                snapshot.rootGeneration(),
                snapshot.producerGeneration());
    }

    private static float[] orientationOnlyOrFramework(float[] matrix) {
        float c0x = matrix[0];
        float c0y = matrix[1];
        float c1x = matrix[4];
        float c1y = matrix[5];
        float scale0 = length(c0x, c0y);
        float scale1 = length(c1x, c1y);
        float determinant = c0x * c1y - c1x * c0y;
        if (scale0 <= EPSILON || scale1 <= EPSILON || Math.abs(determinant) <= EPSILON) {
            return matrix.clone();
        }

        float o0x = c0x / scale0;
        float o0y = c0y / scale0;
        float o1x = c1x / scale1;
        float o1y = c1y / scale1;
        float dot = o0x * o1x + o0y * o1y;
        if (Math.abs(dot) > ORTHOGONAL_EPSILON) return matrix.clone();

        float biasX = -Math.min(0f, o0x) - Math.min(0f, o1x);
        float biasY = -Math.min(0f, o0y) - Math.min(0f, o1y);
        return affine(
                o0x, o1x, biasX,
                o0y, o1y, biasY);
    }

    private static float length(float x, float y) {
        return (float) Math.sqrt(x * x + y * y);
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
