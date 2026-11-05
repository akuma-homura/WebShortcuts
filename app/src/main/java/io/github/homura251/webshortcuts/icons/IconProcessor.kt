package io.github.homura251.webshortcuts.icons

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.exifinterface.media.ExifInterface
import com.caverock.androidsvg.SVG
import java.io.ByteArrayInputStream
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Source dimensions describe the image before normalization, never the 512px output. */
data class DecodedIcon(val bitmap: Bitmap, val sourceWidth: Int, val sourceHeight: Int, val scalable: Boolean)

/** Decode once, then fit the actual artwork into Android's adaptive icon safe circle. */
class IconProcessor {
    fun decode(bytes: ByteArray, mimeType: String? = null, centerCrop: Boolean = false, checkpoint: () -> Unit = {}): Bitmap =
        decodeWithMetadata(bytes, mimeType, centerCrop, checkpoint = checkpoint).bitmap

    fun decodeWithMetadata(bytes: ByteArray, mimeType: String? = null, centerCrop: Boolean = false,
                           maskable: Boolean = false, checkpoint: () -> Unit = {}): DecodedIcon {
        checkpoint()
        require(bytes.isNotEmpty() && bytes.size <= IconPolicy.MAX_IMAGE_BYTES) { "Image must be smaller than 5 MB." }
        val declared = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        require(declared == null || declared in IconPolicy.supportedMimeTypes) { "Unsupported image format." }
        val actual = sniffMimeType(bytes) ?: throw IllegalArgumentException("Unsupported or invalid image.")
        require(declared == null || declared == actual || actual == "image/x-icon" && declared == "image/vnd.microsoft.icon") {
            "Image does not match its declared format."
        }
        val decoded = when (actual) {
            "image/x-icon" -> IcoDecoder.decode(bytes).let { DecodedIcon(it, it.width, it.height, false) }
            "image/svg+xml" -> decodeSvg(bytes, checkpoint).let { DecodedIcon(it, it.width, it.height, true) }
            else -> {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                DecodedIcon(decodeRaster(bytes, actual, checkpoint), bounds.outWidth, bounds.outHeight, false)
            }
        }
        return try {
            checkpoint()
            val normalized = normalize(decoded.bitmap, centerCrop, maskable)
            try { checkpoint(); decoded.copy(bitmap = normalized) } catch (failure: Throwable) { normalized.recycle(); throw failure }
        } finally { decoded.bitmap.recycle() }
    }

