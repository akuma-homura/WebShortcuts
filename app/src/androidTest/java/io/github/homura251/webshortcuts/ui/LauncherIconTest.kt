package io.github.homura251.webshortcuts.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.homura251.webshortcuts.R
import java.io.File
import kotlin.math.hypot
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherIconTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun launcherResourcesKeepTheirForegroundInsideTheSharedSafeCircle() {
        val normal = context.applicationInfo.loadIcon(context.packageManager)
        assertTrue("The installed application must expose an adaptive icon", normal is AdaptiveIconDrawable)
        val round = context.getDrawable(R.mipmap.ic_launcher_round) as AdaptiveIconDrawable
        listOf(normal as AdaptiveIconDrawable, round).forEach { icon ->
            assertSafeForeground(icon.foreground, "colored foreground")
            if (Build.VERSION.SDK_INT >= 33) assertSafeForeground(checkNotNull(icon.monochrome), "monochrome")
            val background = drawLayer(icon.background)
            val pixels = IntArray(background.width * background.height)
            background.getPixels(pixels, 0, background.width, 0, 0, background.width, background.height)
            assertTrue("Background must cover every launcher mask without transparent edges", pixels.all { Color.alpha(it) == 255 })
            background.recycle()
        }
    }

    @Test fun captureCommonLauncherMasksAndThemedIcons() {
        val directory = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        listOf(false to "20_launcher_icon_light.png", true to "21_launcher_icon_dark.png").forEach { (dark, name) ->
            val bitmap = LauncherIconPreviewRenderer.gallery(context, dark)
            File(directory, name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }

    private fun drawLayer(drawable: Drawable): Bitmap = Bitmap.createBitmap(1080, 1080, Bitmap.Config.ARGB_8888).also {
        drawable.setBounds(0, 0, it.width, it.height)
        drawable.draw(Canvas(it))
    }

    private fun assertSafeForeground(drawable: Drawable, label: String) {
        val bitmap = drawLayer(drawable)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var visible = 0
        var maxRadius = 0.0
        pixels.forEachIndexed { index, pixel ->
            // Ignore only the barely visible antialias/gloss fringe.
            if (Color.alpha(pixel) >= 16) {
                visible++
                maxRadius = maxOf(maxRadius, hypot(index % bitmap.width - 539.5, index / bitmap.width - 539.5))
            }
        }
        assertTrue("$label must contain visible artwork", visible > 5_000)
        assertTrue("$label radius $maxRadius must fit the 66/108 safe circle", maxRadius <= 331.0)
        bitmap.recycle()
    }
}
