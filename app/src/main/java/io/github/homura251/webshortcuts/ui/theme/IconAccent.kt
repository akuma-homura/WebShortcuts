package io.github.homura251.webshortcuts.ui.theme

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** At most 576 reads and a 16 KiB histogram, regardless of the source's dimensions. */
internal fun extractIconAccent(bitmap: Bitmap): Color? {
    if (bitmap.isRecycled || bitmap.config == Bitmap.Config.HARDWARE) return null
    return try {
        val histogram = IntArray(4096)
        val columns = minOf(24, bitmap.width)
        val rows = minOf(24, bitmap.height)
        for (row in 0 until rows) for (column in 0 until columns) {
            val x = ((column + 0.5f) * bitmap.width / columns).toInt().coerceAtMost(bitmap.width - 1)
            val y = ((row + 0.5f) * bitmap.height / rows).toInt().coerceAtMost(bitmap.height - 1)
            val pixel = bitmap.getPixel(x, y)
            if (pixel ushr 24 < 128) continue
            val red = pixel shr 16 and 255
            val green = pixel shr 8 and 255
            val blue = pixel and 255
            val chroma = maxOf(red, green, blue) - minOf(red, green, blue)
            // Ignore transparent padding, white margins and neutral text; favor colorful artwork.
            if (chroma < 24) continue
            val bucket = ((red shr 4) shl 8) or ((green shr 4) shl 4) or (blue shr 4)
            histogram[bucket] += chroma
        }
        val bucket = histogram.indices.maxBy { histogram[it] }
        if (histogram[bucket] == 0) null else Color(
            red = ((bucket shr 8 and 15) * 17) / 255f,
            green = ((bucket shr 4 and 15) * 17) / 255f,
            blue = ((bucket and 15) * 17) / 255f,
        )
    } catch (_: IllegalStateException) {
        // The owner may have released a preview while its sampling job was being cancelled.
        null
    }
}

@Composable
internal fun rememberIconAccent(bitmap: Bitmap?): State<Color?> = produceState<Color?>(null, bitmap) {
    value = null
    if (bitmap != null) value = withContext(Dispatchers.Default) { extractIconAccent(bitmap) }
}

internal fun contrastRatio(first: Color, second: Color): Float {
    val a = first.luminance()
    val b = second.luminance()
    return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
}

/** Retain MD3 text roles and reduce the tint when an unusually bright/dark icon needs it. */
internal fun tonalAccent(base: Color, accent: Color?, foreground: Color, secondaryForeground: Color = foreground): Color {
    if (accent == null) return base
    for (step in 4 downTo 1) {
        val candidate = lerp(base, accent, step * 0.04f)
        if (contrastRatio(foreground, candidate) >= 4.5f && contrastRatio(secondaryForeground, candidate) >= 4.5f) return candidate
    }
    return base
}

@Composable
internal fun animatedIconTint(base: Color, accent: Color?, foreground: Color, secondaryForeground: Color = foreground): Color =
    // Reset the animation on theme changes so text never animates over the previous theme's surface.
    key(base, foreground, secondaryForeground) {
        animateColorAsState(
            targetValue = tonalAccent(base, accent, foreground, secondaryForeground),
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "icon_tonal_accent",
        ).value
    }
