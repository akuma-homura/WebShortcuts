package io.github.homura251.webshortcuts.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconAccentTest {
    @Test fun coloredArtworkWinsOverWhitePaddingAndSamplingDoesNotMutateOrRecycleTheSource() {
        val source = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            Canvas(source).drawRect(180f, 180f, 332f, 332f, Paint().apply { color = Color.BLUE })
            val accent = extractIconAccent(source)
            assertNotNull(accent)
            assertEquals(Color.BLUE, accent!!.toArgb())
            assertFalse(source.isRecycled)
            assertEquals(Color.WHITE, source.getPixel(0, 0))
            assertEquals(Color.BLUE, source.getPixel(256, 256))
        } finally { source.recycle() }
    }

    @Test fun tinyTransparentNeutralAndRecycledImagesHaveSafeFallbacks() {
        val source = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        assertNull(extractIconAccent(source))
        source.eraseColor(Color.WHITE)
        assertNull(extractIconAccent(source))
        source.eraseColor(Color.RED)
        assertEquals(Color.RED, extractIconAccent(source)!!.toArgb())
        source.eraseColor(0x40FF0000)
        assertNull(extractIconAccent(source))
        source.recycle()
        assertNull(extractIconAccent(source))
    }
}
