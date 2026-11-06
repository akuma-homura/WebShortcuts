package io.github.homura251.webshortcuts.icons

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.util.IdentityHashMap
import java.util.Locale

/** AndroidSVG never receives network-bearing references, XML entities, or an unbounded render tree. */
internal object SvgSafety {
    private const val MAX_EXPANDED_ELEMENTS = 8192
    private const val MAX_EXPANDED_DEPTH = 64
    private const val MAX_EXPANDED_DRAWING_WORK = 1024 * 1024
    private val cssUrls = Regex("url\\s*\\((.*?)\\)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    fun check(source: String, checkpoint: () -> Unit = {}) {
        checkpoint()
        require(source.toByteArray(Charsets.UTF_8).size <= IconPolicy.MAX_TEXT_BYTES) { "SVG is too large." }
        require(!Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(source)) {
            "SVG document types and entities are not supported."
        }
        val document = Jsoup.parse(source, "", Parser.xmlParser())
        val elements = document.getAllElements()
        require(elements.size <= 2048) { "SVG is too complex." }
        require(elements.any { it.normalName() == "svg" }) { "Not an SVG document." }
        val identified = elements.filter { it.id().isNotEmpty() }
        val ids = identified.associateBy { it.id() }
        require(ids.size == identified.size) { "Duplicate SVG ids are not supported." }
        // A list preserves repeated <use> edges. A set alone misses exponential acyclic expansion.
        val references = IdentityHashMap<Element, MutableList<Element>>()
        elements.forEach { element ->
            checkpoint()
            require(element.normalName().substringAfterLast(':') !in setOf("script", "foreignobject", "image", "feimage", "iframe", "audio", "video")) {
                "External or interactive SVG content is not supported."
            }
            require(element.parents().size <= 32) { "SVG nesting is too deep." }
            fun reference(id: String) {
                ids[id]?.let { references.getOrPut(element) { mutableListOf() }.add(it) }
            }
            element.attributes().forEach { attribute ->
                val key = attribute.key.lowercase(Locale.ROOT)
                val value = attribute.value.trim()
                require(!key.startsWith("on")) { "SVG event handlers are not supported." }
                if (key == "href" || key.endsWith(":href")) {
                    require(value.startsWith('#') && value.length > 1) { "External SVG references are not supported." }
                    reference(value.drop(1))
                }
                if (key == "style" || value.contains("url", ignoreCase = true)) checkCss(value).forEach(::reference)
            }
            if (element.normalName().substringAfterLast(':') == "style") {
                val cssReferences = checkCss(element.data().ifEmpty { element.text() })
                // Stylesheet selectors can apply resource effects to arbitrary nodes. Inline resource
                // references are supported; stylesheet resource references cannot be bounded safely.
                require(cssReferences.isEmpty()) { "SVG resource references must be inline." }
            }
        }

        data class Cost(val elements: Int, val drawingWork: Int, val depth: Int)
        val costs = IdentityHashMap<Element, Cost>()
        val visiting = java.util.Collections.newSetFromMap(IdentityHashMap<Element, Boolean>())
        fun cost(element: Element, depth: Int): Cost {
            checkpoint()
            require(depth <= MAX_EXPANDED_DEPTH) { "SVG reference nesting is too deep." }
            require(element !in visiting) { "Recursive SVG reference." }
            costs[element]?.let { return it }
            visiting.add(element)
            var expanded = 1
            var drawingWork = 1 + element.attributes().sumOf { it.value.length } + element.ownText().length
            require(drawingWork <= MAX_EXPANDED_DRAWING_WORK) { "SVG drawing data is too complex." }
            var longestChild = 0
            fun include(child: Element) {
                val childCost = cost(child, depth + 1)
                // Saturating at a small limit avoids overflow and never walks the expanded tree.
                require(childCost.elements <= MAX_EXPANDED_ELEMENTS - expanded) { "SVG reference expansion is too complex." }
                expanded += childCost.elements
                require(childCost.drawingWork <= MAX_EXPANDED_DRAWING_WORK - drawingWork) { "SVG drawing expansion is too complex." }
                drawingWork += childCost.drawingWork
                longestChild = maxOf(longestChild, childCost.depth)
            }
            element.children().forEach(::include)
            references[element]?.forEach(::include)
            require(longestChild < MAX_EXPANDED_DEPTH) { "SVG reference nesting is too deep." }
            visiting.remove(element)
            return Cost(expanded, drawingWork, longestChild + 1).also { costs[element] = it }
        }
        cost(document, 0)
        checkpoint()
    }

    private fun checkCss(value: String): List<String> {
        require(!value.contains('\\') && !value.contains('@')) { "External SVG CSS is not supported." }
        return cssUrls.findAll(value.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")).map { match ->
            val url = match.groupValues[1].trim().trim('"', '\'').trim()
            require(url.startsWith('#') && url.length > 1) { "External SVG URLs are not supported." }
            url.drop(1)
        }.toList()
    }
}
