package io.github.homura251.webshortcuts.icons

import org.junit.Assert.*
import org.junit.Test

class CropRectTest {
    @Test fun rejectsNonfiniteOutsideAndEmptySelections() {
        for (values in listOf(
            listOf(Float.NaN, 0f, 1f, 1f), listOf(0f, Float.POSITIVE_INFINITY, 1f, 1f),
            listOf(-0.1f, 0f, 1f, 1f), listOf(0f, 0f, 1.1f, 1f),
            listOf(0.8f, 0f, 0.2f, 1f), listOf(0f, 0.4f, 1f, 0.4f),
        )) assertThrows(IllegalArgumentException::class.java) { CropRect(values[0], values[1], values[2], values[3]) }
    }

    @Test fun movingStopsAtImageEdgesWithoutChangingSize() {
        val original = CropRect(0.2f, 0.3f, 0.6f, 0.8f)
        val moved = original.moved(5f, -5f)
        assertRect(CropRect(0.6f, 0f, 1f, 0.5f), moved)
        assertEquals(original.width, moved.width, 0.000001f)
        assertEquals(original.height, moved.height, 0.000001f)
    }

    @Test fun cornerDragRetainsOppositeCornerAndCannotInvertSelection() {
        val original = CropRect(0.2f, 0.3f, 0.7f, 0.8f)
        val expanded = original.resized(CropRect.Corner.TOP_LEFT, -5f, -5f, 0.1f, 0.1f)
        assertRect(CropRect(0f, 0f, 0.7f, 0.8f), expanded)
        val collapsed = original.resized(CropRect.Corner.TOP_LEFT, 5f, 5f, 0.1f, 0.2f)
        assertRect(CropRect(0.6f, 0.6f, 0.7f, 0.8f), collapsed)
    }

    @Test fun everyCornerRespectsMinimumSizeAndBounds() {
        val original = CropRect(0.2f, 0.3f, 0.7f, 0.8f)
        for (corner in CropRect.Corner.entries) for (dx in listOf(-5f, 0f, 5f)) for (dy in listOf(-5f, 0f, 5f)) {
            val result = original.resized(corner, dx, dy, 0.1f, 0.2f)
            assertTrue(result.width >= 0.1f - 0.000001f)
            assertTrue(result.height >= 0.2f - 0.000001f)
            assertTrue(result.left >= 0f && result.top >= 0f && result.right <= 1f && result.bottom <= 1f)
        }
    }

    @Test fun squarePixelCropUsesImageAspectRatherThanNormalizedSquare() {
        val result = CropRect.FULL.withAspectRatio(500f / 1000f, 0.032f, 0.064f)
        assertRect(CropRect(0.25f, 0f, 0.75f, 1f), result)
        assertEquals(result.width * 1000, result.height * 500, 0.0001f)
        val smaller = result.resized(CropRect.Corner.BOTTOM_RIGHT, -0.1f, -0.1f, 0.032f, 0.064f, 0.5f)
        assertEquals(result.left, smaller.left, 0.000001f)
        assertEquals(result.top, smaller.top, 0.000001f)
        assertEquals(0.5f, smaller.width / smaller.height, 0.000001f)
    }

    @Test fun fixedAspectResizeHonorsEveryCornerAndMinimum() {
        val minimum = CropRect.minimumSize(400, 1200)
        val original = CropRect(0.2f, 0.3f, 0.8f, 0.5f)
        for (corner in CropRect.Corner.entries) for (dx in listOf(-5f, 5f)) for (dy in listOf(-5f, 5f)) {
            val result = original.resized(corner, dx, dy, minimum.width, minimum.height, 3f)
            assertEquals(3f, result.width / result.height, 0.0001f)
            assertTrue(result.width >= minimum.width - 0.000001f)
            assertTrue(result.height >= minimum.height - 0.000001f)
        }
    }

    @Test fun minimumSelectionUsesSourcePixelsIncludingSmallImages() {
        val normal = CropRect.minimumSize(1000, 500)
        assertEquals(32f / 1000, normal.width, 0.000001f)
        assertEquals(32f / 500, normal.height, 0.000001f)
        val large = CropRect.minimumSize(4000, 2000)
        assertEquals(100f / 4000, large.width, 0.000001f)
        assertEquals(100f / 2000, large.height, 0.000001f)
        val small = CropRect.minimumSize(8, 16)
        assertEquals(1f, small.width, 0.000001f)
        assertEquals(0.5f, small.height, 0.000001f)
    }

    @Test fun clampingExpandsTinySelectionAndRetainsItInsideImage() {
        val result = CropRect.clamp(0.99f, 0.99f, 1f, 1f, 0.2f, 0.3f)
        assertRect(CropRect(0.8f, 0.7f, 1f, 1f), result)
        assertRect(CropRect.FULL, CropRect.clamp(-5f, -3f, 5f, 3f))
    }

    private fun assertRect(expected: CropRect, actual: CropRect) {
        assertEquals(expected.left, actual.left, 0.000001f)
        assertEquals(expected.top, actual.top, 0.000001f)
        assertEquals(expected.right, actual.right, 0.000001f)
        assertEquals(expected.bottom, actual.bottom, 0.000001f)
    }
}
