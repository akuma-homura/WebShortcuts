package io.github.homura251.webshortcuts.icons

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.Locale
import kotlin.math.pow

/** Shared limits apply before allocating or decoding untrusted image data. */
object IconPolicy {
    const val OUTPUT_SIZE = 512
    const val MAX_DIMENSION = 4096
    const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
    const val MAX_TEXT_BYTES = 512 * 1024
    const val SAFE_ZONE_RATIO = 66f / 108f
    const val WEB_MASKABLE_SAFE_RATIO = 0.8f
    const val ADAPTIVE_VIEWPORT_RATIO = 72f / 108f

    val supportedMimeTypes = setOf(
        "image/png", "image/jpeg", "image/webp", "image/x-icon",
        "image/vnd.microsoft.icon", "image/svg+xml",
    )

    fun validateDimensions(width: Int, height: Int) {
        require(width in 1..MAX_DIMENSION && height in 1..MAX_DIMENSION) {
            "Image dimensions must be between 1 and $MAX_DIMENSION pixels."
        }
    }

    /** Reads at most limit+1 bytes, without trusting Content-Length or available(). */
    fun readBounded(input: InputStream, limit: Int): ByteArray {
        require(limit > 0)
        val output = ByteArrayOutputStream(minOf(limit, 16 * 1024))
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, limit - output.size() + 1))
            if (count < 0) return output.toByteArray()
            if (count == 0) continue
            if (output.size() + count > limit) throw IOException("Resource exceeds the size limit.")
            output.write(buffer, 0, count)
        }
    }

    /** First letter/digit, including supplementary Unicode letters, never half a surrogate. */
    fun initial(title: String): String {
        val point = title.codePoints().filter(Character::isLetterOrDigit).findFirst()
        if (!point.isPresent) return "W"
        return String(Character.toChars(point.asInt)).uppercase(Locale.ROOT)
            .codePoints().limit(1).toArray().let { String(it, 0, it.size) }
    }

    data class TonalPair(val background: Int, val foreground: Int)

    // Material 3 light-container / on-container roles; deliberately stable across wallpaper changes.
    val tonalPairs = listOf(
        TonalPair(0xffeaddff.toInt(), 0xff21005d.toInt()),
        TonalPair(0xffe8def8.toInt(), 0xff1d192b.toInt()),
        TonalPair(0xffffd8e4.toInt(), 0xff31111d.toInt()),
        TonalPair(0xffd3e4ff.toInt(), 0xff001c3b.toInt()),
        TonalPair(0xffc6efdc.toInt(), 0xff002116.toInt()),
        TonalPair(0xffffddb4.toInt(), 0xff2a1800.toInt()),
    )

    fun tonalPair(url: String): TonalPair {
        val host = runCatching { URI(url).host?.lowercase(Locale.ROOT) }.getOrNull() ?: url
        return tonalPairs[Math.floorMod(host.hashCode(), tonalPairs.size)]
    }

    internal fun contrast(pair: TonalPair): Double {
        fun luminance(color: Int): Double {
            fun channel(shift: Int): Double {
                val value = (color ushr shift and 255) / 255.0
                return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
            }
            return channel(16) * 0.2126 + channel(8) * 0.7152 + channel(0) * 0.0722
        }
        val first = luminance(pair.background)
        val second = luminance(pair.foreground)
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }
}
