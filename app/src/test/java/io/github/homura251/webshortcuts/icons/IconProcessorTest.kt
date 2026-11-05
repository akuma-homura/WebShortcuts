package io.github.homura251.webshortcuts.icons

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconProcessorTest {
    private val processor = IconProcessor()

    @Test fun preservesAspectRatioAndProvidesAdaptiveSafePadding() {
        val source = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val result = processor.normalize(source)
        assertEquals(512, result.width)
        assertEquals(512, result.height)
        assertEquals(Color.RED, result.getPixel(256, 256))
        assertEquals(Color.WHITE, result.getPixel(256, 150))
        assertEquals(Color.WHITE, result.getPixel(50, 256))
    }

    @Test fun squareWideAndTallLogosRemainEntirelyInsideTheCircularSafeZone() {
        for ((width, height) in listOf(128 to 128, 200 to 100, 100 to 200)) {
            val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
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
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><rect width="24" height="24" fill="#ff0000"/></svg>"""
        val bitmap = processor.decode(svg.toByteArray(), "image/svg+xml")
        assertEquals(Color.RED, bitmap.getPixel(256, 256))
        assertEquals(Color.WHITE, bitmap.getPixel(20, 20))
    }

    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
}
