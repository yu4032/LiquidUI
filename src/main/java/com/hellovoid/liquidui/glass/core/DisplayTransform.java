package com.hellovoid.liquidui.glass.core;

/** Composed column-major transform plus the source generations that authorize it. */
public record DisplayTransform(
        float[] windowUvToOes,
        long rootGeneration,
        long producerGeneration) {
    public DisplayTransform {
        if (windowUvToOes == null || windowUvToOes.length != 16) {
            throw new IllegalArgumentException("windowUvToOes must contain 16 values");
        }
        windowUvToOes = windowUvToOes.clone();
    }

    @Override
    public float[] windowUvToOes() {
        return windowUvToOes.clone();
    }

    public boolean matches(long root, long producer) {
        return rootGeneration == root && producerGeneration == producer;
    }

    public float[] map(float x, float y) {
        float mappedX = windowUvToOes[0] * x
                + windowUvToOes[4] * y
                + windowUvToOes[12];
        float mappedY = windowUvToOes[1] * x
                + windowUvToOes[5] * y
                + windowUvToOes[13];
        float mappedW = windowUvToOes[3] * x
                + windowUvToOes[7] * y
                + windowUvToOes[15];
        if (Math.abs(mappedW) > 0.000001f) {
            mappedX /= mappedW;
            mappedY /= mappedW;
        }
        return new float[]{mappedX, mappedY};
    }
}
