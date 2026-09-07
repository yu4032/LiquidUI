package com.hellovoid.liquidui.glass.core;

/** Immutable inputs needed to map one Window output UV into its current OES source. */
public record DisplayTransformSnapshot(
        int displayId,
        int displayRotation,
        int configRotation,
        int windowLeft,
        int windowTop,
        int windowWidth,
        int windowHeight,
        int hostLeft,
        int hostTop,
        int hostWidth,
        int hostHeight,
        int producerWidth,
        int producerHeight,
        float[] surfaceTextureMatrix,
        long rootGeneration,
        long producerGeneration) {
    public DisplayTransformSnapshot {
        requireRotation(displayRotation, "displayRotation");
        requireRotation(configRotation, "configRotation");
        requirePositive(windowWidth, "windowWidth");
        requirePositive(windowHeight, "windowHeight");
        requirePositive(hostWidth, "hostWidth");
        requirePositive(hostHeight, "hostHeight");
        requirePositive(producerWidth, "producerWidth");
        requirePositive(producerHeight, "producerHeight");
        if (surfaceTextureMatrix == null || surfaceTextureMatrix.length != 16) {
            throw new IllegalArgumentException("surfaceTextureMatrix must contain 16 values");
        }
        surfaceTextureMatrix = surfaceTextureMatrix.clone();
        for (float value : surfaceTextureMatrix) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("surfaceTextureMatrix must be finite");
            }
        }
    }

    @Override
    public float[] surfaceTextureMatrix() {
        return surfaceTextureMatrix.clone();
    }

    private static void requireRotation(int value, String name) {
        if (value < 0 || value > 3) {
            throw new IllegalArgumentException(name + " must be in [0,3]");
        }
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
    }
}
