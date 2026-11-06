package io.github.homura251.webshortcuts.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.exifinterface.media.ExifInterface
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconProcessorTest {
    private val processor = IconProcessor()

    @Test fun preservesAspectRatioAndProvidesAdaptiveSafePadding() {
        val source = rectangleLogo(200, 100)
        val result = processor.normalize(source)
        assertEquals(512, result.width)
        assertEquals(512, result.height)
        assertEquals(Color.RED, result.getPixel(256, 256))
        assertEquals(Color.WHITE, result.getPixel(256, 150))
        assertEquals(Color.WHITE, result.getPixel(50, 256))
    }

    @Test fun squareWideAndTallLogosRemainEntirelyInsideTheCircularSafeZone() {
        for ((width, height) in listOf(128 to 128, 200 to 100, 100 to 200)) {
            val source = rectangleLogo(width, height)
            val result = processor.normalize(source)
            try {
                val pixels = IntArray(result.width * result.height)
                result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
                val center = result.width / 2.0
                val radius = result.width * IconPolicy.SAFE_ZONE_RATIO.toDouble() / 2.0
                var farthestSquared = 0.0
                var left = result.width
                var right = -1
                var top = result.height
                var bottom = -1
                for (y in 0 until result.height) for (x in 0 until result.width) {
                    if (pixels[y * result.width + x] == Color.WHITE) continue
                    val dx = x + 0.5 - center
                    val dy = y + 0.5 - center
                    farthestSquared = maxOf(farthestSquared, dx * dx + dy * dy)
                    left = minOf(left, x)
                    right = maxOf(right, x)
                    top = minOf(top, y)
                    bottom = maxOf(bottom, y)
                }
                assertEquals("$width x $height center", Color.RED, result.getPixel(256, 256))
                assertTrue("$width x $height logo extends beyond the safe circle", farthestSquared <= radius * radius)
                assertEquals("$width x $height aspect ratio", width.toDouble() / height,
                    (right - left + 1).toDouble() / (bottom - top + 1), 0.03)
                assertEquals("$width x $height horizontal centering", center - 0.5, (left + right) / 2.0, 0.5)
                assertEquals("$width x $height vertical centering", center - 0.5, (top + bottom) / 2.0, 0.5)
            } finally {
                result.recycle()
                source.recycle()
            }
        }
    }

    @Test fun transparentAndWhiteMarginsDoNotShrinkTheSameRoundLogo() {
        val small = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        Canvas(small).drawCircle(50f, 50f, 40f, Paint().apply { color = Color.RED })
        val padded = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        Canvas(padded).drawBitmap(small, 206f, 206f, null)
        val white = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        Canvas(white).drawBitmap(small, 206f, 206f, null)
        val results = listOf(small, padded, white).map { processor.normalize(it) }
        try {
            assertTrue(results[0].sameAs(results[1]))
            // Filtering a transparent edge and an edge already composited onto white can
            // round channel values differently. Compare the actual visible artwork's size.
            fun visibleSpan(bitmap: Bitmap) = (0 until 512).filter {
                val pixel = bitmap.getPixel(it, 256)
                Color.red(pixel) > 128 && Color.green(pixel) < 128
            }
            val transparentSpan = visibleSpan(results[0])
            val whiteSpan = visibleSpan(results[2])
            assertEquals(transparentSpan.first().toDouble(), whiteSpan.first().toDouble(), 1.0)
            assertEquals(transparentSpan.last().toDouble(), whiteSpan.last().toDouble(), 1.0)
            val diameter = (0 until 512).count { results[0].getPixel(it, 256) != Color.WHITE }
            assertTrue("Round artwork should use the safe circle, not an inscribed square", diameter in 300..314)
        } finally {
            (results + listOf(small, padded, white)).forEach(Bitmap::recycle)
        }
    }

    @Test fun maskableEightyPercentCircleUsesAndroidSafeCircleOnce() {
        val source = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        Canvas(source).drawCircle(250f, 250f, 200f, Paint().apply { color = Color.RED })
        val result = processor.normalize(source, maskable = true)
        try {
            val diameter = (0 until 512).count { Color.red(result.getPixel(it, 256)) > 128 }
            assertTrue("Web safe circle should map to the 66/108 Android circle", diameter in 308..314)
            assertEquals("Extend the existing background instead of adding a white frame", Color.BLUE, result.getPixel(0, 0))
        } finally { source.recycle(); result.recycle() }
    }

    @Test fun opaqueArtworkWithDifferentCornerDetailsKeepsAllFourCorners() {
        val source = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val canvas = Canvas(source)
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        listOf(0f to 0f, 180f to 0f, 0f to 180f, 180f to 180f).forEachIndexed { index, (x, y) ->
            canvas.drawRect(x, y, x + 20f, y + 20f, Paint().apply { color = colors[index] })
        }
        val result = processor.normalize(source)
        try {
            val pixels = IntArray(512 * 512)
            result.getPixels(pixels, 0, 512, 0, 0, 512, 512)
            colors.forEach { assertTrue("All corner details must survive", it in pixels) }
            for (y in 0 until 512) for (x in 0 until 512) {
                if (result.getPixel(x, y) == Color.WHITE) continue
                assertTrue(kotlin.math.hypot(x + 0.5 - 256, y + 0.5 - 256) <= 512 * IconPolicy.SAFE_ZONE_RATIO / 2)
            }
        } finally { source.recycle(); result.recycle() }
    }

    @Test fun metadataReportsOriginalRasterPixelsBeforeDownsampling() {
        val source = Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val bytes = ByteArrayOutputStream().also { source.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val result = processor.decodeWithMetadata(bytes, "image/png")
        try {
            assertEquals(2048, result.sourceWidth)
            assertEquals(1024, result.sourceHeight)
            assertFalse(result.scalable)
            assertEquals(512, result.bitmap.width)
        } finally { source.recycle(); result.bitmap.recycle() }
    }

    @Test fun invisibleArtworkIsRejectedForBothOrdinaryAndMaskableSources() {
        val source = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        try {
            for (maskable in listOf(false, true)) {
                assertThrows(IllegalArgumentException::class.java) { processor.normalize(source, maskable = maskable) }
            }
        } finally { source.recycle() }
    }

    @Test fun readsPngAndRejectsMismatchedMime() {
        val png = png()
        assertEquals(512, processor.decode(png, "image/png").width)
        assertThrows(IllegalArgumentException::class.java) { processor.decode(png, "image/jpeg") }
    }

    @Test fun rejectsHugeRasterBeforeAllocatingFullImage() {
        val huge = Bitmap.createBitmap(4097, 1, Bitmap.Config.ARGB_8888)
        val stream = ByteArrayOutputStream()
        huge.compress(Bitmap.CompressFormat.PNG, 100, stream)
        assertThrows(IllegalArgumentException::class.java) { processor.decode(stream.toByteArray(), "image/png") }
    }

    @Test fun icoDecodesHighestPngFrame() {
        val payload = png()
        val bytes = ByteBuffer.allocate(22 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(0).putShort(1).putShort(1)
            .put(64).put(64).put(0).put(0).putShort(1).putShort(32)
            .putInt(payload.size).putInt(22).put(payload).array()
        val image = processor.decode(bytes, "image/x-icon")
        assertEquals(Color.RED, image.getPixel(256, 256))
    }

    @Test fun simpleSvgIsRenderedInsideSafeZone() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><rect x="2" y="2" width="20" height="20" fill="#ff0000"/></svg>"""
        val bitmap = processor.decode(svg.toByteArray(), "image/svg+xml")
        assertEquals(Color.RED, bitmap.getPixel(256, 256))
        assertEquals(Color.WHITE, bitmap.getPixel(20, 20))
    }

    @Test fun freeCropSelectsOffCenterPixelsWithoutMutatingTheRetainedOriginal() {
        val source = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        Canvas(source).apply {
            drawColor(Color.RED)
            drawRect(100f, 0f, 200f, 100f, Paint().apply { color = Color.BLUE })
        }
        val right = processor.crop(source, CropRect(0.5f, 0f, 1f, 1f))
        val left = processor.crop(source, CropRect(0f, 0f, 0.5f, 1f))
        try {
            assertEquals(Color.BLUE, right.getPixel(256, 256))
            assertEquals(Color.RED, left.getPixel(256, 256))
            assertFalse(source.isRecycled)
            assertEquals(200, source.width)
            assertEquals(Color.RED, source.getPixel(20, 50))
            assertEquals(Color.BLUE, source.getPixel(180, 50))
        } finally { right.recycle(); left.recycle(); source.recycle() }
    }

    @Test fun editableDecodePreservesTransparencyAndOriginalGeometryInsteadOfLauncherPadding() {
        val source = rectangleLogo(120, 60)
        val bytes = ByteArrayOutputStream().also { source.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val editable = processor.decodeEditable(bytes, "image/png")
        try {
            assertEquals(122, editable.width)
            assertEquals(62, editable.height)
            assertEquals(Color.TRANSPARENT, editable.getPixel(0, 0))
            assertEquals(Color.RED, editable.getPixel(60, 30))
        } finally { editable.recycle(); source.recycle() }
    }

    @Test fun editableDecodeBoundsLargeSourcesWithoutCollapsingToA512PixelIcon() {
        val source = Bitmap.createBitmap(3800, 1900, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val bytes = ByteArrayOutputStream().also { source.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        source.recycle()
        val editable = processor.decodeEditable(bytes, "image/png")
        try {
            assertEquals(2048, editable.width)
            assertEquals(1024, editable.height)
        } finally { editable.recycle() }
    }

    @Test fun editableJpegAppliesExifBeforeTheUserChoosesCropCoordinates() {
        val source = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888)
        Canvas(source).apply {
            drawColor(Color.RED)
            drawRect(60f, 0f, 120f, 60f, Paint().apply { color = Color.BLUE })
        }
        val file = File.createTempFile("editable-orientation", ".jpg")
        try {
            file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            ExifInterface(file).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            val editable = processor.decodeEditable(file.readBytes(), "image/jpeg")
            try {
                assertEquals(60, editable.width)
                assertEquals(120, editable.height)
                assertTrue(Color.red(editable.getPixel(30, 20)) > 200)
                assertTrue(Color.blue(editable.getPixel(30, 100)) > 200)
            } finally { editable.recycle() }
        } finally { file.delete(); source.recycle() }
    }

    private fun rectangleLogo(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width + 2, height + 2, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawRect(1f, 1f, width + 1f, height + 1f, Paint().apply { color = Color.RED })
        }

    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
}
