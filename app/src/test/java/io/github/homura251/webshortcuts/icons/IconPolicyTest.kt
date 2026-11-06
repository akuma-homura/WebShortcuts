package io.github.homura251.webshortcuts.icons

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class IconPolicyTest {
    @Test fun initialsPreserveUnicodeAndIgnoreDecoration() {
        assertEquals("中", IconPolicy.initial("  ⭐ 中文站点"))
        assertEquals("A", IconPolicy.initial("apple"))
        assertEquals("2", IconPolicy.initial("#2026"))
        assertEquals("W", IconPolicy.initial("🙂 !"))
        assertEquals("𠮷", IconPolicy.initial("𠮷野家"))
    }

    @Test fun everyTonalPairHasReadableContrast() {
        IconPolicy.tonalPairs.forEach { assertTrue(IconPolicy.contrast(it) >= 4.5) }
        assertEquals(IconPolicy.tonalPair("https://example.com/a"), IconPolicy.tonalPair("https://example.com/b"))
    }

    @Test fun readsExactlyTheLimit() {
        assertEquals(20, IconPolicy.readBounded(ByteArrayInputStream(ByteArray(20)), 20).size)
    }

    @Test(expected = IOException::class) fun rejectsOneByteAboveLimit() {
        IconPolicy.readBounded(ByteArrayInputStream(ByteArray(21)), 20)
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsDimensionBombs() {
        IconPolicy.validateDimensions(4097, 1)
    }

    @Test fun headReaderStopsBeforeTheBodyAndHandlesSplitTokens() {
        val head = "<html><head><title>hello</title></head>"
        val input = object : ByteArrayInputStream((head + "<body>" + "x".repeat(5000)).toByteArray()) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = super.read(buffer, offset, minOf(length, 3))
        }
        val result = SiteIconDiscoverer.readResponse(input, 1000, true).toString(Charsets.UTF_8)
        assertEquals(head, result)
        assertTrue(input.available() > 4000)
    }

    @Test fun largeBodyDoesNotMakeASmallHeadExceedTheCap() {
        val source = "<head></head><body>" + "x".repeat(1000)
        assertEquals("<head></head>", SiteIconDiscoverer.readResponse(ByteArrayInputStream(source.toByteArray()), 32, true).toString(Charsets.UTF_8))
    }

    @Test(expected = IOException::class) fun headReaderRejectsOversizedHead() {
        SiteIconDiscoverer.readResponse(ByteArrayInputStream("<head>${"x".repeat(100)}</head>".toByteArray()), 20, true)
    }
}