    fun normalize(source: Bitmap, centerCrop: Boolean = false, maskable: Boolean = false): Bitmap {
        require(!source.isRecycled)
        IconPolicy.validateDimensions(source.width, source.height)
        val output = Bitmap.createBitmap(IconPolicy.OUTPUT_SIZE, IconPolicy.OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        var sourceRect = if (centerCrop) {
            val side = minOf(source.width, source.height)
            val left = (source.width - side) / 2
            val top = (source.height - side) / 2
            Rect(left, top, left + side, top + side)
        } else Rect(0, 0, source.width, source.height)
        val background = solidBorderColor(source, sourceRect)
        canvas.drawColor(background ?: Color.WHITE)
        val artwork = artworkBounds(source, sourceRect, background)
        if (artwork?.isEmpty == true) {
            if (background != null) return output // A solid swatch needs no inset rectangle.
            output.recycle()
            throw IllegalArgumentException("Image contains no visible artwork.")
        }
        // Maskable web artwork already reserves an 80% safe circle. Map that circle once,
        // instead of fitting the whole square (and its existing padding) into another circle.
        val safeDiameter = IconPolicy.OUTPUT_SIZE * IconPolicy.SAFE_ZONE_RATIO - 2f
        val diameter = if (maskable) {
            maxOf(sourceRect.width(), sourceRect.height()) * IconPolicy.WEB_MASKABLE_SAFE_RATIO
        } else {
            if (artwork != null) {
                sourceRect = artwork
                artworkDiameter(source, artwork, background)
            } else hypot(sourceRect.width().toFloat(), sourceRect.height().toFloat())
        }
        val scale = safeDiameter / diameter
        val width = sourceRect.width() * scale
        val height = sourceRect.height() * scale
        val offsetX = (IconPolicy.OUTPUT_SIZE - width) / 2
        val offsetY = (IconPolicy.OUTPUT_SIZE - height) / 2
        canvas.drawBitmap(source, sourceRect, RectF(offsetX, offsetY, offsetX + width, offsetY + height),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return output
    }

    // Only an exactly uniform, opaque perimeter is treated as background. Complex images
    // keep the conservative whole-rectangle fit; pale details are never thresholded away.
    private fun solidBorderColor(source: Bitmap, rect: Rect): Int? {
        val color = source.getPixel(rect.left, rect.top)
        if (Color.alpha(color) != 255) return null
        for (x in rect.left until rect.right) {
            if (source.getPixel(x, rect.top) != color || source.getPixel(x, rect.bottom - 1) != color) return null
        }
        for (y in rect.top until rect.bottom) {
            if (source.getPixel(rect.left, y) != color || source.getPixel(rect.right - 1, y) != color) return null
        }
        return color
    }

    private fun artworkBounds(source: Bitmap, rect: Rect, background: Int?): Rect? {
        val row = IntArray(rect.width())
        var transparent = false
        var left = rect.right
        var top = rect.bottom
        var right = rect.left
        var bottom = rect.top
        for (y in rect.top until rect.bottom) {
            source.getPixels(row, 0, row.size, rect.left, y, row.size, 1)
            for (index in row.indices) {
                val color = row[index]
                if (Color.alpha(color) < 255) transparent = true
                if (if (background != null) color != background else Color.alpha(color) != 0) {
                    val x = rect.left + index
                    left = minOf(left, x); top = minOf(top, y)
                    right = maxOf(right, x + 1); bottom = maxOf(bottom, y + 1)
                }
            }
        }
        return if (background == null && !transparent) null
        else if (left >= right || top >= bottom) Rect() else Rect(left, top, right, bottom)
    }

    private fun artworkDiameter(source: Bitmap, rect: Rect, background: Int?): Float {
        val row = IntArray(rect.width())
        val centerX = rect.exactCenterX()
        val centerY = rect.exactCenterY()
        var radius = 0f
        for (y in rect.top until rect.bottom) {
            source.getPixels(row, 0, row.size, rect.left, y, row.size, 1)
            for (index in row.indices) {
                if (if (background != null) row[index] == background else Color.alpha(row[index]) == 0) continue
                val dx = kotlin.math.abs(rect.left + index + 0.5f - centerX) + 0.5f
                val dy = kotlin.math.abs(y + 0.5f - centerY) + 0.5f
                radius = maxOf(radius, hypot(dx, dy))
            }
        }
        return 2f * radius
    }

    private fun decodeRaster(bytes: ByteArray, mime: String, checkpoint: () -> Unit): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        IconPolicy.validateDimensions(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= IconPolicy.OUTPUT_SIZE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        checkpoint()
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw IllegalArgumentException("Image could not be decoded.")
        if (mime != "image/jpeg") return decoded
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return decoded
        }
        val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (oriented !== decoded) decoded.recycle()
        return oriented
    }

    private fun decodeSvg(bytes: ByteArray, checkpoint: () -> Unit): Bitmap {
        val text = bytes.toString(Charsets.UTF_8)
        SvgSafety.check(text, checkpoint)
        checkpoint()
        val svg = SVG.getFromString(text)
        checkpoint()
        val viewBox = svg.documentViewBox
        val width = if (svg.documentWidth > 0) svg.documentWidth else viewBox?.width() ?: 512f
        val height = if (svg.documentHeight > 0) svg.documentHeight else viewBox?.height() ?: 512f
        require(width.isFinite() && height.isFinite() && width > 0 && height > 0)
        IconPolicy.validateDimensions(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1))
        val ratio = IconPolicy.OUTPUT_SIZE / maxOf(width, height)
        val targetWidth = (width * ratio).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * ratio).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        return try {
            svg.setDocumentWidth(targetWidth.toFloat())
            svg.setDocumentHeight(targetHeight.toFloat())
            checkpoint()
            svg.renderToCanvas(Canvas(bitmap))
            checkpoint()
            bitmap
        } catch (failure: Throwable) {
            bitmap.recycle()
            throw failure
        }
    }

    companion object {
        internal fun sniffMimeType(bytes: ByteArray): String? {
            if (bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes.copyOfRange(1, 8).contentEquals(
                    byteArrayOf(0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))) return "image/png"
            if (bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()) return "image/jpeg"
            if (bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP") return "image/webp"
            if (bytes.size >= 6 && bytes[0] == 0.toByte() && bytes[1] == 0.toByte() && bytes[2] == 1.toByte() && bytes[3] == 0.toByte()) return "image/x-icon"
            val prefix = bytes.copyOfRange(0, minOf(bytes.size, 1024)).toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r', '\t')
            if (Regex("<svg(?:\\s|>)", RegexOption.IGNORE_CASE).containsMatchIn(prefix)) return "image/svg+xml"
            return null
        }
    }
}
