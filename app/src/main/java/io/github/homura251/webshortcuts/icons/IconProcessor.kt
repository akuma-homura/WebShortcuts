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
import kotlin.math.roundToInt

/** All decoded images are bounded, then letterboxed into the adaptive icon safe zone. */
class IconProcessor {
    fun decode(bytes: ByteArray, mimeType: String? = null, centerCrop: Boolean = false): Bitmap {
        require(bytes.isNotEmpty() && bytes.size <= IconPolicy.MAX_IMAGE_BYTES) { "Image must be smaller than 5 MB." }
        val declared = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        require(declared == null || declared in IconPolicy.supportedMimeTypes) { "Unsupported image format." }
        val actual = sniffMimeType(bytes) ?: throw IllegalArgumentException("Unsupported or invalid image.")
        require(declared == null || declared == actual || actual == "image/x-icon" && declared == "image/vnd.microsoft.icon") {
            "Image does not match its declared format."
        }
        val bitmap = when (actual) {
            "image/x-icon" -> IcoDecoder.decode(bytes)
            "image/svg+xml" -> decodeSvg(bytes)
            else -> decodeRaster(bytes, actual)
        }
        return try { normalize(bitmap, centerCrop) } finally { bitmap.recycle() }
    }

    fun normalize(source: Bitmap, centerCrop: Boolean = false): Bitmap {
        require(!source.isRecycled)
        IconPolicy.validateDimensions(source.width, source.height)
        val output = Bitmap.createBitmap(IconPolicy.OUTPUT_SIZE, IconPolicy.OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        val sourceRect = if (centerCrop) {
            val side = minOf(source.width, source.height)
            val left = (source.width - side) / 2
            val top = (source.height - side) / 2
            Rect(left, top, left + side, top + side)
        } else Rect(0, 0, source.width, source.height)
        val safeSize = IconPolicy.OUTPUT_SIZE * IconPolicy.SAFE_ZONE_RATIO
        val scale = minOf(safeSize / sourceRect.width(), safeSize / sourceRect.height())
        val width = sourceRect.width() * scale
        val height = sourceRect.height() * scale
        val offsetX = (IconPolicy.OUTPUT_SIZE - width) / 2
        val offsetY = (IconPolicy.OUTPUT_SIZE - height) / 2
        canvas.drawBitmap(source, sourceRect, RectF(offsetX, offsetY, offsetX + width, offsetY + height),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return output
    }

    private fun decodeRaster(bytes: ByteArray, mime: String): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        IconPolicy.validateDimensions(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= IconPolicy.OUTPUT_SIZE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
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

    private fun decodeSvg(bytes: ByteArray): Bitmap {
        val text = bytes.toString(Charsets.UTF_8)
        SvgSafety.check(text)
        val svg = SVG.getFromString(text)
        val viewBox = svg.documentViewBox
        val width = if (svg.documentWidth > 0) svg.documentWidth else viewBox?.width() ?: 512f
        val height = if (svg.documentHeight > 0) svg.documentHeight else viewBox?.height() ?: 512f
        require(width.isFinite() && height.isFinite() && width > 0 && height > 0)
        IconPolicy.validateDimensions(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1))
        val ratio = IconPolicy.OUTPUT_SIZE / maxOf(width, height)
        val targetWidth = (width * ratio).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * ratio).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        svg.setDocumentWidth(targetWidth.toFloat())
        svg.setDocumentHeight(targetHeight.toFloat())
        svg.renderToCanvas(Canvas(bitmap))
        return bitmap
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
