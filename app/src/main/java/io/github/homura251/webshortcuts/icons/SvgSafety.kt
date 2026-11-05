package io.github.homura251.webshortcuts.icons

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.util.Locale

/** AndroidSVG never receives network-bearing references or XML entities. */
internal object SvgSafety {
    fun check(source: String) {
        require(source.toByteArray(Charsets.UTF_8).size <= IconPolicy.MAX_TEXT_BYTES) { "SVG is too large." }
        require(!Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(source)) {
            "SVG document types and entities are not supported."
        }
        val document = Jsoup.parse(source, "", Parser.xmlParser())
        val elements = document.getAllElements()
        require(elements.size <= 2048) { "SVG is too complex." }
        require(elements.any { it.normalName() == "svg" }) { "Not an SVG document." }
        val ids = elements.filter { it.hasAttr("id") }.associateBy { it.id() }
        val references = mutableMapOf<String, MutableSet<String>>()
        elements.forEach { element ->
            require(element.normalName().substringAfterLast(':') !in setOf("script", "foreignobject", "image", "feimage", "iframe", "audio", "video")) {
                "External or interactive SVG content is not supported."
            }
            require(element.parents().size <= 32) { "SVG nesting is too deep." }
            element.attributes().forEach { attribute ->
                val key = attribute.key.lowercase(Locale.ROOT)
                val value = attribute.value.trim()
                require(!key.startsWith("on")) { "SVG event handlers are not supported." }
                if (key == "href" || key.endsWith(":href")) {
                    require(value.startsWith('#') && value.length > 1) { "External SVG references are not supported." }
                    (listOf(element) + element.parents()).filter { it.id().isNotEmpty() }.forEach { owner ->
                        references.getOrPut(owner.id()) { mutableSetOf() }.add(value.drop(1))
                    }
                    // A <use> without its own id may still reference its ancestor and form a cycle.
                    if (element.normalName().substringAfterLast(':') == "use") {
                        val target = ids[value.drop(1)]
                        require(target == null || target !in element.parents() && target != element) { "Recursive SVG reference." }
                    }
                }
                if (key == "style" || value.contains("url", ignoreCase = true)) checkCss(value)
            }
            if (element.normalName().substringAfterLast(':') == "style") checkCss(element.data().ifEmpty { element.text() })
        }
        val visited = mutableSetOf<String>()
        fun visit(id: String, path: Set<String>) {
            require(path.size <= 16 && id !in path) { "Recursive SVG reference." }
            if (id in visited) return
            references[id]?.forEach { visit(it, path + id) }
            visited.add(id)
        }
        references.keys.forEach { visit(it, emptySet()) }
    }

    private fun checkCss(value: String) {
        require(!value.contains('\\') && !value.contains('@')) { "External SVG CSS is not supported." }
        val references = Regex("url\\s*\\((.*?)\\)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        references.findAll(value.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")).forEach { match ->
            val url = match.groupValues[1].trim().trim('"', '\'').trim()
            require(url.startsWith('#') && url.length > 1) { "External SVG URLs are not supported." }
        }
    }
}
