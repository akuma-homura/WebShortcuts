package io.github.homura251.webshortcuts.local

import io.github.homura251.webshortcuts.data.BookmarkSourceType
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.util.Locale

/** Bounded format checks, independent of a document provider or WebView implementation. */
object LocalPagePolicy {
    private const val HEADER_BYTES = 64 * 1024
    private val htmlTag = Regex("<(?:!doctype\\s+html|html\\b|head\\b|body\\b|meta\\b|title\\b|div\\b|p\\b|h[1-6]\\b|main\\b|article\\b|section\\b|table\\b|a\\b|span\\b|style\\b|script\\b)", RegexOption.IGNORE_CASE)
    private val htmlPart = Regex("(?:^|\\r?\\n)Content-Type:\\s*(?:text/html|application/xhtml\\+xml)\\b", RegexOption.IGNORE_CASE)
    private val boundaryParameter = Regex("(?:^|;)\\s*boundary\\s*=\\s*(?:\"([^\"\\r\\n]+)\"|([^;\\s]+))", RegexOption.IGNORE_CASE)
    private val charsetParameter = Regex("charset\\s*=\\s*[\"']?\\s*([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE)

    fun detectType(displayName: String, mimeType: String?): BookmarkSourceType {
        return when (displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "html", "htm" -> BookmarkSourceType.HTML
            "mhtml", "mht" -> BookmarkSourceType.MHTML
            else -> when (mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)) {
                "text/html", "application/xhtml+xml" -> BookmarkSourceType.HTML
                "multipart/related", "message/rfc822", "application/x-mimearchive", "application/x-mhtml", "application/mhtml" -> BookmarkSourceType.MHTML
                else -> throw IllegalArgumentException("请选择 HTML、HTM、MHTML 或 MHT 文件")
            }
        }
    }

    fun validateFile(type: BookmarkSourceType, file: File) {
        validate(type, file.length()) { file.inputStream() }
    }

    fun validateBytes(type: BookmarkSourceType, bytes: ByteArray) {
        validate(type, bytes.size.toLong()) { ByteArrayInputStream(bytes) }
    }

    /** Null lets WebView honor an HTML meta charset; a BOM or declared charset takes precedence. */
    fun htmlEncoding(file: File): String? = file.inputStream().use { encoding(readPrefix(it)) }

    private fun validate(type: BookmarkSourceType, size: Long, stream: () -> InputStream) {
        require(type != BookmarkSourceType.WEB) { "在线收藏不是本地网页" }
        require(size in 1..LocalPageStore.MAX_FILE_BYTES) { if (size == 0L) "所选文件为空" else "本地网页不能超过 32 MB" }
        val prefix = stream().use { readPrefix(it) }
        when (type) {
            BookmarkSourceType.HTML -> {
                val charset = encoding(prefix)?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
                val text = prefix.toString(charset).removePrefix("\uFEFF")
                require('\u0000' !in text && htmlTag.containsMatchIn(text)) { "未识别为 HTML 页面，请检查文件格式" }
            }
            BookmarkSourceType.MHTML -> validateArchive(prefix, stream)
            BookmarkSourceType.WEB -> error("unreachable")
        }
    }

    private fun validateArchive(prefix: ByteArray, stream: () -> InputStream) {
        val text = prefix.toString(Charsets.ISO_8859_1)
        val separator = Regex("\\r?\\n\\r?\\n").find(text)
            ?: throw IllegalArgumentException("MHTML 文件缺少有效的 MIME 头")
        val headers = text.substring(0, separator.range.first).replace(Regex("\\r?\\n[ \\t]+"), " ")
        val contentType = headers.lineSequence().map { it.trimEnd('\r') }
            .firstOrNull { it.startsWith("Content-Type:", ignoreCase = true) }?.substringAfter(':')?.trim().orEmpty()
        require(contentType.substringBefore(';').trim().equals("multipart/related", ignoreCase = true)) { "此文件不是受支持的 MHTML 网页归档" }
        val match = boundaryParameter.find(contentType)
        val boundary = match?.let { it.groupValues[1].ifBlank { it.groupValues[2] } }.orEmpty()
        require(boundary.isNotEmpty() && boundary.length <= 200 && boundary.none { it.isISOControl() }) { "MHTML 归档边界无效" }
        val closing = "--$boundary--"
        var hasHtml = false
        var hasClosing = false
        stream().use { input ->
            val buffer = ByteArray(16 * 1024)
            var carry = ""
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                val chunk = carry + String(buffer, 0, count, Charsets.ISO_8859_1)
                hasHtml = hasHtml || htmlPart.containsMatchIn(chunk)
                hasClosing = hasClosing || chunk.contains(closing)
                carry = chunk.takeLast(512)
            }
        }
        require(hasHtml && hasClosing) { "MHTML 归档不完整或不包含 HTML 页面" }
    }

    private fun encoding(bytes: ByteArray): String? {
        fun at(index: Int) = bytes.getOrNull(index)?.toInt()?.and(0xff)
        return when {
            at(0) == 0xef && at(1) == 0xbb && at(2) == 0xbf -> "UTF-8"
            at(0) == 0xff && at(1) == 0xfe -> "UTF-16LE"
            at(0) == 0xfe && at(1) == 0xff -> "UTF-16BE"
            else -> charsetParameter.find(bytes.toString(Charsets.ISO_8859_1))?.groupValues?.get(1)
                ?.takeIf { runCatching { Charset.isSupported(it) }.getOrDefault(false) }
        }
    }

    private fun readPrefix(input: InputStream): ByteArray {
        val buffer = ByteArray(HEADER_BYTES)
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) break
            if (count > 0) offset += count
        }
        return if (offset == buffer.size) buffer else buffer.copyOf(offset)
    }
}
