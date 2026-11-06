package io.github.homura251.webshortcuts.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.IconSourceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A failed AUTO fetch changes the rendering, never the user's selected mode. */
class IconResolver(
    @Suppress("UNUSED_PARAMETER") context: Context,
    private val store: IconStore,
    @Suppress("UNUSED_PARAMETER") private val processor: IconProcessor,
) {
    suspend fun resolve(bookmark: BookmarkItem): Bitmap = withContext(Dispatchers.Default) {
        val path = when (bookmark.selectedIconSource) {
            IconSourceMode.AUTO -> bookmark.autoImagePath
            IconSourceMode.CUSTOM -> bookmark.customImagePath
            IconSourceMode.GENERATED -> null
        }
        store.read(path) ?: generated(bookmark.title, bookmark.url)
    }

    fun generated(title: String, url: String): Bitmap {
        val size = IconPolicy.OUTPUT_SIZE
        val pair = IconPolicy.tonalPair(url)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(pair.background)
        val text = IconPolicy.initial(title)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pair.foreground
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textSize = size * 0.40f
        }
        val maxWidth = size * IconPolicy.SAFE_ZONE_RATIO * 0.82f
        if (paint.measureText(text) > maxWidth) paint.textSize *= maxWidth / paint.measureText(text)
        val baseline = size / 2f - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
        canvas.drawText(text, size / 2f, baseline, paint)
        return bitmap
    }
}
