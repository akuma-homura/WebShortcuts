package io.github.homura251.webshortcuts.icons

import org.junit.Assert.*
import org.junit.Test

class IconCandidatesTest {
    @Test fun resolvesHtmlBaseAndRelTokensWithoutDependingOnAttributeOrder() {
        val head = IconCandidates.parseHead("""
            <html><head><base href="/assets/">
            <link sizes="180x180" href="apple.png" rel="apple-touch-icon-precomposed">
            <link href="../favicon.png" rel="shortcut ICON" sizes="32x32 128x128">
            <link href="app.webmanifest" rel="manifest">
            <link href="javascript:alert(1)" rel="icon">
            </head><body><link rel="icon" href="body.png"></body></html>
        """.trimIndent(), "https://example.com/path/index.html")
        assertEquals(listOf("https://example.com/assets/apple.png", "https://example.com/favicon.png"), head.candidates.map { it.url })
        assertEquals(listOf("https://example.com/assets/app.webmanifest"), head.manifests)
        assertEquals(128, head.candidates.last().size)
    }

    @Test fun manifestUsesItsOwnFinalUrlAndSkipsMonochromeOrUnsupportedIcons() {
        val icons = IconCandidates.parseManifest("""{"icons":[
          {"src":"icons/icon.png","sizes":"192x192","type":"image/png"},
          {"src":"/large.webp","sizes":"512x512","type":"image/webp","purpose":"maskable any"},
          {"src":"/mono.png","sizes":"512x512","purpose":"monochrome"},
          {"src":"/bad.gif","sizes":"512x512","type":"image/gif"},
          {"src":"data:image/png;base64,xxxx","sizes":"512x512"}
        ]}""", "https://example.com/pwa/app.webmanifest")
        assertEquals(2, icons.size)
        assertFalse(icons.first().maskable)
        assertTrue(icons.last().maskable)
        assertEquals("https://example.com/pwa/icons/icon.png", icons.first().url)
        assertEquals("https://example.com/large.webp", IconCandidates.sorted(icons).first().url)
    }

    @Test fun rankingPrioritizesResolutionBeforeSourceKindAndDeduplicates() {
        val icons = listOf(
            IconCandidate("https://site/f.ico", CandidateKind.FAVICON),
            IconCandidate("https://site/small.png", CandidateKind.MANIFEST, 48),
            IconCandidate("https://site/link.png", CandidateKind.LINK, 256),
            IconCandidate("https://site/apple.png", CandidateKind.APPLE, 180),
            IconCandidate("https://site/pwa.png", CandidateKind.MANIFEST, 512, "image/png"),
            IconCandidate("https://site/pwa.png", CandidateKind.LINK, 512),
        )
        assertEquals(listOf("pwa.png", "link.png", "apple.png", "small.png", "f.ico"),
            IconCandidates.sorted(icons).map { it.url.substringAfterLast('/') })
    }

    @Test fun scalableAnd512PixelIconsFitTheBudgetBeforeMany192PixelDeclarations() {
        val medium = (1..20).map { IconCandidate("https://site/medium$it.png", CandidateKind.MANIFEST, 192) }
        val vector = IconCandidate("https://site/vector.svg", CandidateKind.LINK, 0, "image/svg+xml")
        val large = IconCandidate("https://site/large.png", CandidateKind.LINK, 512, "image/png")
        val result = IconCandidates.sorted(medium + large + vector)
        assertEquals(vector, result[0])
        assertEquals(large, result[1])
        assertEquals(12, result.size)
    }

    @Test fun rejectsCredentialsAndHttpsDowngrade() {
        assertNull(IconCandidates.resolve("https://example.com", "http://example.com/icon.png"))
        assertNull(IconCandidates.resolve("https://example.com", "https://name:password@site.com/icon.png"))
        assertNull(IconCandidates.resolve("https://example.com", "file:///etc/passwd"))
        assertEquals("http://192.168.1.1/favicon.ico", IconCandidates.rootFavicon("http://192.168.1.1/app?q=1#x"))
    }

    @Test fun reservesTheLastAttemptForRootFallbackEvenWithManyDeclaredIcons() {
        val declared = (1..30).map { IconCandidate("https://example.com/icon$it.png", CandidateKind.MANIFEST, 512) }
        val fallback = IconCandidate("https://example.com/favicon.ico", CandidateKind.FAVICON)
        val result = IconCandidates.sorted(declared + fallback)
        assertEquals(12, result.size)
        assertEquals(fallback, result.last())
        assertEquals(11, result.count { it.kind == CandidateKind.MANIFEST })
    }

    @Test fun declaredSizesAreBounded() {
        assertEquals(512, IconCandidates.size("any"))
        assertEquals(192, IconCandidates.size("garbage 999999x999999 192x512"))
        assertEquals(0, IconCandidates.size("-1x30 0x0"))
    }
}
