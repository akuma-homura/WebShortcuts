package io.github.homura251.webshortcuts.icons

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.Locale

internal enum class CandidateKind(val label: String) {
    MANIFEST("Web App Manifest"), APPLE("Apple Touch Icon"), LINK("Website icon"), FAVICON("Favicon"),
}

internal data class IconCandidate(
    val url: String,
    val kind: CandidateKind,
    val size: Int = 0,
    val mimeType: String? = null,
) {
    val rank: Int
        get() = when {
            kind == CandidateKind.MANIFEST && size >= 192 -> 0
            kind == CandidateKind.APPLE -> 1
            kind == CandidateKind.LINK && size >= 96 -> 2
            kind == CandidateKind.MANIFEST -> 3
            kind == CandidateKind.LINK -> 4
            else -> 5
        }
}

internal data class HeadDiscovery(val candidates: List<IconCandidate>, val manifests: List<String>)

internal object IconCandidates {
    fun parseHead(html: String, pageUrl: String): HeadDiscovery {
        val document = Jsoup.parse(html, pageUrl)
        val declaredBase = document.head().selectFirst("base[href]")?.attr("href")
        val base = declaredBase?.let { resolve(pageUrl, it) } ?: pageUrl
        val candidates = mutableListOf<IconCandidate>()
        val manifests = mutableListOf<String>()
        document.head().select("link[href]").take(128).forEach { link ->
            val relation = link.attr("rel").lowercase(Locale.ROOT).split(Regex("\\s+")).toSet()
            val url = resolve(base, link.attr("href")) ?: return@forEach
            val mime = link.attr("type").substringBefore(';').trim().lowercase(Locale.ROOT).ifEmpty { null }
            when {
                "manifest" in relation -> manifests.add(url)
                "apple-touch-icon" in relation || "apple-touch-icon-precomposed" in relation ->
                    candidates.add(IconCandidate(url, CandidateKind.APPLE, size(link.attr("sizes")), mime))
                "icon" in relation && (mime == null || mime in IconPolicy.supportedMimeTypes) ->
                    candidates.add(IconCandidate(url, CandidateKind.LINK, size(link.attr("sizes")), mime))
            }
        }
        return HeadDiscovery(candidates, manifests.distinct().take(2))
    }

    fun parseManifest(text: String, manifestUrl: String): List<IconCandidate> {
        val icons = JSONObject(text).optJSONArray("icons") ?: return emptyList()
        return (0 until minOf(icons.length(), 128)).mapNotNull { index ->
            val item = icons.optJSONObject(index) ?: return@mapNotNull null
            val purpose = item.optString("purpose", "any").lowercase(Locale.ROOT).split(Regex("\\s+"))
            if ("any" !in purpose && "maskable" !in purpose) return@mapNotNull null
            val mime = item.optString("type").substringBefore(';').trim().lowercase(Locale.ROOT).ifEmpty { null }
            if (mime != null && mime !in IconPolicy.supportedMimeTypes) return@mapNotNull null
            val src = resolve(manifestUrl, item.optString("src")) ?: return@mapNotNull null
            IconCandidate(src, CandidateKind.MANIFEST, size(item.optString("sizes")), mime)
        }
    }

    fun sorted(candidates: List<IconCandidate>): List<IconCandidate> {
        val ranked = candidates.sortedWith(compareBy<IconCandidate> { it.rank }
            .thenByDescending { it.size.coerceAtMost(IconPolicy.MAX_DIMENSION) }
            .thenBy { if (it.mimeType in setOf("image/png", "image/webp")) 0 else 1 })
            .distinctBy { it.url }
        val fallback = ranked.firstOrNull { it.kind == CandidateKind.FAVICON }
        // Keep the request budget bounded without allowing many broken declarations to evict /favicon.ico.
        return if (fallback == null) ranked.take(12)
        else ranked.filter { it.kind != CandidateKind.FAVICON }.take(11) + fallback
    }

    fun resolve(base: String, value: String): String? {
        if (value.isBlank()) return null
        val baseUrl = base.toHttpUrlOrNull() ?: return null
        val resolved = baseUrl.resolve(value.trim()) ?: return null
        if (resolved.username.isNotEmpty() || resolved.password.isNotEmpty()) return null
        if (baseUrl.isHttps && !resolved.isHttps) return null
        return resolved.newBuilder().fragment(null).build().toString()
    }

    fun rootFavicon(pageUrl: String): String? = pageUrl.toHttpUrlOrNull()?.newBuilder()
        ?.encodedPath("/favicon.ico")?.query(null)?.fragment(null)?.build()?.toString()

    internal fun size(sizes: String): Int = sizes.lowercase(Locale.ROOT).split(Regex("\\s+"))
        .mapNotNull { value ->
            if (value == "any") return@mapNotNull IconPolicy.OUTPUT_SIZE
            val parts = value.split('x')
            if (parts.size != 2) return@mapNotNull null
            val width = parts[0].toIntOrNull() ?: return@mapNotNull null
            val height = parts[1].toIntOrNull() ?: return@mapNotNull null
            if (width <= 0 || height <= 0 || width > IconPolicy.MAX_DIMENSION || height > IconPolicy.MAX_DIMENSION) null
            else minOf(width, height)
        }.maxOrNull() ?: 0
}
