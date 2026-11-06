package io.github.homura251.webshortcuts.icons

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** ICO directory parsing is separate from Android decoding so malformed containers are testable. */
internal object IcoDirectory {
    data class Frame(val width: Int, val height: Int, val bits: Int, val offset: Int, val length: Int)

    fun frames(bytes: ByteArray): List<Frame> {
        require(bytes.size >= 6) { "Truncated ICO header." }
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(data.short.toInt() == 0 && data.short.toInt() == 1) { "Not an ICO file." }
        val count = data.short.toInt() and 0xffff
        require(count in 1..256 && 6L + count * 16L <= bytes.size) { "Invalid ICO directory." }
        return (0 until count).mapNotNull {
            val width = (data.get().toInt() and 255).let { if (it == 0) 256 else it }
            val height = (data.get().toInt() and 255).let { if (it == 0) 256 else it }
            data.get() // Palette length.
            data.get() // Reserved.
            data.short // Planes.
            val bits = data.short.toInt() and 0xffff
            val length = data.int.toLong() and 0xffffffffL
            val offset = data.int.toLong() and 0xffffffffL
            if (length == 0L || offset < 6L + count * 16L || offset + length > bytes.size) null
            else Frame(width, height, bits, offset.toInt(), length.toInt())
        }.sortedWith(compareByDescending<Frame> { minOf(it.width, it.height) }.thenByDescending { it.bits })
    }
}

internal object IcoDecoder {
    private val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)

    fun decode(bytes: ByteArray): Bitmap {
        for (frame in IcoDirectory.frames(bytes)) {
            val payload = bytes.copyOfRange(frame.offset, frame.offset + frame.length)
            val bitmap = runCatching {
                if (payload.size >= 8 && payload.copyOfRange(0, 8).contentEquals(pngSignature)) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(payload, 0, payload.size, bounds)
                    IconPolicy.validateDimensions(bounds.outWidth, bounds.outHeight)
                    require(bounds.outWidth == frame.width && bounds.outHeight == frame.height) { "ICO frame size mismatch." }
                    BitmapFactory.decodeByteArray(payload, 0, payload.size)
                } else decodeDib(payload)
            }.getOrNull()
            if (bitmap != null) return bitmap
        }
        throw IllegalArgumentException("No supported frame found in ICO file.")
    }

    /** Uncompressed 1/4/8/24/32 bit Windows DIB frames, including legacy AND transparency masks. */
    private fun decodeDib(bytes: ByteArray): Bitmap {
        require(bytes.size >= 40) { "Truncated DIB header." }
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val headerSize = data.int
        require(headerSize in 40..124 && headerSize <= bytes.size)
        val width = data.int
        val dibHeight = data.int
        require(dibHeight > 0 && dibHeight % 2 == 0)
        val height = dibHeight / 2
        IconPolicy.validateDimensions(width, height)
        require(width <= 256 && height <= 256)
        require(data.short.toInt() == 1)
        val bits = data.short.toInt() and 65535
        require(bits in setOf(1, 4, 8, 24, 32))
        require(data.int == 0) { "Compressed ICO frame is unsupported." }
        data.position(32)
        val colorsUsed = data.int.toLong() and 0xffffffffL
        val paletteSize = if (bits <= 8) {
            if (colorsUsed == 0L) 1 shl bits else colorsUsed.also { require(it <= (1L shl bits)) }.toInt()
        } else 0
        val pixelsOffset = headerSize + paletteSize * 4
        val stride = ((width * bits + 31) / 32) * 4
        val pixelEnd = pixelsOffset.toLong() + stride.toLong() * height
        require(pixelEnd <= bytes.size)
        val maskStride = ((width + 31) / 32) * 4
        val hasMask = pixelEnd + maskStride.toLong() * height <= bytes.size
        val palette = IntArray(paletteSize) { index ->
            val start = headerSize + index * 4
            0xff000000.toInt() or ((bytes[start + 2].toInt() and 255) shl 16) or
                ((bytes[start + 1].toInt() and 255) shl 8) or (bytes[start].toInt() and 255)
        }
        val pixels = IntArray(width * height)
        var hasAlpha = false
        for (y in 0 until height) for (x in 0 until width) {
            val row = pixelsOffset + (height - y - 1) * stride
            val color = when (bits) {
                1, 4, 8 -> {
                    val bitOffset = x * bits
                    val index = (bytes[row + bitOffset / 8].toInt() and 255) ushr (8 - bits - bitOffset % 8) and ((1 shl bits) - 1)
                    require(index < palette.size)
                    palette[index]
                }
                else -> {
                    val start = row + x * (bits / 8)
                    val alpha = if (bits == 32) bytes[start + 3].toInt() and 255 else 255
                    if (bits == 32 && alpha != 0) hasAlpha = true
                    (alpha shl 24) or ((bytes[start + 2].toInt() and 255) shl 16) or
                        ((bytes[start + 1].toInt() and 255) shl 8) or (bytes[start].toInt() and 255)
                }
            }
            pixels[y * width + x] = color
        }
        for (y in 0 until height) for (x in 0 until width) {
            val index = y * width + x
            if (bits == 32 && !hasAlpha) pixels[index] = pixels[index] or 0xff000000.toInt()
            if (hasMask) {
                val maskByte = bytes[pixelEnd.toInt() + (height - 1 - y) * maskStride + x / 8].toInt() and 255
                if (maskByte and (0x80 ushr (x % 8)) != 0) pixels[index] = pixels[index] and 0x00ffffff
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
