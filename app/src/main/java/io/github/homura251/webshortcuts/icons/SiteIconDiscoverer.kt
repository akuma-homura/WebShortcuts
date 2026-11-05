package io.github.homura251.webshortcuts.icons

import android.graphics.Bitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

/** A result always contains a normalized bitmap ready for private storage and launcher masks. */
data class IconFetchResult(val bitmap: Bitmap, val sourceUrl: String, val description: String)

/**
 * Never receives browser cookies or credentials. Reads only the page head and bounded resources.
 * All calls can be cancelled while queued, connecting, or reading a body; a fetch has a total deadline.
 */
class SiteIconDiscoverer(
    private val processor: IconProcessor,
    client: OkHttpClient = OkHttpClient(),
) {
    private val http = client.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .cookieJar(okhttp3.CookieJar.NO_COOKIES)
        .build()

    suspend fun fetch(url: String): IconFetchResult? = withContext(Dispatchers.IO) {
        val parsedUrl = url.toHttpUrlOrNull() ?: return@withContext null
        if (parsedUrl.username.isNotEmpty() || parsedUrl.password.isNotEmpty()) return@withContext null
        withTimeoutOrNull(25_000) {
            val candidates = mutableListOf<IconCandidate>()
            var pageUrl = parsedUrl.toString()
            val page = request(pageUrl, ResourceKind.HTML)
            if (page != null) {
                pageUrl = page.finalUrl
                val head = runCatching { IconCandidates.parseHead(page.text(), pageUrl) }.getOrNull()
                if (head != null) {
                    candidates.addAll(head.candidates)
                    for (manifestUrl in head.manifests) {
                        coroutineContext.ensureActive()
                        val manifest = request(manifestUrl, ResourceKind.MANIFEST) ?: continue
                        candidates.addAll(runCatching { IconCandidates.parseManifest(manifest.text(), manifest.finalUrl) }.getOrDefault(emptyList()))
                    }
                }
            }
            IconCandidates.rootFavicon(pageUrl)?.let { candidates.add(IconCandidate(it, CandidateKind.FAVICON)) }
            for (candidate in IconCandidates.sorted(candidates)) {
                coroutineContext.ensureActive()
                val resource = request(candidate.url, ResourceKind.IMAGE) ?: continue
                val fetchContext = coroutineContext
                val bitmap = try {
                    processor.decode(resource.bytes, resource.mime, checkpoint = { fetchContext.ensureActive() })
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    continue
                }
                try { coroutineContext.ensureActive() } catch (cancelled: CancellationException) {
                    bitmap.recycle()
                    throw cancelled
                }
                val resolution = if (candidate.size > 0) " · ${candidate.size}×${candidate.size}" else ""
                return@withTimeoutOrNull IconFetchResult(bitmap, resource.finalUrl, candidate.kind.label + resolution)
            }
            null
        }
    }

    private enum class ResourceKind(val maxBytes: Int) {
        HTML(IconPolicy.MAX_TEXT_BYTES), MANIFEST(IconPolicy.MAX_TEXT_BYTES), IMAGE(IconPolicy.MAX_IMAGE_BYTES),
    }

    private data class Resource(
        val finalUrl: String,
        val mime: String?,
        val charset: Charset,
        val bytes: ByteArray,
        val redirect: String? = null,
    ) {
        fun text(): String = bytes.toString(charset)
    }

    private suspend fun request(startUrl: String, kind: ResourceKind): Resource? {
        var url = startUrl
        repeat(4) { attempt ->
            coroutineContext.ensureActive()
            val resource = requestOnce(url, kind) ?: return null
            val location = resource.redirect ?: return resource
            if (attempt == 3) return null
            url = IconCandidates.resolve(url, location) ?: return null
        }
        return null
    }

    private suspend fun requestOnce(url: String, kind: ResourceKind): Resource? = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(Request.Builder().url(url)
            .header("User-Agent", "WebShortcuts/1.1 (Android; icon discovery)")
            .header("Accept", when (kind) {
                ResourceKind.HTML -> "text/html,application/xhtml+xml;q=0.9"
                ResourceKind.MANIFEST -> "application/manifest+json,application/json;q=0.9"
                ResourceKind.IMAGE -> "image/png,image/webp,image/jpeg,image/x-icon,image/vnd.microsoft.icon,image/svg+xml;q=0.9"
            }).get().build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val resource = try {
                    response.use { reply ->
                        if (reply.code in setOf(301, 302, 303, 307, 308)) {
                            reply.header("Location")?.let {
                                Resource(url, null, Charsets.UTF_8, byteArrayOf(), redirect = it)
                            }
                        } else if (!reply.isSuccessful || reply.body == null) {
                            null
                        } else {
                            val body = reply.body!!
                            val mediaType = body.contentType()
                            val mime = mediaType?.let { "${it.type}/${it.subtype}" }?.lowercase(Locale.ROOT)
                            val acceptedMime = when (kind) {
                                ResourceKind.HTML -> mime == "text/html" || mime == "application/xhtml+xml"
                                ResourceKind.MANIFEST -> mime in setOf("application/manifest+json", "application/json", "text/json", "text/plain")
                                ResourceKind.IMAGE -> mime in IconPolicy.supportedMimeTypes
                            }
                            // HTML can have a large body while its head remains small, so do not reject its Content-Length.
                            val validLength = kind == ResourceKind.HTML || body.contentLength() <= kind.maxBytes
                            if (!acceptedMime || !validLength) null
                            else {
                                val bytes = body.byteStream().use { stream ->
                                    readResponse(stream, kind.maxBytes, kind == ResourceKind.HTML) { continuation.isActive }
                                }
                                Resource(reply.request.url.toString(), mime, mediaType?.charset(Charsets.UTF_8) ?: Charsets.UTF_8, bytes)
                            }
                        }
                    }
                } catch (_: Exception) {
                    null
                }
                if (continuation.isActive) continuation.resume(resource)
            }
        })
    }

    companion object {
        /** Stops at the first closing head or body token, including tokens split across reads. */
        internal fun readResponse(input: InputStream, limit: Int, headOnly: Boolean, active: () -> Boolean = { true }): ByteArray {
            val output = ByteArrayOutputStream(minOf(limit, 16 * 1024))
            val buffer = ByteArray(8192)
            var previousTail = ""
            val headEnd = Regex("</head\\s*>|<body(?:\\s|>)", RegexOption.IGNORE_CASE)
            while (true) {
                if (!active()) throw IOException("Request cancelled.")
                val count = input.read(buffer, 0, minOf(buffer.size, limit - output.size() + 1))
                if (count < 0) return output.toByteArray()
                if (count == 0) continue
                if (headOnly) {
                    val chunk = previousTail + String(buffer, 0, count, Charsets.ISO_8859_1)
                    val match = headEnd.find(chunk)
                    if (match != null) {
                        val newBytes = (match.range.last + 1 - previousTail.length).coerceIn(0, count)
                        if (output.size() + newBytes > limit) throw IOException("Resource exceeds the size limit.")
                        output.write(buffer, 0, newBytes)
                        return output.toByteArray()
                    }
                    previousTail = chunk.takeLast(32)
                }
                if (output.size() + count > limit) throw IOException("Resource exceeds the size limit.")
                output.write(buffer, 0, count)
            }
        }
    }
}
