package io.github.homura251.webshortcuts.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.RequiresApi
import androidx.test.core.app.ApplicationProvider
import io.github.homura251.webshortcuts.R
import kotlin.math.hypot
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@RequiresApi(33)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherIconResourcesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun coloredAndThemedForegroundsFitTheSharedSafeCircle() {
        val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val layers = listOf("colored foreground" to icon.foreground, "monochrome" to checkNotNull(icon.monochrome))
        layers.forEach { (name, drawable) ->
            val radius = radius(drawable, 1080)
            println("$name radius=${radius / 10}dp; safe radius=33dp")
            assertTrue("$name radius $radius must fit the 66dp safe circle", radius <= 331.0)
        }
    }

    @Test fun foregroundFramingScalesWithTheRequestedLauncherSize() {
        val drawable = (context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable).foreground
        val small = radius(drawable, 216) / 216
        val large = radius(drawable, 1080) / 1080
        assertTrue("The emblem must remain large enough to read", small > .26)
        assertEquals("Inset must scale with icon size", large, small, .006)
    }

    private fun radius(drawable: Drawable, size: Int): Double {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        var maximum = 0.0
        var visible = 0
        pixels.forEachIndexed { index, color ->
            if (Color.alpha(color) >= 16) {
                visible++
                maximum = maxOf(maximum, hypot(index % size - (size - 1) / 2.0, index / size - (size - 1) / 2.0))
            }
        }
        assertTrue("Drawable must contain visible artwork", visible > size * size / 50)
        bitmap.recycle()
        return maximum
    }
}
