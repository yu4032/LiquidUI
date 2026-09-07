package com.hellovoid.liquidui.glass.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DisplayTransformEngineContractTest {
    @Test
    public void surfaceTextureCropIsNeutralizedButOrientationIsPreserved() {
        float[] croppedFlipY = affine(0.25f, 0f, 0.05f, 0f, -0.25f, 0.75f);

        // LiquidDock's validated Stage-B mapping treats SurfaceTexture crop/scale/translation as
        // producer metadata, not Window geometry. Only the signed-permutation orientation remains.
        assertPoint(0, 0f, 0f, croppedFlipY, 0f, 1f);
        assertPoint(1, 0f, 0f, croppedFlipY, 0f, 0f);
        assertPoint(2, 0f, 0f, croppedFlipY, 1f, 0f);
        assertPoint(3, 0f, 0f, croppedFlipY, 1f, 1f);

        for (int rotation = 0; rotation < 4; rotation++) {
            DisplayTransform transform = DisplayTransformEngine.compose(
                    snapshot(rotation, croppedFlipY, 7L, 11L));
            assertMapped(transform, 0.5f, 0.5f, 0.5f, 0.5f);
        }
    }

    @Test
    public void oppositeLandscapeRotationsKeepCorrectTopBottomDirection() {
        float[] croppedFlipY = affine(0.25f, 0f, 0.05f, 0f, -0.25f, 0.75f);
        DisplayTransform landscape = DisplayTransformEngine.compose(
                snapshot(1, croppedFlipY, 7L, 11L));
        DisplayTransform reverseLandscape = DisplayTransformEngine.compose(
                snapshot(3, croppedFlipY, 7L, 11L));

        assertMapped(landscape, 0f, 0f, 0f, 0f);
        assertMapped(landscape, 0f, 1f, 1f, 0f);
        assertMapped(reverseLandscape, 0f, 0f, 1f, 1f);
        assertMapped(reverseLandscape, 0f, 1f, 0f, 1f);
    }

    @Test
    public void hostGeometryIsComposedInWindowCoordinates() {
        DisplayTransformSnapshot snapshot = new DisplayTransformSnapshot(
                0, 0, 0,
                0, 0, 200, 100,
                50, 25, 100, 50,
                200, 100,
                identity(), 3L, 5L);

        DisplayTransform transform = DisplayTransformEngine.compose(snapshot);

        assertMapped(transform, 0f, 0f, 0.25f, 0.25f);
        assertMapped(transform, 1f, 1f, 0.75f, 0.75f);
    }

    @Test
    public void nonOrthogonalSurfaceTransformFailsSafeToFrameworkMatrix() {
        float[] shear = affine(0.8f, 0.2f, 0.05f, 0.1f, 0.7f, 0.1f);
        DisplayTransform transform = DisplayTransformEngine.compose(
                snapshot(0, shear, 7L, 11L));

        assertMapped(transform, 0f, 0f, 0.05f, 0.1f);
        assertMapped(transform, 1f, 1f, 1.05f, 0.9f);
    }

    @Test
    public void generationsAndMatrixOwnershipArePartOfValidity() {
        float[] matrix = identity();
        DisplayTransformSnapshot snapshot = snapshot(3, matrix, 7L, 11L);
        matrix[0] = 99f;

        DisplayTransform transform = DisplayTransformEngine.compose(snapshot);
        float[] returned = transform.windowUvToOes();
        returned[0] = 99f;

        assertTrue(transform.matches(7L, 11L));
        assertFalse(transform.matches(8L, 11L));
        assertFalse(transform.matches(7L, 12L));
        assertMapped(transform, 0f, 0f, 1f, 0f);
    }

    private static void assertPoint(
            int rotation, float x, float y, float[] matrix,
            float expectedX, float expectedY) {
        DisplayTransform transform = DisplayTransformEngine.compose(
                snapshot(rotation, matrix, 7L, 11L));
        assertMapped(transform, x, y, expectedX, expectedY);
    }

    private static void assertMapped(
            DisplayTransform transform, float x, float y,
            float expectedX, float expectedY) {
        float[] mapped = transform.map(x, y);
        assertEquals(expectedX, mapped[0], 0.0001f);
        assertEquals(expectedY, mapped[1], 0.0001f);
    }

    private static DisplayTransformSnapshot snapshot(
            int rotation, float[] matrix, long rootGeneration, long producerGeneration) {
        return new DisplayTransformSnapshot(
                0, rotation, rotation,
                0, 0, 100, 100,
                0, 0, 100, 100,
                100, 100,
                matrix, rootGeneration, producerGeneration);
    }

    private static float[] identity() {
        return affine(1f, 0f, 0f, 0f, 1f, 0f);
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
}
